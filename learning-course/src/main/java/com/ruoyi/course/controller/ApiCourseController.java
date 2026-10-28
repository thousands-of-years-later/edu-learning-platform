package com.ruoyi.course.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.course.domain.Course;
import com.ruoyi.course.service.ICourseService;

/**
 * 课程 接口（给小程序用）
 *
 * 路径放在 /api 下，因此【默认就要求登录】—— ShiroConfig 只放行了
 * /api/login 与 /api/register，这个前缀天然满足「查课程必须登录」，不用改过滤链。
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
@RequestMapping("/api/course")
public class ApiCourseController extends BaseController
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
}
