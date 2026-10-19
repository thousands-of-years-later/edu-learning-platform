package com.ruoyi.user.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.user.domain.PointsAccount;

/**
 * 积分账户 数据层
 *
 * 余额变动只有一条语句（upsert + 原子加），不做「先查再算再写」：
 * 后者在并发签到 / 兑换时会互相覆盖余额。
 *
 * @author edu
 */
public interface PointsAccountMapper
{
    /**
     * 查账户，从未有过积分记录的用户返回 null（调用方按余额 0 处理）
     */
    public PointsAccount selectByUser(@Param("userId") Long userId);

    /**
     * 只查余额（不存在返回 null）
     */
    public Integer selectBalance(@Param("userId") Long userId);

    /**
     * 加积分：账户不存在就建（懒创建），存在就原子累加余额与累计获得
     *
     * 一条 insert ... on duplicate key update 完成，并发安全。
     *
     * @param userId 用户ID
     * @param points 本次获得（>= 1）
     * @return 影响行数（插入 1 / 更新 2，调用方不用管）
     */
    public int increasePoints(@Param("userId") Long userId, @Param("points") int points);

    /**
     * 账户不存在时建一条余额 0 的记录（查询类接口用，保证后续读到的不是 null）
     */
    public int insertAccountIfAbsent(@Param("userId") Long userId);
}
