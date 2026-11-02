package com.ruoyi.user.service;

import java.util.Map;

/**
 * 学习数据 服务层（设计稿 20_学习数据）
 *
 * 两部分：
 *   1. report —— 客户端播放时上报本次学习秒数，按天累加；课程小节顺带记进度；
 *   2. stats  —— 今日学习分钟、连续学习天数、本周 7 天柱状图、已完成课程 / 笔记 / 待办。
 *
 * 日期与"本周"一律按 Asia/Shanghai 计算（服务器可能是 UTC，凌晨会算错天）。
 *
 * @author edu
 */
public interface IStudyService
{
    /**
     * 上报学习时长
     *
     * @param userId     用户ID（从 Token 上下文取）
     * @param targetType courseVideo 课程小节 / shortVideo 短视频
     * @param videoId    小节ID 或 短视频ID
     * @param seconds    本次新增学习秒数（1~3600）
     * @param finished   是否学完（仅课程小节有效：0 否 / 1 是；短视频忽略）
     * @param duration   视频总时长（秒，可选）：课程小节时长为空时补填一次，已有值不覆盖
     * @return {todayMinutes: 今日累计分钟, todaySeconds: 今日累计秒数}
     */
    public Map<String, Object> report(Long userId, String targetType, Long videoId, Integer seconds,
                                      Integer finished, Integer duration);

    /**
     * 学习数据总览
     *
     * @return {todayMinutes, continuousDays, weekMinutes:[{date, weekday, minutes} × 7],
     *          completedCourses, noteCount, todoCount}
     */
    public Map<String, Object> stats(Long userId);
}
