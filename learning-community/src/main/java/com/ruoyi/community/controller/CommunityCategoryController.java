package com.ruoyi.community.controller;

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
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.course.domain.Category;
import com.ruoyi.course.service.ICategoryService;

/**
 * 社区类别接口
 *
 * 与课程类别（CourseCategoryController）共用同一张 learning_category 表，
 * 靠 category_type 隔离数据：本控制器只碰 category_type = 'article' 的数据。
 * 类别领域对象与服务来自依赖的 learning-course 模块。
 *
 * 页面模板位于 templates/community/category/ 下，菜单与按钮权限见
 * sql/learning_category_menu.sql。
 *
 * @author edu
 */
@Controller
@RequestMapping("/community/category")
public class CommunityCategoryController extends BaseController
{
    /** 页面根路径，模板位于 templates/<prefix>/ 下 */
    private String prefix = "community/category";

    /** 本模块的类别类型，服务端常量，前端传入的 categoryType 一律被覆盖 */
    private static final String CATEGORY_TYPE = Category.TYPE_ARTICLE;

    @Autowired
    private ICategoryService categoryService;

    /**
     * 社区类别列表页
     */
    @RequiresPermissions("community:category:view")
    @GetMapping()
    public String category()
    {
        return prefix + "/category";
    }

    /**
     * 新增社区类别页
     */
    @RequiresPermissions("community:category:add")
    @GetMapping("/add")
    public String add()
    {
        return prefix + "/add";
    }

    /**
     * 修改社区类别页
     */
    @RequiresPermissions("community:category:edit")
    @GetMapping("/edit/{categoryId}")
    public String edit(@PathVariable("categoryId") Long categoryId, ModelMap mmap)
    {
        mmap.put("category", categoryService.selectCategoryById(categoryId));
        return prefix + "/edit";
    }

    /**
     * 查询社区类别列表
     *
     * 按约定不加权限注解：课程/文章都要挂类别，普通用户需要能读到类别列表，
     * 所以查询对所有登录用户开放，增删改仍受权限控制。
     */
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Category category)
    {
        startPage();
        category.setCategoryType(CATEGORY_TYPE);
        List<Category> list = categoryService.selectCategoryList(category);
        return getDataTable(list);
    }

    @Log(title = "社区类别", businessType = BusinessType.EXPORT)
    @RequiresPermissions("community:category:export")
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Category category)
    {
        category.setCategoryType(CATEGORY_TYPE);
        List<Category> list = categoryService.selectCategoryList(category);
        ExcelUtil<Category> util = new ExcelUtil<Category>(Category.class);
        return util.exportExcel(list, "社区类别数据");
    }

    @RequiresPermissions("community:category:remove")
    @Log(title = "社区类别", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(categoryService.deleteCategoryByIds(ids, CATEGORY_TYPE));
    }

    /**
     * 新增保存社区类别
     */
    @RequiresPermissions("community:category:add")
    @Log(title = "社区类别", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated Category category)
    {
        // 先强制类型，再做同类型下的重名校验
        category.setCategoryType(CATEGORY_TYPE);
        if (!categoryService.checkCategoryNameUnique(category))
        {
            return error("新增类别'" + category.getCategoryName() + "'失败，类别名称已存在");
        }
        category.setCreateBy(getLoginName());
        return toAjax(categoryService.insertCategory(category));
    }

    /**
     * 修改保存社区类别
     */
    @RequiresPermissions("community:category:edit")
    @Log(title = "社区类别", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Validated Category category)
    {
        category.setCategoryType(CATEGORY_TYPE);
        if (!categoryService.checkCategoryNameUnique(category))
        {
            return error("修改类别'" + category.getCategoryName() + "'失败，类别名称已存在");
        }
        category.setUpdateBy(getLoginName());
        return toAjax(categoryService.updateCategory(category));
    }

    /**
     * 校验社区类别名称唯一性
     */
    @PostMapping("/checkCategoryNameUnique")
    @ResponseBody
    public boolean checkCategoryNameUnique(Category category)
    {
        category.setCategoryType(CATEGORY_TYPE);
        return categoryService.checkCategoryNameUnique(category);
    }
}
