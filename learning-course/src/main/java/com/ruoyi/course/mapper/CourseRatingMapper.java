package com.ruoyi.course.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.course.domain.CourseRating;

/**
 * 课程评分 数据层
 *
 * @author edu
 */
public interface CourseRatingMapper
{
    /**
     * 新增一条评分（重复评分由唯一索引 uk_course_user 报出，服务层捕获 DuplicateKeyException）
     */
    public int insertRating(CourseRating rating);

    /**
     * 修改当前用户对某门课程的评分（course_id + user_id 同时进 where，别人的分改不到）
     */
    public int updateScoreByCourseAndUser(CourseRating rating);

    /**
     * 查询当前用户对某门课程的评分记录，未评过返回 null
     */
    public CourseRating selectRatingByCourseAndUser(@Param("courseId") Long courseId, @Param("userId") Long userId);

    /**
     * 同 selectRatingByCourseAndUser，但加锁读（for update）。
     * 改分路径专用：锁住自己的评分行，保证「读旧分 → 算差值 → 改计数」不被并发改分插队
     */
    public CourseRating selectRatingForUpdate(@Param("courseId") Long courseId, @Param("userId") Long userId);

    /**
     * 课程是否存在且可评分（未逻辑删除 + 状态正常），返回 0/1
     */
    public int existsCourse(@Param("courseId") Long courseId);

    /**
     * 某课程的评分人数与总分（count(*) / sum(score)），是接口统计的唯一真相
     */
    public Map<String, Object> selectCourseStats(@Param("courseId") Long courseId);

    /**
     * 原子调整课程表的两列评分冗余统计：
     * 新增评分传 (countDelta=1, scoreDelta=score)，改分传 (countDelta=0, scoreDelta=新分-旧分)。
     * 返回 0 行 = 课程已被删，调用方必须报错回滚
     */
    public int updateCourseRatingStats(@Param("courseId") Long courseId, @Param("countDelta") int countDelta,
            @Param("scoreDelta") int scoreDelta);
}
