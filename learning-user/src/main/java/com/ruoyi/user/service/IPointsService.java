package com.ruoyi.user.service;

import java.util.List;
import java.util.Map;

/**
 * 积分 / 权益 服务层
 *
 * 与签到共用同一张积分账户（learning_points_account）：签到负责加，这里负责减。
 * 免广告权益也挂在账户行上（ad_free_expire_time），因为「扣积分 + 延长权益」必须原子完成，
 * 拆成两张表就要跨表多条语句，容易出并发漏洞。
 *
 * userId 一律由调用方从 API 上下文带入，不接受前端传入。
 *
 * @author edu
 */
public interface IPointsService
{
    /**
     * 免广告兑换档位（10 积分/30 分钟、30 积分/2 小时、100 积分/24 小时）
     */
    public List<Map<String, Object>> exchangeTiers();

    /**
     * 兑换免广告
     *
     * 同一事务内：一条 UPDATE 完成「余额够才扣 + 权益叠加延长」→ 写积分流水。
     * 余额不足会抛业务异常，余额与权益都不会变。
     *
     * @param userId 用户ID
     * @param tier   档位（1/2/3）
     * @return {balance, adFreeExpireTime, adFreeRemainMinutes, adFree, label, cost}
     */
    public Map<String, Object> exchange(Long userId, Integer tier);

    /**
     * 我的积分账户（任务中心"当前无免广告权益"那块 + 余额）
     *
     * @param userId 用户ID
     * @return {balance, adFree, adFreeExpireTime, adFreeRemainMinutes}
     *         adFreeRemainMinutes 是服务端算好的剩余分钟（向上取整），前端直接显示，
     *         不要用手机本地时间减 adFreeExpireTime（手机时间可以随便改）
     */
    public Map<String, Object> account(Long userId);
}
