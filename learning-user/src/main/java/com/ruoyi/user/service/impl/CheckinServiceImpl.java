package com.ruoyi.user.service.impl;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.user.domain.CheckinRecord;
import com.ruoyi.user.domain.CheckinRule;
import com.ruoyi.user.domain.PointsLog;
import com.ruoyi.user.mapper.CheckinMapper;
import com.ruoyi.user.mapper.PointsAccountMapper;
import com.ruoyi.user.mapper.PointsLogMapper;
import com.ruoyi.user.service.ICheckinService;

/**
 * 签到 / 积分 服务层处理
 *
 * 几条贯穿全类的规矩：
 *   1) 「今天」只按【东八区自然日】算，且绝不信客户端传上来的日期（否则改天就能多签）；
 *   2) 连续天数不遍历历史：只看最近一条记录是不是昨天，是则 +1，否则从 1 重新开始
 *      —— 断签重置天然成立，也不用给断掉的日子补行；
 *   3) 一天只能签一次由【唯一索引】兜底，不靠先查后插（并发下会签两次）；
 *   4) 签到、加积分、写流水在同一个事务里，任何一步失败都不会留下半截数据。
 *
 * @author edu
 */
@Service
public class CheckinServiceImpl implements ICheckinService
{
    /** 签到按东八区自然日算：显式指定时区，不依赖服务器 / JVM 默认时区 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private CheckinMapper checkinMapper;

    @Autowired
    private PointsAccountMapper pointsAccountMapper;

    @Autowired
    private PointsLogMapper pointsLogMapper;

    @Override
    @Transactional
    public Map<String, Object> sign(Long userId)
    {
        checkUser(userId);
        String today = today();

        // 连续天数：昨天签过 = 昨天那行的连续天数 + 1；否则断签，从 1 重新开始
        CheckinRecord last = checkinMapper.selectLastByUser(userId);
        int continuousDays = 1;
        if (last != null && yesterday(today).equals(last.getSignDate()))
        {
            continuousDays = last.getContinuousDays() + 1;
        }
        int points = CheckinRule.pointsOf(continuousDays);

        CheckinRecord record = new CheckinRecord();
        record.setUserId(userId);
        record.setSignDate(today);
        record.setPoints(points);
        record.setContinuousDays(continuousDays);
        try
        {
            checkinMapper.insertRecord(record);
        }
        catch (DuplicateKeyException e)
        {
            // 唯一索引 (user_id, sign_date) 兜底：并发双击时只有一次能插入成功，
            // 另一条走到这里直接报错，整笔事务回滚（不会只加了积分没写签到记录）
            throw new ServiceException("今天已经签到过了");
        }

        // 加积分：一条 upsert 完成「账户懒创建 + 原子累加」
        pointsAccountMapper.increasePoints(userId, points);
        Integer balance = pointsAccountMapper.selectBalance(userId);

        // 积分流水（只增不改），ref_id 指向本次签到记录，方便以后对账
        PointsLog log = new PointsLog();
        log.setUserId(userId);
        log.setChangeAmount(points);
        log.setBalance(balance == null ? points : balance);
        log.setType(PointsLog.TYPE_CHECKIN);
        log.setRefId(record.getCheckinId());
        log.setRemark("每日签到（连续第" + continuousDays + "天）");
        pointsLogMapper.insertLog(log);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("points", points);
        result.put("continuousDays", continuousDays);
        result.put("totalDays", checkinMapper.countByUser(userId));
        result.put("balance", balance == null ? points : balance);
        result.put("signDate", today);
        return result;
    }

    @Override
    public Map<String, Object> status(Long userId)
    {
        checkUser(userId);
        String today = today();

        CheckinRecord last = checkinMapper.selectLastByUser(userId);
        boolean signedToday = last != null && today.equals(last.getSignDate());

        // 连续天数口径（与设计稿一致：还没签的这一天不算进连续天数）
        //   今天已签   → 今天那行的连续天数
        //   昨天签过   → 昨天那行的连续天数（今天签了就 +1）
        //   更早断签   → 0，今天签了从 1 重新开始
        int continuousDays = 0;
        if (signedToday)
        {
            continuousDays = last.getContinuousDays();
        }
        else if (last != null && yesterday(today).equals(last.getSignDate()))
        {
            continuousDays = last.getContinuousDays();
        }

        // 今天还没签时给出「签完能得几分」，前端拼「签到 +18 积分」
        int todayPoints = signedToday ? 0 : CheckinRule.pointsOf(continuousDays + 1);

        Integer balance = pointsAccountMapper.selectBalance(userId);
        if (balance == null)
        {
            // 老用户从没签过：补一条余额 0 的账户，避免前端拿到 null
            pointsAccountMapper.insertAccountIfAbsent(userId);
            balance = 0;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signedToday", signedToday);
        result.put("todayPoints", todayPoints);
        result.put("continuousDays", continuousDays);
        result.put("totalDays", checkinMapper.countByUser(userId));
        result.put("balance", balance);
        result.put("today", today);
        return result;
    }

    @Override
    public Map<String, Object> calendar(Long userId, Integer year, Integer month)
    {
        checkUser(userId);
        LocalDate now = LocalDate.now(ZONE);
        int y = year == null ? now.getYear() : year;
        int m = month == null ? now.getMonthValue() : month;
        if (m < 1 || m > 12)
        {
            throw new ServiceException("月份不合法");
        }
        if (y < 2000 || y > 2100)
        {
            throw new ServiceException("年份不合法");
        }

        YearMonth ym = YearMonth.of(y, m);
        List<String> dates = checkinMapper.selectSignDatesInRange(userId, ym.atDay(1).toString(),
                ym.atEndOfMonth().toString());

        // 月历只需要「几号」，把 yyyy-MM-dd 的日部分取出来给前端画点
        List<Integer> days = new ArrayList<>();
        for (String date : dates)
        {
            days.add(Integer.valueOf(date.substring(8, 10)));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", y);
        result.put("month", m);
        result.put("days", days);
        result.put("today", now.toString());
        result.put("signedToday", checkinMapper.existsByUserAndDate(userId, now.toString()) > 0);
        return result;
    }

    @Override
    public List<CheckinRecord> selectRecords(Long userId)
    {
        checkUser(userId);
        return checkinMapper.selectRecordsByUser(userId);
    }

    @Override
    public Map<String, Object> rules()
    {
        Map<String, Object> result = new HashMap<>();
        result.put("tiers", CheckinRule.tiers());
        result.put("loopDays", CheckinRule.LOOP_DAYS);
        result.put("dailyLimit", 1);
        result.put("dailyTip", "每天可签到一次");
        result.put("resetNote", "中断一天后连续天数重新从 1 开始");
        result.put("loopNote", "连续 " + CheckinRule.LOOP_DAYS + " 天后从第 1 档重新循环");
        return result;
    }

    private void checkUser(Long userId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
    }

    /** 今天（东八区）yyyy-MM-dd */
    private String today()
    {
        return LocalDate.now(ZONE).toString();
    }

    /** 昨天的 yyyy-MM-dd */
    private String yesterday(String today)
    {
        return LocalDate.parse(today).minusDays(1).toString();
    }
}
