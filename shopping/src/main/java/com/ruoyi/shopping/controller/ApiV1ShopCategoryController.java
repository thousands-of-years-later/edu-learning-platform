package com.ruoyi.shopping.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.shopping.domain.ShopCategory;
import com.ruoyi.shopping.service.IShopCategoryService;

/**
 * 积分商城-商品分类 接口 v1（Token 认证，给 App / 小程序用）
 *
 * GET /api/v1/shop/category/list
 *
 * /api/v1/** 在 Shiro 里是 anon，鉴权由 ApiTokenFilter 完成；这里不需要 @Anonymous。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/shop/category")
public class ApiV1ShopCategoryController extends BaseController
{
    @Autowired
    private IShopCategoryService shopCategoryService;

    /**
     * 启用中的商品分类列表（不分页）
     *
     * 只返回 status='0' 且未删除的分类；停用/删除分类下的商品在商品接口里也会被过滤掉。
     *
     * @return {rows:[{categoryId, categoryName, orderNum, ...}], total}
     */
    @GetMapping("/list")
    public TableDataInfo list()
    {
        ShopCategory query = new ShopCategory();
        query.setStatus(ShopCategory.STATUS_NORMAL);
        return getDataTable(shopCategoryService.selectShopCategoryList(query));
    }
}
