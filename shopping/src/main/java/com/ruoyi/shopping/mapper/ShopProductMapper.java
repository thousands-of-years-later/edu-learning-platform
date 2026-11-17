package com.ruoyi.shopping.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.shopping.domain.ShopProduct;

/**
 * 积分商城-商品 数据层
 *
 * @author edu
 */
public interface ShopProductMapper
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
     * 批量逻辑删除（del_flag='2'）
     */
    public int deleteShopProductByIds(Long[] productIds);

    /**
     * 分类下未删除商品数量（删除分类前校验）
     */
    public int countByCategoryId(Long categoryId);

    /**
     * 兑换扣库存：一条 UPDATE 同时扣 1 件库存、加 1 次已兑换。
     *
     * where 里的 stock >= 1、status='0'、del_flag='0'、价格一致、分类可用 是防超卖/防信息变更的闸门，
     * 影响 0 行即调用方报错，事务回滚。
     *
     * @param productId   商品ID
     * @param pointsPrice 兑换时看到的积分价格（价格被后台改动则影响 0 行）
     * @return 影响行数（1 成功 / 0 失败）
     */
    public int decreaseStock(@Param("productId") Long productId, @Param("pointsPrice") int pointsPrice);
}
