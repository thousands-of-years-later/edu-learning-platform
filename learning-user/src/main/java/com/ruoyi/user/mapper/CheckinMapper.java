package com.ruoyi.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.user.domain.CheckinRecord;

/**
 * 签到记录 数据层
 *
 * 「一天只能签一次」不靠查询判断，靠唯一索引 (user_id, sign_date)：
 * 服务层直接 insert，撞唯一键就说明今天签过了，并发下也不会出现两条。
 *
 * @author edu
 */
public interface CheckinMapper
{
    /**
     * 插入一条签到记录（checkinId 由 useGeneratedKeys 回填）
     *
     * 撞唯一索引 uk_checkin_user_date 会抛 DuplicateKeyException，
     * 服务层翻译成「今天已经签到过了」。
     *
     * @param record 签到记录（userId / signDate / points / continuousDays 由服务层算好）
     * @return 影响行数
     */
    public int insertRecord(CheckinRecord record);

    /**
     * 查最近一次签到（连续天数的唯一依据）
     *
     * 走 uk_checkin_user_date 的反向扫描（user_id, sign_date），不需要额外索引。
     *
     * @param userId 用户ID
     * @return 最近一条签到记录，没签过返回 null
     */
    public CheckinRecord selectLastByUser(@Param("userId") Long userId);

    /**
     * 我的签到记录列表（分页由上层 PageHelper 驱动，按日期倒序）
     */
    public List<CheckinRecord> selectRecordsByUser(@Param("userId") Long userId);

    /**
     * 某个日期区间内的签到日期（月历用：后端只回日期，前端自己画点）
     *
     * @param userId 用户ID
     * @param begin  开始日期 yyyy-MM-dd（含）
     * @param end    结束日期 yyyy-MM-dd（含）
     * @return 日期字符串列表，升序
     */
    public List<String> selectSignDatesInRange(@Param("userId") Long userId,
                                               @Param("begin") String begin,
                                               @Param("end") String end);

    /**
     * 累计签到天数（走唯一索引 user_id 前缀）
     */
    public int countByUser(@Param("userId") Long userId);

    /**
     * 某天是否已签到（0 / 1）
     */
    public int existsByUserAndDate(@Param("userId") Long userId, @Param("signDate") String signDate);
}
