package com.ruoyi.course.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.course.domain.CourseRating;

/**
 * 课程评分 服务层
 *
 * @author edu
 */
public interface ICourseRatingService
{
    /**
     * 新增评分；已评过则业务报错「您已评分，请使用修改」（不做覆盖）
     *
     * @param userId   当前登录用户ID（只从登录态/Token 上下文取）
     * @param courseId 课程ID
     * @param score    评分（1-5 的整数）
     * @return {myScore: 本次评分, avgScore: 课程平均分, ratingCount: 评分人数}
     */
    public Map<String, Object> addRating(Long userId, Long courseId, Integer score);

    /**
     * 新增评分（带文字评价）
     *
     * @param content 文字评价（可空；≤500 字，空串按 null 存）
     */
    public Map<String, Object> addRating(Long userId, Long courseId, Integer score, String content);

    /**
     * 修改评分；未评过则业务报错「您还未评分，请先评分」
     *
     * 只改分数，不动已有文字（老接口 /api/rating/update 走这里）
     *
     * @return {myScore, avgScore, ratingCount}
     */
    public Map<String, Object> updateRating(Long userId, Long courseId, Integer score);

    /**
     * 修改评分 + 文字评价（文字整体覆盖：传空 = 清空文字）
     */
    public Map<String, Object> updateRating(Long userId, Long courseId, Integer score, String content);

    /**
     * 某门课程的评价列表（分页由控制器 startPage() 驱动）
     *
     * @param courseId 课程ID
     * @return 评分行（含 content、评分人昵称/头像）
     */
    public List<CourseRating> selectRatingList(Long courseId);

    /**
     * 校验课程可评价/可查询（未删除 + 状态正常）
     *
     * 控制器必须在使用 startPage() 之前调用，避免分页参数残留。
     */
    public void checkCourse(Long courseId);

    /**
     * 查询：当前用户对课程的评分 + 课程平均分 + 评分人数
     *
     * @return {myScore: 我的评分（未评为 null）, avgScore: 平均分（无人评分为 null）, ratingCount: 评分人数}
     */
    public Map<String, Object> getRatingInfo(Long userId, Long courseId);

    // ==================== 后台评价管理 ====================

    /**
     * 后台评价列表（分页由控制器 startPage() 驱动）
     *
     * @param query 过滤条件：courseId / score / content / userId
     */
    public List<CourseRating> selectAdminList(CourseRating query);

    /**
     * 后台删除评价（物理删除，评分表没有 del_flag）
     *
     * 每删一条同步修正课程的 rating_count / rating_score，
     * 否则课程详情的平均分与人数会和实际对不上。
     *
     * @param ids 逗号分隔的评分ID
     */
    public int deleteRatingByIds(String ids);
}
