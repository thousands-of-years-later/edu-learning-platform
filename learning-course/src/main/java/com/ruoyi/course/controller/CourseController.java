package com.ruoyi.course.controller;

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
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.course.domain.Category;
import com.ruoyi.course.domain.Course;
import com.ruoyi.course.service.ICategoryService;
import com.ruoyi.course.service.ICourseService;

/**
 * 课程信息 控制器
 *
 * 写操作（增删改）只允许超级管理员（user_id == 1）：权限注解之外再加一道
 * ShiroUtils.isAdmin() 校验，因为「增删改只能是管理员」是硬性要求，
 * 不能只依赖 sys_role_menu 的授权行（授权行可能被后续脚本改动）。
 *
 * @author edu
 */
@Controller
@RequestMapping("/course/course")
public class CourseController extends BaseController
{
    /** 页面根路径，模板位于 templates/<prefix>/ 下 */
    private String prefix = "course/course";

    /** 非管理员尝试写操作时的统一提示 */
    private static final String ADMIN_ONLY_MSG = "只有超级管理员可以维护课程数据";

    @Autowired
    private ICourseService courseService;

    @Autowired
    private ICategoryService categoryService;

    @RequiresPermissions("course:course:view")
    @GetMapping()
    public String course(ModelMap mmap)
    {
        // 列表页的「类别」筛选项
        mmap.put("categories", selectCourseCategories());
        return prefix + "/course";
    }

    @RequiresPermissions("course:course:add")
    @GetMapping("/add")
    public String add(ModelMap mmap)
    {
        mmap.put("categories", selectCourseCategories());
        return prefix + "/add";
    }

    @RequiresPermissions("course:course:edit")
    @GetMapping("/edit/{courseId}")
    public String edit(@PathVariable("courseId") Long courseId, ModelMap mmap)
    {
        mmap.put("course", courseService.selectCourseById(courseId));
        mmap.put("categories", selectCourseCategories());
        return prefix + "/edit";
    }

    /**
     * 按约定不加权限注解：课程/章节/视频列表都要能被普通用户查看
     */
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Course course)
    {
        startPage();
        List<Course> list = courseService.selectCourseList(course);
        return getDataTable(list);
    }

    @Log(title = "课程", businessType = BusinessType.EXPORT)
    @RequiresPermissions("course:course:export")
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Course course)
    {
        List<Course> list = courseService.selectCourseList(course);
        ExcelUtil<Course> util = new ExcelUtil<Course>(Course.class);
        return util.exportExcel(list, "课程数据");
    }

    @RequiresPermissions("course:course:remove")
    @Log(title = "课程", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        if (!ShiroUtils.isAdmin())
        {
            return error(ADMIN_ONLY_MSG);
        }
        return toAjax(courseService.deleteCourseByIds(ids));
    }

    @RequiresPermissions("course:course:add")
    @Log(title = "课程", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated Course course)
    {
        if (!ShiroUtils.isAdmin())
        {
            return error(ADMIN_ONLY_MSG);
        }
        if (!courseService.checkCourseNameUnique(course))
        {
            return error("新增课程'" + course.getCourseName() + "'失败，课程名称已存在");
        }
        course.setCreateBy(getLoginName());
        return toAjax(courseService.insertCourse(course));
    }

    @RequiresPermissions("course:course:edit")
    @Log(title = "课程", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Validated Course course)
    {
        if (!ShiroUtils.isAdmin())
        {
            return error(ADMIN_ONLY_MSG);
        }
        if (!courseService.checkCourseNameUnique(course))
        {
            return error("修改课程'" + course.getCourseName() + "'失败，课程名称已存在");
        }
        course.setUpdateBy(getLoginName());
        return toAjax(courseService.updateCourse(course));
    }

    @PostMapping("/checkCourseNameUnique")
    @ResponseBody
    public boolean checkCourseNameUnique(Course course)
    {
        return courseService.checkCourseNameUnique(course);
    }

    /**
     * 课程表单的类别下拉：只取课程模块下状态正常的类别
     */
    private List<Category> selectCourseCategories()
    {
        Category category = new Category();
        category.setCategoryType(Category.TYPE_COURSE);
        category.setStatus("0");
        return categoryService.selectCategoryList(category);
    }
}
