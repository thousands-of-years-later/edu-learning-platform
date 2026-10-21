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
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.course.domain.Banner;
import com.ruoyi.course.domain.Category;
import com.ruoyi.course.domain.Course;
import com.ruoyi.course.service.IBannerService;
import com.ruoyi.course.service.ICategoryService;
import com.ruoyi.course.service.ICourseService;

/**
 * 课程轮播图接口
 *
 * 每个课程类别一个轮播窗口，另有一个「全部」窗口（categoryId = 0，单独维护）。
 * 页面模板位于 templates/course/banner/ 下，菜单与按钮权限见 sql/learning_banner_menu.sql。
 *
 * 权限口径就是「普通用户只能看」：查询接口按约定不加权限注解、对所有登录用户开放，
 * 增删改导出受 @RequiresPermissions 控制，且 sql/learning_banner_menu.sql 只把这些权限授给 admin。
 * 这里【不加】ShiroUtils.isAdmin() 守卫 —— 它是 user_id == 1，会把 eduadmin 这类
 * user_id != 1 但 role_key='admin' 的账号锁在门外（与 CourseCategoryController 保持一致）。
 *
 * @author edu
 */
@Controller
@RequestMapping("/course/banner")
public class BannerController extends BaseController
{
    /** 页面根路径，模板位于 templates/<prefix>/ 下 */
    private String prefix = "course/banner";

    @Autowired
    private IBannerService bannerService;

    @Autowired
    private ICategoryService categoryService;

    @Autowired
    private ICourseService courseService;

    /**
     * 轮播图列表页
     */
    @RequiresPermissions("course:banner:view")
    @GetMapping()
    public String banner(ModelMap mmap)
    {
        mmap.put("categories", selectCourseCategories());
        return prefix + "/banner";
    }

    /**
     * 新增轮播图页
     */
    @RequiresPermissions("course:banner:add")
    @GetMapping("/add")
    public String add(ModelMap mmap)
    {
        mmap.put("categories", selectCourseCategories());
        mmap.put("courses", selectCourseOptions());
        return prefix + "/add";
    }

    /**
     * 修改轮播图页
     */
    @RequiresPermissions("course:banner:edit")
    @GetMapping("/edit/{bannerId}")
    public String edit(@PathVariable("bannerId") Long bannerId, ModelMap mmap)
    {
        mmap.put("banner", bannerService.selectBannerById(bannerId));
        mmap.put("categories", selectCourseCategories());
        mmap.put("courses", selectCourseOptions());
        return prefix + "/edit";
    }

    /**
     * 查询轮播图列表
     *
     * 按约定不加权限注解：所有登录用户都能读这个列表，写操作仍受权限控制。
     */
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(Banner banner)
    {
        startPage();
        List<Banner> list = bannerService.selectBannerList(banner);
        return getDataTable(list);
    }

    @Log(title = "轮播图", businessType = BusinessType.EXPORT)
    @RequiresPermissions("course:banner:export")
    @PostMapping("/export")
    @ResponseBody
    public AjaxResult export(Banner banner)
    {
        List<Banner> list = bannerService.selectBannerList(banner);
        ExcelUtil<Banner> util = new ExcelUtil<Banner>(Banner.class);
        return util.exportExcel(list, "轮播图数据");
    }

    @RequiresPermissions("course:banner:remove")
    @Log(title = "轮播图", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(bannerService.deleteBannerByIds(ids));
    }

    /**
     * 新增保存轮播图
     */
    @RequiresPermissions("course:banner:add")
    @Log(title = "轮播图", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated Banner banner)
    {
        banner.setCreateBy(getLoginName());
        return toAjax(bannerService.insertBanner(banner));
    }

    /**
     * 修改保存轮播图
     */
    @RequiresPermissions("course:banner:edit")
    @Log(title = "轮播图", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Validated Banner banner)
    {
        banner.setUpdateBy(getLoginName());
        return toAjax(bannerService.updateBanner(banner));
    }

    /**
     * 可选类别列表（含「全部」选项由页面自己加）
     *
     * 【不按 status 过滤】：如果把停用的类别筛掉，某条轮播的类别事后被停用时，
     * 编辑页的 <select> 渲染不出当前值、浏览器提交空值，运营想改个标题都存不了。
     */
    private List<Category> selectCourseCategories()
    {
        Category query = new Category();
        query.setCategoryType(Category.TYPE_COURSE);
        return categoryService.selectCategoryList(query);
    }

    /**
     * 可选课程列表（下拉框用，页面按「课程名（类别名）」展示）
     *
     * 【不按 status 过滤】，理由同上：过滤掉停用课程会让编辑页选不回原值。
     * 课程可见性由 App 端的 exists 过滤兜底，不靠这个下拉框。
     *
     * 注意这里【不能】调用 startPage()：本方法在页面方法里跑，分页参数会污染后续查询。
     */
    private List<Course> selectCourseOptions()
    {
        return courseService.selectCourseList(new Course());
    }
}
