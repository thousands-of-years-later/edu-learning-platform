package com.ruoyi.course.service;

import java.util.List;
import com.ruoyi.course.domain.Course;
import com.ruoyi.course.domain.CourseDetail;

/**
 * 课程信息 服务层
 *
 * @author edu
 */
public interface ICourseService
{
    /**
     * 查询课程列表
     *
     * 返回的每一行都会带 avgScore（平均分，无人评分为 null）。
     *
     * @param course 课程信息（可用 sortBy 指定排序：likeCount / createTime / avgScore）
     * @return 课程集合
     */
    public List<Course> selectCourseList(Course course);

    /**
     * 校验并归一化列表排序方式（Course.sortBy）：空 → 套默认值 createTime，白名单外 → 报错
     *
     * 必须在 startPage() 之前调用：PageHelper 的分页参数挂在 ThreadLocal 上，
     * startPage() 之后若抛异常而没有任何查询把它消费掉，参数会残留到本线程的下一条查询。
     *
     * @param course 课程信息（sortBy 为接口入参，本方法会就地改写它）
     */
    public void checkSortBy(Course course);

    /**
     * 根据课程ID查询课程
     *
     * @param courseId 课程ID
     * @return 课程信息
     */
    public Course selectCourseById(Long courseId);

    /**
     * 查询客户端课程详情：课程本体 + 章节（每章带自己的视频）+ 无章节的课程视频
     *
     * 只返回未删除且状态正常的课程；课程不存在、已删除或已停用时返回 null。
     * 视频只取 learning_course_video（课程内的课时视频），与短视频模块无关。
     * 章节、视频均按 order_num 升序；章节下没有视频时 videos 为空数组而不是 null。
     *
     * @param courseId 课程ID
     * @return 课程详情；课程不可见时返回 null
     */
    public CourseDetail selectCourseDetail(Long courseId);

    /**
     * 校验课程名称是否唯一
     *
     * @param course 课程信息
     * @return 结果
     */
    public boolean checkCourseNameUnique(Course course);

    /**
     * 新增课程
     *
     * @param course 课程信息
     * @return 结果
     */
    public int insertCourse(Course course);

    /**
     * 修改课程
     *
     * @param course 课程信息
     * @return 结果
     */
    public int updateCourse(Course course);

    /**
     * 批量逻辑删除课程，同时级联删除其下章节与视频
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    public int deleteCourseByIds(String ids);
}
