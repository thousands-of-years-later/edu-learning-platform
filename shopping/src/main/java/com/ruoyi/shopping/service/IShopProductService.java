package com.ruoyi.shopping.service;

import java.util.List;
import com.ruoyi.shopping.domain.ShopProduct;

/**
 * 积分商城-商品 服务层
 *
 * @author edu
 */
public interface IShopProductService
{
    /**
     * 后台商品列表（含分类名称，分页由控制器 startPage() 驱动）
     */
    public List<ShopProduct> selectShopProductList(ShopProduct product);

    /**
     * 后台按ID查未删除商品
     */
    public ShopProduct selectShopProductById(Long productId);

    /**
     * 用户端可见商品列表：上架、未删除、分类启用且未删除
     */
    public List<ShopProduct> selectVisibleProductList(ShopProduct product);

    /**
     * 用户端按ID查可见商品（不存在/下架/分类不可用返回 null）
     */
    public ShopProduct selectVisibleProductById(Long productId);

    public int insertShopProduct(ShopProduct product);

    public int updateShopProduct(ShopProduct product);

    /**
     * 批量删除（逻辑删除）；兑换记录里的商品快照不受影响
     *
     * @param ids 逗号分隔的商品ID
     */
    public int deleteShopProductByIds(String ids);
}
