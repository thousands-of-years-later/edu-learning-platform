package com.ruoyi.user.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.user.domain.AdFreeRule;
import com.ruoyi.user.domain.PointsAccount;
import com.ruoyi.user.domain.PointsLog;
import com.ruoyi.user.mapper.PointsAccountMapper;
import com.ruoyi.user.mapper.PointsLogMapper;
import com.ruoyi.user.service.IPointsService;

/**
 * 积分 / 权益 服务层处理
 *
 * 兑换的关键只有一句 SQL：PointsAccountMapper.exchangeAdFree
 *   - where 里的 balance >= cost 是「防超兑」的闸门，余额不够影响 0 行；
 *   - set 里 greatest(原到期时间, 现在) + 时长 就是「叠加」。
 * 所以这里不做「先查余额再判断」——那种写法在并发下会超兑。
 *
 * @author edu
 */
@Service
public class PointsServiceImpl implements IPointsService
{
    /** 到期时间统一按这个格式给前端（Map 里的 Date 不受实体 @JsonFormat 影响） */
    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Autowired
    private PointsAccountMapper pointsAccountMapper;

    @Autowired
    private PointsLogMapper pointsLogMapper;

    @Override
    public List<Map<String, Object>> exchangeTiers()
    {
        return AdFreeRule.tiers();
    }

    @Override
    @Transactional
    public Map<String, Object> exchange(Long userId, Integer tier)
    {
        checkUser(userId);
        if (!AdFreeRule.isValidTier(tier))
        {
            throw new ServiceException("兑换档位不合法");
        }
        int cost = AdFreeRule.costOf(tier);
        int minutes = AdFreeRule.minutesOf(tier);
        String label = AdFreeRule.labelOf(minutes);

        // 一条 UPDATE 同时完成「扣分」和「叠加延长」：
        // 影响 0 行 = 余额不够（或账户不存在）→ 抛错，整笔事务回滚，余额和权益都不变
        if (pointsAccountMapper.exchangeAdFree(userId, cost, minutes) == 0)
        {
            throw new ServiceException("积分不足");
        }
        PointsAccount account = pointsAccountMapper.selectByUser(userId);

        // 积分流水：负数表示消耗；balance 存变动后的余额快照
        PointsLog log = new PointsLog();
        log.setUserId(userId);
        log.setChangeAmount(-cost);
        log.setBalance(account == null ? 0 : account.getBalance());
        log.setType(PointsLog.TYPE_EXCHANGE);
        log.setRemark("兑换免广告" + label);
        pointsLogMapper.insertLog(log);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cost", cost);
        result.put("label", label);
        result.put("balance", account == null ? 0 : account.getBalance());
        result.put("adFree", true);
        result.put("adFreeExpireTime", format(account == null ? null : account.getAdFreeExpireTime()));
        result.put("adFreeRemainMinutes", remainMinutes(account == null ? null : account.getAdFreeExpireTime()));
        return result;
    }

    @Override
    public Map<String, Object> account(Long userId)
    {
        checkUser(userId);
        PointsAccount account = pointsAccountMapper.selectByUser(userId);
        if (account == null)
        {
            // 老用户从没签到过：补一条余额 0 的账户，避免前端拿到 null
            pointsAccountMapper.insertAccountIfAbsent(userId);
            account = pointsAccountMapper.selectByUser(userId);
        }
        Date expireTime = account == null ? null : account.getAdFreeExpireTime();
        boolean adFree = expireTime != null && expireTime.after(new Date());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("balance", account == null ? 0 : account.getBalance());
        result.put("adFree", adFree);
        result.put("adFreeExpireTime", format(expireTime));
        // 服务端算好的剩余分钟：前端直接显示"剩余 X 分钟/小时"，
        // 不要用手机本地时间减到期时间（手机时间可以随便改）
        result.put("adFreeRemainMinutes", adFree ? remainMinutes(expireTime) : 0);
        return result;
    }

    private void checkUser(Long userId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
    }

    /** 到期时间格式化成字符串给前端（null 原样返回 null） */
    private String format(Date time)
    {
        return time == null ? null : new SimpleDateFormat(TIME_PATTERN).format(time);
    }

    /**
     * 权益剩余分钟数（向上取整）
     *
     * 向上取整是为了避免"还剩 30 秒却显示 0 分钟"的错位：剩 1 秒也返回 1，剩 30 分整返回 30。
     * 已过期 / 从未兑换返回 0。用服务端时间算，客户端不要自己拿手机时间减。
     */
    private int remainMinutes(Date expireTime)
    {
        if (expireTime == null)
        {
            return 0;
        }
        long millis = expireTime.getTime() - System.currentTimeMillis();
        if (millis <= 0)
        {
            return 0;
        }
        return (int) ((millis + 59_999) / 60_000);
    }
}
