package com.ruoyi.course.controller;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
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
 * 课程类别接口
 *
 * 与社区类别（CommunityCategoryController）共用同一张 learning_category 表，
 * 靠 category_type 隔离数据：本控制器只碰 category_type = 'course' 的数据。
 *
 * 本控制器只提供 JSON 接口，不带页面。前端做好后再补回返回视图的方法，
 * 并执行 sql/learning_category_menu.sql 恢复菜单与按钮权限。
 *
 * @author edu
 */
@Controller
@RequestMapping("/course/category")
public class CourseCategoryController extends BaseController
{
    /** 本模块的类别类型，服务端常量，前端传入的 categoryType 一律被覆盖 */
    private static final String CATEGORY_TYPE = Category.TYPE_COURSE;

    @Autowired
    private ICategoryService categoryService;

    /**
     * 查询课程类别列表
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

    @Log(title = "课程类别", businessType = BusinessType.EXPORT)
    @RequiresPermissions("course:category:export")
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Category category)
    {
        category.setCategoryType(CATEGORY_TYPE);
        List<Category> list = categoryService.selectCategoryList(category);
        ExcelUtil<Category> util = new ExcelUtil<Category>(Category.class);
        return util.exportExcel(list, "课程类别数据");
    }

    @RequiresPermissions("course:category:remove")
    @Log(title = "课程类别", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(categoryService.deleteCategoryByIds(ids, CATEGORY_TYPE));
    }

    /**
     * 新增保存课程类别
     */
    @RequiresPermissions("course:category:add")
    @Log(title = "课程类别", businessType = BusinessType.INSERT)
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
     * 修改保存课程类别
     */
    @RequiresPermissions("course:category:edit")
    @Log(title = "课程类别", businessType = BusinessType.UPDATE)
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
     * 校验课程类别名称唯一性
     */
    @PostMapping("/checkCategoryNameUnique")
    @ResponseBody
    public boolean checkCategoryNameUnique(Category category)
    {
        category.setCategoryType(CATEGORY_TYPE);
        return categoryService.checkCategoryNameUnique(category);
    }
}
