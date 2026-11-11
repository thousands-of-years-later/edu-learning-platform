package com.ruoyi.user.service;

import java.util.Map;

/**
 * 积分发放 服务层（观看课程/帖子、看广告）
 *
 * 规则（2026-11）：
 *   · 课程小节、帖子：每条内容每天只发一次，每次 5 分；累计观看满 30 秒才结算；
 *     两类合计每天最多 50 分。
 *   · 广告：完整看完一条 10 分，每天最多 3 条，不占 50 分额度。
 *
 * 去重、上限、加余额、写流水都在这里收口；签到仍走 ICheckinService（规则不同）。
 *
 * @author edu
 */
public interface IPointsRewardService
{
    /**
     * 上报观看时长并尝试发分（课程小节 / 帖子）
     *
     * @param userId     用户ID（从 Token 取，不接受前端传）
     * @param targetType post（帖子）/ courseVideo（课程小节）
     * @param targetId   帖子ID / 课程小节ID
     * @param seconds    本次新增观看秒数（1~3600）
     * @return {awardedPoints, todayContentPoints, todayContentLimit, watchedSeconds}
     *         awardedPoints=0 表示本次没发（未满 30 秒 / 今天这条已发过 / 今天 50 分已满）
     */
    public Map<String, Object> watch(Long userId, String targetType, Long targetId, Integer seconds);

    /**
     * 看完广告领积分
     *
     * @param userId  用户ID（从 Token 取）
     * @param adId    广告唯一标识（广告位/广告ID，最长 64）
     * @param seconds 本次观看秒数（至少 15 秒才算看完）
     * @return {awardedPoints, todayAdCount, todayAdLimit}
     */
    public Map<String, Object> ad(Long userId, String adId, Integer seconds);
}
