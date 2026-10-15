package com.ruoyi.course.service;

import java.util.Map;

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
     * 修改评分；未评过则业务报错「您还未评分，请先评分」
     *
     * @return {myScore, avgScore, ratingCount}
     */
    public Map<String, Object> updateRating(Long userId, Long courseId, Integer score);

    /**
     * 查询：当前用户对课程的评分 + 课程平均分 + 评分人数
     *
     * @return {myScore: 我的评分（未评为 null）, avgScore: 平均分（无人评分为 null）, ratingCount: 评分人数}
     */
    public Map<String, Object> getRatingInfo(Long userId, Long courseId);
}
