package com.ruoyi.user.mapper;

import java.sql.Date;
import org.apache.ibatis.annotations.Param;

/**
 * 积分-广告观看 数据层
 *
 * 一条记录 = 某用户某天看完某条广告并领到了积分；
 * (user_id, ad_id, view_date) 唯一，重复插入直接忽略，保证同一条广告当天只发一次。
 *
 * @author edu
 */
public interface PointsAdMapper
{
    /**
     * 记录一条广告观看（重复则忽略）
     *
     * @return 1 = 首次记录，可以发分；0 = 今天已经领过这条广告
     */
    public int insertViewIfAbsent(@Param("userId") Long userId,
                                  @Param("adId") String adId,
                                  @Param("viewDate") Date viewDate,
                                  @Param("seconds") int seconds,
                                  @Param("points") int points);

    /**
     * 某用户某天已领过几条广告积分
     */
    public int countByUserAndDate(@Param("userId") Long userId,
                                  @Param("viewDate") Date viewDate);
}
