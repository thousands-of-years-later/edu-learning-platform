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
     * 批量逻辑删除课程，并清零 like_count / favorite_count / comment_count
     *
     * 不含 rating_count / rating_score：那两列归评分模块维护，本次不做评分级联。
     *
     * @param courseIds 需要删除的课程ID
     * @return 结果
     */
    public int deleteCourseByIds(Long[] courseIds);

    /**
     * 删除指向这些课程的点赞收藏（级联清理，硬删除）
     *
     * learning_interaction 在 learning-interation 模块，本模块只依赖 ruoyi-common，
     * 反向注入会形成循环依赖，按 CategoryMapper.countPostByCategoryId 的先例直接写纯 SQL。
     * 该表没有 del_flag（取消点赞即硬删除），这里同样是硬删除。
     * target_type 必须带上：三种目标的 ID 各自独立编号，只按 target_id 过滤会串到别的类型上。
     *
     * @param courseIds 被删除的课程ID
     * @return 结果
     */
    public int deleteInteractionByCourseIds(Long[] courseIds);

    /**
     * 逻辑删除指向这些课程的评论（级联清理）
     *
     * 同上走纯 SQL。课程评论挂的是 course_id（课程级），不是 learning_course_video.video_id。
     * where 带 del_flag = '0'，重复执行不会反复改写 update_time。
     *
     * @param courseIds 被删除的课程ID
     * @return 结果
     */
    public int softDeleteCommentByCourseIds(Long[] courseIds);

    /**
     * 统计某类别下的课程数量（供类别删除保护使用）
     *
     * @param categoryId 类别ID
     * @return 课程数量
     */
    public int countCourseByCategoryId(Long categoryId);
}
