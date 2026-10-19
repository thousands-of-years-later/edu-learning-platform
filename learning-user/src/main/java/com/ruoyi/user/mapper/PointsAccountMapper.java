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

    /**
     * 兑换免广告：一条 UPDATE 同时完成「扣积分」与「叠加延长权益」
     *
     * where 里的 balance >= cost 是防超兑的闸门：余额不够就影响 0 行，调用方据此报「积分不足」，
     * 绝不会扣成负数；并发双击时数据库行锁会让第二条看到扣完后的余额，同样影响 0 行。
     * set 里的 greatest(原到期时间, 现在) + 时长 就是叠加：没过期从原到期时间往后加，
     * 已过期或从未有过则从此刻开始算。
     *
     * @param userId  用户ID
     * @param cost    消耗积分
     * @param minutes 本次增加的免广告时长（分钟）
     * @return 影响行数（1 = 成功，0 = 余额不足或账户不存在）
     */
    public int exchangeAdFree(@Param("userId") Long userId, @Param("cost") int cost, @Param("minutes") int minutes);
}
