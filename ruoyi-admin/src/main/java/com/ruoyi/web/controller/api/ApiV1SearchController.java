package com.ruoyi.web.controller.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.community.domain.Post;
import com.ruoyi.community.service.IPostService;
import com.ruoyi.course.domain.Course;
import com.ruoyi.course.service.ICourseService;
import com.ruoyi.video.domain.ShortVideo;
import com.ruoyi.video.service.IShortVideoService;

/**
 * 搜索 接口 v1（Token 认证，给小程序用）
 *
 * 视频 / 帖子 / 课程分开搜索，三个独立接口，结果固定按点赞数倒序。
 * 复用各模块已有的列表 Service 与标题模糊查询，不建搜索表、不引搜索引擎。
 *
 * 出口在 /api/v1/** 下：ShiroConfig 里整体放行，鉴权由 ApiTokenFilter 完成，
 * 所以这里【不加】@Anonymous 注解（标了反而会被误读成这个接口不需要登录）。
 *
 * 实现约定：
 * - 入参只有 keyword，实体在方法内 new、过滤/排序字段全部服务端赋值 ——
 *   不让客户端通过 sortBy/status/ownerId 之类的参数改排序或绕过可见性过滤；
 * - 只返回可见内容：del_flag='0' 且 status='0'（正常视频 / 已发布帖子 / 正常课程）；
 * - keyword 缺失或空白 → 不过滤，等价于「按点赞数的热门榜」；
 * - 全部赋值在 startPage() 之前（PageHelper 分页参数挂在 ThreadLocal 上）；
 * - 只搜标题，不搜正文（帖子列表 SQL 本就不带 content）。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/search")
public class ApiV1SearchController extends BaseController
{
    @Autowired
    private IShortVideoService shortVideoService;

    @Autowired
    private IPostService postService;

    @Autowired
    private ICourseService courseService;

    /**
     * 视频搜索（按点赞数倒序）
     *
     * @param keyword 标题关键词（模糊匹配；空则返回全部）
     * @return {rows: [短视频], total}（分页参数 pageNum / pageSize）
     */
    @GetMapping("/video")
    public TableDataInfo video(String keyword)
    {
        ShortVideo query = new ShortVideo();
        query.setVideoTitle(StringUtils.trim(keyword));
        // 客户端传什么 status 都不算数：停用视频对 App 不可见
        query.setStatus(ShortVideo.STATUS_NORMAL);
        query.setSortBy(ShortVideo.SORT_BY_LIKE_COUNT);
        startPage();
        return getDataTable(shortVideoService.selectShortVideoList(query));
    }

    /**
     * 帖子搜索（按点赞数倒序）
     *
     * @param keyword 标题关键词（模糊匹配；空则返回全部）
     * @return {rows: [帖子], total}（分页参数 pageNum / pageSize）
     */
    @GetMapping("/post")
    public TableDataInfo post(String keyword)
    {
        Post query = new Post();
        query.setPostTitle(StringUtils.trim(keyword));
        // 只搜已发布：草稿/待审核/已下架/已驳回不出现（ownerId 留空，不做归属范围限制）
        query.setStatus(Post.STATUS_PUBLISHED);
        query.setSortBy(Post.SORT_BY_LIKE_COUNT);
        startPage();
        return getDataTable(postService.selectPostList(query));
    }

    /**
     * 课程搜索（按点赞数倒序）
     *
     * @param keyword 课程名关键词（模糊匹配；空则返回全部）
     * @return {rows: [课程（含 avgScore，无人评分为 null）], total}（分页参数 pageNum / pageSize）
     */
    @GetMapping("/course")
    public TableDataInfo course(String keyword)
    {
        Course query = new Course();
        query.setCourseName(StringUtils.trim(keyword));
        // 与 /api/v1/course/list 一致：客户端传什么 status 都不算数，只返回正常课程
        query.setStatus("0");
        // 复用课程已有的排序白名单（CourseMapper.xml 里翻译成 like_count desc）
        query.setSortBy(Course.SORT_BY_LIKE_COUNT);
        startPage();
        return getDataTable(courseService.selectCourseList(query));
    }
}
