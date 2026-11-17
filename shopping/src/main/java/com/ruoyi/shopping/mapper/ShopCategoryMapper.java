package com.ruoyi.shopping.mapper;

import java.util.List;
import com.ruoyi.shopping.domain.ShopCategory;

/**
 * 积分商城-商品分类 数据层
 *
 * @author edu
 */
public interface ShopCategoryMapper
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
     * 校验同 del_flag='0' 下名称是否重复
     */
    public ShopCategory checkShopCategoryNameUnique(ShopCategory category);

    public int insertShopCategory(ShopCategory category);

    public int updateShopCategory(ShopCategory category);

    /**
     * 批量逻辑删除（del_flag='2'）
     */
    public int deleteShopCategoryByIds(Long[] categoryIds);
}
