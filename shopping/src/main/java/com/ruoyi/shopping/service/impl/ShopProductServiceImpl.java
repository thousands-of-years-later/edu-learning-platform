package com.ruoyi.shopping.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.shopping.domain.ShopCategory;
import com.ruoyi.shopping.domain.ShopProduct;
import com.ruoyi.shopping.mapper.ShopCategoryMapper;
import com.ruoyi.shopping.mapper.ShopProductMapper;
import com.ruoyi.shopping.service.IShopProductService;

/**
 * 积分商城-商品 服务层处理
 *
 * @author edu
 */
@Service
public class ShopProductServiceImpl implements IShopProductService
{
    @Autowired
    private ShopProductMapper shopProductMapper;

    @Autowired
    private ShopCategoryMapper shopCategoryMapper;

    @Override
    public List<ShopProduct> selectShopProductList(ShopProduct product)
    {
        ShopProduct query = product == null ? new ShopProduct() : product;
        query.setProductName(StringUtils.trim(query.getProductName()));
        return shopProductMapper.selectShopProductList(query);
    }

    @Override
    public ShopProduct selectShopProductById(Long productId)
    {
        return productId == null ? null : shopProductMapper.selectShopProductById(productId);
    }

    @Override
    public List<ShopProduct> selectVisibleProductList(ShopProduct product)
    {
        ShopProduct query = product == null ? new ShopProduct() : product;
        query.setKeyword(StringUtils.trim(query.getKeyword()));
        return shopProductMapper.selectVisibleProductList(query);
    }

    @Override
    public ShopProduct selectVisibleProductById(Long productId)
    {
        return productId == null ? null : shopProductMapper.selectVisibleProductById(productId);
    }

    @Override
    public int insertShopProduct(ShopProduct product)
    {
        validate(product, true);
        return shopProductMapper.insertShopProduct(product);
    }

    @Override
    public int updateShopProduct(ShopProduct product)
    {
        if (product.getProductId() == null)
        {
            throw new ServiceException("商品ID不能为空");
        }
        validate(product, false);
        return shopProductMapper.updateShopProduct(product);
    }

    @Override
    public int deleteShopProductByIds(String ids)
    {
        Long[] productIds = Convert.toLongArray(ids);
        if (productIds == null || productIds.length == 0)
        {
            throw new ServiceException("未选择要删除的商品");
        }
        return shopProductMapper.deleteShopProductByIds(productIds);
    }

    /**
     * 后台新增/修改校验。
     *
     * 分类只要求「存在且未删除」，停用分类下的商品仍可编辑（否则分类停用后连改价/下架都做不了），
     * 但用户端可见性会通过分类状态过滤掉。
     */
    private void validate(ShopProduct product, boolean insert)
    {
        product.setProductName(StringUtils.trim(product.getProductName()));
        if (StringUtils.isEmpty(product.getProductName()))
        {
            throw new ServiceException("商品名称不能为空");
        }
        if (product.getPointsPrice() == null || product.getPointsPrice() < 1)
        {
            throw new ServiceException("兑换积分必须大于 0");
        }
        if (product.getStock() == null || product.getStock() < 0)
        {
            throw new ServiceException("库存不能小于 0");
        }
        if (product.getCategoryId() == null)
        {
            throw new ServiceException("商品分类不能为空");
        }
        ShopCategory category = shopCategoryMapper.selectShopCategoryById(product.getCategoryId());
        if (category == null)
        {
            throw new ServiceException("商品分类不存在或已删除");
        }

        product.setCoverUrl(StringUtils.trim(product.getCoverUrl()));
        if (product.getOrderNum() == null)
        {
            product.setOrderNum(0);
        }
        if (StringUtils.isEmpty(product.getStatus()))
        {
            product.setStatus(ShopProduct.STATUS_NORMAL);
        }
        if (insert)
        {
            product.setSoldCount(0);
        }
    }
}
