package com.ruoyi.user.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.user.domain.CheckinRecord;

/**
 * 签到 / 积分 服务层
 *
 * userId 一律由调用方从 API 上下文带入，服务层不接受前端自报的用户ID。
 * 「今天」统一按东八区自然日，由本层计算（不信任客户端传日期，否则可以改天多签）。
 *
 * @author edu
 */
public interface ICheckinService
{
    /**
     * 每日签到
     *
     * 同一事务内做三件事：写签到记录 → 账户余额 +N → 写积分流水。
     * 今天已签过会抛业务异常（唯一索引兜底，并发双击也只有一次成功）。
     *
     * @param userId 用户ID
     * @return {points, continuousDays, totalDays, balance, signDate}
     */
    public Map<String, Object> sign(Long userId);

    /**
     * 签到状态（任务中心大按钮 + 签到记录页顶部统计用）
     *
     * @param userId 用户ID
     * @return {signedToday, todayPoints, continuousDays, totalDays, balance, today}
     *         todayPoints = 今天签到能得几分（已签则为 0，前端拼"签到 +18 积分"用）
     */
    public Map<String, Object> status(Long userId);

    /**
     * 某月的签到日历
     *
     * @param userId 用户ID
     * @param year   年份（空 = 今年）
     * @param month  月份（空 = 本月）
     * @return {year, month, days:[几号...], today, signedToday}
     */
    public Map<String, Object> calendar(Long userId, Integer year, Integer month);

    /**
     * 我的签到记录（最近签到列表，按日期倒序；分页由上层 PageHelper 驱动）
     */
    public List<CheckinRecord> selectRecords(Long userId);

    /**
     * 签到积分规则（给"积分规则"页渲染档位表）
     *
     * @return {tiers:[{day,points}...], loopDays, dailyLimit, resetNote, loopNote}
     */
    public Map<String, Object> rules();
}
