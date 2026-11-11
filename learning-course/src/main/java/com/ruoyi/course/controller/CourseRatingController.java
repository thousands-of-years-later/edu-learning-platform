package com.ruoyi.course.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.course.domain.Course;
import com.ruoyi.course.domain.CourseRating;
import com.ruoyi.course.service.ICourseRatingService;
import com.ruoyi.course.service.ICourseService;

/**
 * 课程评价查看（后台）
 *
 * 不设独立「评价管理」菜单：从课程管理每行的「评价」按钮进入，
 * 展示课程信息、平均分/评分人数/文字评价数，以及每条评分（用户、星级、文字、时间）。
 *
 * 参数：courseId。权限：登录后可访问（与课程列表同级）。
 *
 * @author edu
 */
@Controller
@RequestMapping("/course/rating")
public class CourseRatingController extends BaseController
{
    /** 页面路径前缀 */
    private static final String PREFIX = "course/rating";

    @Autowired
    private ICourseRatingService courseRatingService;

    @Autowired
    private ICourseService courseService;

    /**
     * 某门课程的评价查看页
     *
     * @param courseId 课程ID
     */
    @GetMapping("/view")
    public String view(Long courseId, ModelMap mmap)
    {
        mmap.put("courseId", courseId);
        if (courseId == null)
        {
            mmap.put("error", "参数不合法，无法查看评价");
            return PREFIX + "/view";
        }

        Course course = courseService.selectCourseById(courseId);
        if (course == null)
        {
            mmap.put("error", "课程不存在或已被删除");
            return PREFIX + "/view";
        }

        List<CourseRating> ratings = courseRatingService.selectRatingList(courseId);

        // 平均分/评分人数/带文字的评价数：直接按列表算，避免再查一次统计
        int count = ratings.size();
        long scoreSum = 0;
        int withContentCount = 0;
        for (CourseRating rating : ratings)
        {
            scoreSum += rating.getScore() == null ? 0 : rating.getScore();
            if (StringUtils.isNotEmpty(rating.getContent()))
            {
                withContentCount++;
            }
        }

        mmap.put("course", course);
        mmap.put("ratings", ratings);
        mmap.put("ratingCount", count);
        mmap.put("avgScore", CourseRating.avgScore(count, scoreSum));
        mmap.put("withContentCount", withContentCount);
        return PREFIX + "/view";
    }

    /**
     * 删除一条评价（管理员）
     *
     * 删除后同步修正课程的评分人数与总分，课程详情的平均分不会漂移。
     *
     * @param ratingId 评分ID
     */
    @Log(title = "课程评价", businessType = BusinessType.DELETE)
    @PostMapping("/remove")
    @ResponseBody
    public AjaxResult remove(Long ratingId)
    {
        if (ratingId == null)
        {
            throw new ServiceException("评价ID不能为空");
        }
        // 菜单已取消，这里用"是否管理员"兜底：业务后台菜单只授给 admin
        if (!ShiroUtils.isAdmin())
        {
            throw new ServiceException("只有管理员可以删除评价");
        }
        return toAjax(courseRatingService.deleteRatingById(ratingId));
    }
}
