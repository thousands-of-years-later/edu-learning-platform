package com.ruoyi.course.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.course.domain.Course;
import com.ruoyi.course.domain.CourseDetail;
import com.ruoyi.course.service.ICourseService;

/**
 * 课程 接口 v1（Token 认证，给小程序用）
 *
 * 与 ApiCourseController（/api/course）功能一一对应，
 * 差别只在鉴权：老的走 Shiro 会话（/api/** 默认要求登录）；
 * 这里走 Token：/api/v1/** 在 ShiroConfig 里是 anon，Shiro 没有登录态，
 * 鉴权由 ApiTokenFilter 完成（ApiContext 里就有用户身份，但本接口只是查课程，用不到）。
 *
 * 两个入口同时存在：老客户端不用改，新客户端用 /api/v1。
 * 这里【不加】@Anonymous 注解 —— /api/v1/** 是整体放行的，真正的鉴权由 ApiTokenFilter 做，
 * 标上 @Anonymous 反而会让人误以为这个接口不需要登录。
 *
 * 列表按三种方式查（都是倒序）：
 *   sortBy=likeCount  按点赞数
 *   sortBy=createTime 按上传日期（缺省值）
 *   sortBy=avgScore   按综合评分（平均分 = rating_score / rating_count，无人评分排最后）
 * 非法取值由服务层统一报错，不静默套默认值。
 *
 * 只返回「正常」状态的课程：与点赞/收藏/评分/评论的 existsCourse 可见性规则一致，
 * 停用课程对客户端不可见。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/course")
public class ApiV1CourseController extends BaseController
{
    @Autowired
    private ICourseService courseService;

    /**
     * 课程列表（分页）
     *
     * @param course 过滤：courseName（模糊）、categoryId；排序：sortBy（缺省 createTime）
     * @return {rows: [课程（含 viewCount 阅读数、avgScore 平均分，无人评分为 null）], total}
     *         三种排序共用同一返回结构，点赞数 likeCount / 日期 createTime 也始终返回
     *         （分页参数 pageNum / pageSize）
     */
    @GetMapping("/list")
    public TableDataInfo list(Course course)
    {
        // 校验/归一化必须在 startPage() 之前：PageHelper 的分页参数挂在 ThreadLocal 上，
        // 校验失败若发生在 startPage() 之后且没有查询消费它，参数会残留到本线程的下一条查询
        courseService.checkSortBy(course);
        // 客户端传什么 status 都不算数：App 只能看到正常课程
        course.setStatus("0");
        startPage();
        List<Course> list = courseService.selectCourseList(course);
        return getDataTable(list);
    }

    /**
     * 课程详情：课程本体（简介、封面等）+ 章节（每章带自己的课程视频）+ 无章节的课程视频
     *
     * 只返回未删除且状态正常的课程，停用 / 删除与不存在一样返回错误，避免探测。
     * 视频来自 learning_course_video（课程内的课时视频），不是短视频模块。
     * 章节与视频均按 order_num 升序；章节下没有视频时该章的 videos 为空数组。
     *
     * @param courseId 课程ID
     * @return {code:0, data:{course:{...}, chapters:[{...,videos:[...]}], videos:[...]}}
     */
    @GetMapping("/{courseId}")
    public AjaxResult detail(@PathVariable("courseId") String courseId)
    {
        // 路径参数用 String 接、手动转 Long：非法 ID 也要返回 JSON，
        // 交给 Spring 做 Long 绑定会抛类型转换异常、走到 HTML 错误页
        Long id = Convert.toLong(courseId);
        if (StringUtils.isNull(id))
        {
            return AjaxResult.error("课程ID不合法");
        }
        CourseDetail detail = courseService.selectCourseDetail(id);
        if (StringUtils.isNull(detail))
        {
            return AjaxResult.error("课程不存在或已下架");
        }
        return AjaxResult.success(detail);
    }
}
