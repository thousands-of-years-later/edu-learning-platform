package com.ruoyi.shopping.service;

import java.util.List;
import com.ruoyi.shopping.domain.ShopCategory;

/**
 * 积分商城-商品分类 服务层
 *
 * @author edu
 */
public interface IShopCategoryService
{
    /**
     * 分类列表（后台，分页由控制器 startPage() 驱动）
     */
    public List<ShopCategory> selectShopCategoryList(ShopCategory category);

    /**
     * 按ID查未删除分类
     */
    public ShopCategory selectShopCategoryById(Long categoryId);

    /**
     * 校验分类名称唯一性（同 del_flag='0' 下）
     *
     * @return true = 唯一；false = 已存在
     */
    public boolean checkShopCategoryNameUnique(ShopCategory category);

    public int insertShopCategory(ShopCategory category);

    public int updateShopCategory(ShopCategory category);

    /**
     * 批量删除（逻辑删除）；分类下还有未删除商品时拒绝
     *
     * @param ids 逗号分隔的分类ID
     */
    public int deleteShopCategoryByIds(String ids);
}
