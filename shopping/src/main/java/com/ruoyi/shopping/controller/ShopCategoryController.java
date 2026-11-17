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
import com.ruoyi.shopping.service.IShopCategoryService;

/**
 * 积分商城-商品分类（后台）
 *
 * 页面模板：templates/shop/category/
 * 菜单与按钮权限：sql/learning_shop_menu.sql
 *
 * @author edu
 */
@Controller
@RequestMapping("/shop/category")
public class ShopCategoryController extends BaseController
{
    private String prefix = "shop/category";

    @Autowired
    private IShopCategoryService shopCategoryService;

    @RequiresPermissions("shop:category:view")
    @GetMapping()
    public String category()
    {
        return prefix + "/category";
    }

    @RequiresPermissions("shop:category:add")
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    @RequiresPermissions("shop:category:edit")
    @GetMapping("/edit/{categoryId}")
    public String edit(@PathVariable("categoryId") Long categoryId, ModelMap mmap)
    {
        mmap.put("shopCategory", shopCategoryService.selectShopCategoryById(categoryId));
        return prefix + "/edit";
    }

    /**
     * 分类列表数据
     *
     * 按仓库约定列表接口不加权限注解（后台登录即可读），增删改仍受权限控制。
     */
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(ShopCategory category)
    {
        startPage();
        List<ShopCategory> list = shopCategoryService.selectShopCategoryList(category);
        return getDataTable(list);
    }

    @RequiresPermissions("shop:category:add")
    @Log(title = "商城商品分类", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated ShopCategory category)
    {
        if (!shopCategoryService.checkShopCategoryNameUnique(category))
        {
            return error("新增分类'" + category.getCategoryName() + "'失败，分类名称已存在");
        }
        category.setCreateBy(getLoginName());
        return toAjax(shopCategoryService.insertShopCategory(category));
    }

    @RequiresPermissions("shop:category:edit")
    @Log(title = "商城商品分类", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Validated ShopCategory category)
    {
        if (!shopCategoryService.checkShopCategoryNameUnique(category))
        {
            return error("修改分类'" + category.getCategoryName() + "'失败，分类名称已存在");
        }
        category.setUpdateBy(getLoginName());
        return toAjax(shopCategoryService.updateShopCategory(category));
    }

    @RequiresPermissions("shop:category:remove")
    @Log(title = "商城商品分类", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(shopCategoryService.deleteShopCategoryByIds(ids));
    }

    /**
     * 分类名称唯一性校验（新增/编辑表单远程校验）
     */
    @PostMapping("/checkCategoryNameUnique")
    @ResponseBody
    public boolean checkCategoryNameUnique(ShopCategory category)
    {
        return shopCategoryService.checkShopCategoryNameUnique(category);
    }
}
