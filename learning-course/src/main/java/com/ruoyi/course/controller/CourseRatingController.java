package com.ruoyi.course.controller;

import java.util.List;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.course.domain.CourseRating;
import com.ruoyi.course.service.ICourseRatingService;

/**
 * 课程评价管理（后台）
 *
 * 客户端评分接口在 /api/v1/rating（add / update / info / list），这里是管理员用的：
 * 看全站评价（含文字评价），删刷分/违规评价。
 *
 * 删除是【物理删除】（评分表没有 del_flag），删完同步修正课程的评分人数与总分。
 *
 * 权限：course:rating:view / list / remove（只给 admin）。
 *
 * @author edu
 */
@Controller
@RequestMapping("/course/rating")
public class CourseRatingController extends BaseController
{
    /** 页面根路径，模板位于 templates/<prefix>/ 下 */
    private String prefix = "course/rating";

    @Autowired
    private ICourseRatingService courseRatingService;

    /**
     * 评价列表页
     */
    @RequiresPermissions("course:rating:view")
    @GetMapping()
    public String rating()
    {
        return prefix + "/rating";
    }

    /**
     * 评价列表数据（分页）
     *
     * @param rating 查询条件：courseId、score、content（模糊）、userId
     */
    @RequiresPermissions("course:rating:list")
    @PostMapping("/list")
    @ResponseBody
    public TableDataInfo list(CourseRating rating)
    {
        startPage();
        List<CourseRating> list = courseRatingService.selectAdminList(rating);
        return getDataTable(list);
    }

    /**
     * 删除评价（批量，逗号分隔；评分表物理删除并修正课程统计）
     */
    @RequiresPermissions("course:rating:remove")
    @Log(title = "评价管理", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(String ids)
    {
        return toAjax(courseRatingService.deleteRatingByIds(ids));
    }
}
