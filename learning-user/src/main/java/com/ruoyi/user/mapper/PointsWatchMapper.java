package com.ruoyi.user.mapper;

import java.sql.Date;
import org.apache.ibatis.annotations.Param;

/**
 * 积分-内容观看累计 数据层
 *
 * 一条记录 = 某用户某天在某条内容（课程小节 / 帖子）上的累计观看秒数与发分状态；
 * (user_id, target_type, target_id, award_date) 唯一，靠数据库保证"每天每条内容只发一次"。
 *
 * @author edu
 */
public interface PointsWatchMapper
{
    /**
     * 累加当天观看秒数；首次上报时插入一行
     *
     * @param seconds 本次新增秒数
     * @return 影响行数（插入 1 / 累加 2，MySQL 的 ON DUPLICATE KEY 语义）
     */
    public int upsertSeconds(@Param("userId") Long userId,
                             @Param("targetType") String targetType,
                             @Param("targetId") Long targetId,
                             @Param("awardDate") Date awardDate,
                             @Param("seconds") int seconds);

    /**
     * 查当天已累计的观看秒数（没有记录返回 0）
     */
    public int selectWatchedSeconds(@Param("userId") Long userId,
                                    @Param("targetType") String targetType,
                                    @Param("targetId") Long targetId,
                                    @Param("awardDate") Date awardDate);

    /**
     * 抢占发分资格：只有"未发分且已看满 minSeconds"才把 awarded 置 1
     *
     * 并发下只有一条 update 能成功，返回 1 的一方才真正加积分。
     *
     * @return 1 = 本次抢到，可以发分；0 = 已发过或时长不够
     */
    public int claimAward(@Param("userId") Long userId,
                          @Param("targetType") String targetType,
                          @Param("targetId") Long targetId,
                          @Param("awardDate") Date awardDate,
                          @Param("minSeconds") int minSeconds,
                          @Param("points") int points);

    /**
     * 帖子是否存在且已发布（防刷：不接受随便编的 targetId）
     */
    public int countPublishedPost(@Param("postId") Long postId);

    /**
     * 课程小节是否存在（防刷：不接受随便编的 videoId）
     */
    public int countCourseVideo(@Param("videoId") Long videoId);
}
