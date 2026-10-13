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
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.course.domain.CourseChapter;
import com.ruoyi.course.service.ICourseChapterService;

/**
 * 课程章节 控制器
 *
 * 章节是课程的从属内容，不单开权限：读用 course:course:view，
 * 写复用 course:course:add / edit / remove，同样是管理员专属。
 *
 * @author edu
 */
@Controller
@RequestMapping("/course/chapter")
public class CourseChapterController extends BaseController
{
    /** 页面根路径，模板位于 templates/<prefix>/ 下 */
    private String prefix = "course/chapter";

    /** 非管理员尝试写操作时的统一提示 */
    private static final String ADMIN_ONLY_MSG = "只有超级管理员可以维护课程数据";

    @Autowired
    private ICourseChapterService courseChapterService;

    /**
     * 章节归属于某个课程，courseId 缺失时直接回到课程列表，
     * 否则查询条件为空会把全库章节都列出来。
     */
    @RequiresPermissions("course:course:view")
    @GetMapping()
    public String chapter(Long courseId, ModelMap mmap)
    {
        if (StringUtils.isNull(courseId))
        {
            return "redirect:/course/course";
        }
        mmap.put("courseId", courseId);
        return prefix + "/chapter";
    }

    /**
     * courseId 走路径变量：RuoYi 的 $.operate.add(id) 只会替换 createUrl 里的 {id} 占位符，
     * 带不了 query string（见 system/dict/data 的同款写法）。
     */
    @RequiresPermissions("course:course:add")
    @GetMapping("/add/{courseId}")
    public String add(@PathVariable("courseId") Long courseId, ModelMap mmap)
    {
        mmap.put("courseId", courseId);
        return prefix + "/add";
    }

    @RequiresPermissions("course:course:edit")
    @GetMapping("/edit/{chapterId}")
    public String edit(@PathVariable("chapterId") Long chapterId, ModelMap mmap)
    {
        mmap.put("chapter", courseChapterService.selectChapterById(chapterId));
        return prefix + "/edit";
    }

    /**
     * 按约定不加权限注解：章节列表对普通用户开放
     */
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(CourseChapter chapter)
    {
        startPage();
        List<CourseChapter> list = courseChapterService.selectChapterList(chapter);
        return getDataTable(list);
    }

    @RequiresPermissions("course:course:remove")
    @Log(title = "课程章节", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        if (!ShiroUtils.isAdmin())
        {
            return error(ADMIN_ONLY_MSG);
        }
        return toAjax(courseChapterService.deleteChapterByIds(ids));
    }

    @RequiresPermissions("course:course:add")
    @Log(title = "课程章节", businessType = BusinessType.INSERT)
    @PostMapping("/add")
    @ResponseBody
    public AjaxResult addSave(@Validated CourseChapter chapter)
    {
        if (!ShiroUtils.isAdmin())
        {
            return error(ADMIN_ONLY_MSG);
        }
        chapter.setCreateBy(getLoginName());
        return toAjax(courseChapterService.insertChapter(chapter));
    }

    @RequiresPermissions("course:course:edit")
    @Log(title = "课程章节", businessType = BusinessType.UPDATE)
    @PostMapping("/edit")
    @ResponseBody
    public AjaxResult editSave(@Validated CourseChapter chapter)
    {
        if (!ShiroUtils.isAdmin())
        {
            return error(ADMIN_ONLY_MSG);
        }
        chapter.setUpdateBy(getLoginName());
        return toAjax(courseChapterService.updateChapter(chapter));
    }
}
