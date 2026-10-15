package com.ruoyi.course.service;

import java.util.List;
import com.ruoyi.course.domain.Course;

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
