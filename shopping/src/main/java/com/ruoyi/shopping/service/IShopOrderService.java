package com.ruoyi.shopping.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.shopping.domain.ShopOrder;

/**
 * 积分商城-兑换记录 服务层
 *
 * @author edu
 */
public interface IShopOrderService
{
    /**
     * 后台兑换记录列表（含用户昵称，分页由控制器 startPage() 驱动）
     */
    public List<ShopOrder> selectShopOrderList(ShopOrder order);

    /**
     * 某个用户的兑换记录（用户端「我的兑换」）
     */
    public List<ShopOrder> selectByUser(Long userId);

    /**
     * 积分兑换商品：同一事务内扣库存、写兑换记录（生成唯一兑换码）、扣积分并写流水。
     *
     * 任一步失败（库存不足、积分不足、兑换码生成失败）整笔回滚，不会出现「扣了积分没库存」之类的半截数据。
     *
     * @param userId    用户ID（由 ApiContext 传入，绝不接受前端传）
     * @param productId 商品ID
     * @return {orderId, redemptionCode, productId, productName, pointsCost, balance}
     */
    public Map<String, Object> exchange(Long userId, Long productId);
}
