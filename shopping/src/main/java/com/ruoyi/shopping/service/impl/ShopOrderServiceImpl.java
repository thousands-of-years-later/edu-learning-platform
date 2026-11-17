package com.ruoyi.shopping.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.shopping.domain.ShopOrder;
import com.ruoyi.shopping.domain.ShopProduct;
import com.ruoyi.shopping.mapper.ShopOrderMapper;
import com.ruoyi.shopping.mapper.ShopProductMapper;
import com.ruoyi.shopping.service.IShopOrderService;
import com.ruoyi.shopping.utils.RedemptionCodeUtils;
import com.ruoyi.user.domain.PointsSource;
import com.ruoyi.user.service.IPointsService;

/**
 * 积分商城-兑换记录 服务层处理
 *
 * 兑换只有一条主线：扣库存 → 写订单（生成唯一码）→ 扣积分/写流水。
 * 三步在同一事务里，任何一步失败都整笔回滚，不会留下「扣了积分没订单」这种半截数据。
 *
 * @author edu
 */
@Service
public class ShopOrderServiceImpl implements IShopOrderService
{
    /** 兑换码冲突时的最大尝试次数（概率极低，正常第 1 次就成功） */
    private static final int CODE_MAX_ATTEMPTS = 3;

    @Autowired
    private ShopOrderMapper shopOrderMapper;

    @Autowired
    private ShopProductMapper shopProductMapper;

    @Autowired
    private IPointsService pointsService;

    @Override
    public List<ShopOrder> selectShopOrderList(ShopOrder order)
    {
        ShopOrder query = order == null ? new ShopOrder() : order;
        query.setUserName(StringUtils.trim(query.getUserName()));
        query.setProductName(StringUtils.trim(query.getProductName()));
        query.setRedemptionCode(StringUtils.trim(query.getRedemptionCode()));
        return shopOrderMapper.selectShopOrderList(query);
    }

    @Override
    public List<ShopOrder> selectByUser(Long userId)
    {
        if (userId == null)
        {
            return new ArrayList<>();
        }
        return shopOrderMapper.selectByUser(userId);
    }

    @Override
    @Transactional
    public Map<String, Object> exchange(Long userId, Long productId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
        if (productId == null)
        {
            throw new ServiceException("商品ID不能为空");
        }

        // 先读一次用于报错文案和下单快照；真正的防超卖/防改价在下一条条件 UPDATE 里
        ShopProduct product = shopProductMapper.selectVisibleProductById(productId);
        if (product == null)
        {
            throw new ServiceException("商品不存在或已下架");
        }
        if (product.getPointsPrice() == null || product.getPointsPrice() <= 0)
        {
            throw new ServiceException("商品信息异常，请稍后再试");
        }
        if (product.getStock() == null || product.getStock() < 1)
        {
            throw new ServiceException("商品库存不足");
        }

        int cost = product.getPointsPrice();
        if (shopProductMapper.decreaseStock(productId, cost) == 0)
        {
            throw new ServiceException("商品库存不足或信息已变更，请刷新后重试");
        }

        // 订单先落库：拿到 orderId 作为积分流水的 ref_id，也把商品信息固化成快照
        ShopOrder order = new ShopOrder();
        order.setUserId(userId);
        order.setProductId(productId);
        order.setProductName(product.getProductName());
        order.setCoverUrl(product.getCoverUrl());
        order.setPointsCost(cost);
        insertWithUniqueCode(order);

        // 积分不足会抛异常，事务回滚上面的扣库存和订单
        int balance = pointsService.deductPoints(userId, cost, PointsSource.SHOP_EXCHANGE, order.getOrderId(),
                "兑换商品：" + product.getProductName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", order.getOrderId());
        result.put("redemptionCode", order.getRedemptionCode());
        result.put("productId", productId);
        result.put("productName", product.getProductName());
        result.put("pointsCost", cost);
        result.put("balance", balance);
        return result;
    }

    /**
     * 插入订单并生成唯一兑换码。
     *
     * 唯一性由 uk_shop_order_code 兜底：撞码时换一个码重试，
     * 连续失败说明环境异常，抛错让整笔事务回滚，客户端可重试。
     */
    private void insertWithUniqueCode(ShopOrder order)
    {
        for (int attempt = 0; attempt < CODE_MAX_ATTEMPTS; attempt++)
        {
            order.setRedemptionCode(RedemptionCodeUtils.generate());
            try
            {
                shopOrderMapper.insertShopOrder(order);
                return;
            }
            catch (DuplicateKeyException e)
            {
                // 兑换码撞车：换一个码继续
            }
        }
        throw new ServiceException("兑换码生成失败，请重试");
    }
}
