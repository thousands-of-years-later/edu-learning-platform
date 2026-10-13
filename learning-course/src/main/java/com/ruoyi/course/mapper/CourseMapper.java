package com.ruoyi.course.mapper;

import java.util.List;
import com.ruoyi.course.domain.Course;

/**
 * 课程信息 数据层
 *
 * @author edu
 */
public interface CourseMapper
{
    /**
     * 查询课程列表（关联查出类别名称）
     *
     * @param course 课程信息
     * @return 课程集合
     */
    public List<Course> selectCourseList(Course course);

    /**
     * 根据课程ID查询课程（只查未删除的）
     *
     * @param courseId 课程ID
     * @return 课程信息
     */
    public Course selectCourseById(Long courseId);

    /**
     * 校验课程名称是否唯一（只查未删除的）
     *
     * @param course 课程信息（courseName 必传）
     * @return 课程信息
     */
    public Course checkCourseNameUnique(Course course);

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
     * 批量逻辑删除课程
     *
     * @param courseIds 需要删除的课程ID
     * @return 结果
     */
    public int deleteCourseByIds(Long[] courseIds);

    /**
     * 统计某类别下的课程数量（供类别删除保护使用）
     *
     * @param categoryId 类别ID
     * @return 课程数量
     */
    public int countCourseByCategoryId(Long categoryId);
}
