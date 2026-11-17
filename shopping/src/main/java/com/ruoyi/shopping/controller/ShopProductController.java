package com.ruoyi.shopping.controller;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.shopping.domain.ShopCategory;
import com.ruoyi.shopping.domain.ShopProduct;
import com.ruoyi.shopping.service.IShopCategoryService;
import com.ruoyi.shopping.service.IShopProductService;

/**
 * 积分商城-商品（后台）
 *
 * 页面模板：templates/shop/product/
 * 菜单与按钮权限：sql/learning_shop_menu.sql
 *
 * @author edu
 */
@Controller
@RequestMapping("/shop/product")
public class ShopProductController extends BaseController
{
    private String prefix = "shop/product";

    @Autowired
    private IShopProductService shopProductService;

    @Autowired
    private IShopCategoryService shopCategoryService;

    @RequiresPermissions("shop:product:view")
    @GetMapping()
    public String product(ModelMap mmap)
    {
        // 列表页的「分类」筛选下拉
        mmap.put("categories", shopCategoryService.selectShopCategoryList(new ShopCategory()));
        return prefix + "/product";
    }

    @RequiresPermissions("shop:product:add")
    @GetMapping("/add")
    public String add(ModelMap mmap)
    {
        // 停用的分类也要列出来：允许先建商品再启用分类
        mmap.put("categories", shopCategoryService.selectShopCategoryList(new ShopCategory()));
        return prefix + "/add";
    }

    @RequiresPermissions("shop:product:edit")
    @GetMapping("/edit/{productId}")
    public String edit(@PathVariable("productId") Long productId, ModelMap mmap)
    {
        mmap.put("shopProduct", shopProductService.selectShopProductById(productId));
        // 停用的分类也要列出来，否则编辑页选不回原值
        mmap.put("categories", shopCategoryService.selectShopCategoryList(new ShopCategory()));
        return prefix + "/edit";
    }

    /**
     * 商品列表数据
     *
     * 按仓库约定列表接口不加权限注解（后台登录即可读），增删改仍受权限控制。
     */
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(ShopProduct product)
    {
        startPage();
        List<ShopProduct> list = shopProductService.selectShopProductList(product);
        return getDataTable(list);
    }

    @RequiresPermissions("shop:product:add")
    @Log(title = "商城商品", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated ShopProduct product)
    {
        product.setCreateBy(getLoginName());
        return toAjax(shopProductService.insertShopProduct(product));
    }

    @RequiresPermissions("shop:product:edit")
    @Log(title = "商城商品", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Validated ShopProduct product)
    {
        product.setUpdateBy(getLoginName());
        return toAjax(shopProductService.updateShopProduct(product));
    }

    @RequiresPermissions("shop:product:remove")
    @Log(title = "商城商品", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(shopProductService.deleteShopProductByIds(ids));
    }
}
