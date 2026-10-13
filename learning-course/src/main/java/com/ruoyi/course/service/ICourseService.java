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
     * @param course 课程信息
     * @return 课程集合
     */
    public List<Course> selectCourseList(Course course);

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
