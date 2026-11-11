package com.ruoyi.course.mapper;

import java.util.List;
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
     * 某门课程的评价列表（分页由控制器 startPage() 驱动，按首次评分时间倒序）
     *
     * 返回评分行 + 评分人昵称/头像（标量子查询取，不 join，避免分页排序撞同名列）；
     * content 可能为空（只打星没写字）。
     */
    public List<CourseRating> selectRatingList(@Param("courseId") Long courseId);

    /**
     * 只改文字评价（不动分数）：传 null / 空串 = 清空文字
     *
     * 与改分分开一个方法：老的 /api/rating/update 只传分数，
     * 若合并成一句 update 会把用户原有文字清掉。
     */
    public int updateContentByCourseAndUser(@Param("courseId") Long courseId,
                                            @Param("userId") Long userId,
                                            @Param("content") String content);

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

    /**
     * 删除某几门课程下的全部评分（级联清理，硬删除）
     *
     * 评分表没有 del_flag：取消评分本就是删行，与 learning_interaction 同一语义。
     * 课程被逻辑删除时这些评分必须一起清掉，否则会留下「课程已删、评分还在」的孤儿数据，
     * 也会让 learning_course_rating_fix_count.sql 把已删课程的评分重新算回冗余列。
     */
    public int deleteRatingByCourseIds(Long[] courseIds);

    /** 按ID查一条评分（后台删除前取课程与分数，用于修正课程统计） */
    public CourseRating selectRatingById(@Param("ratingId") Long ratingId);

    /** 后台删除：物理删除一条评分（评分表没有 del_flag） */
    public int deleteRatingById(@Param("ratingId") Long ratingId);
}
