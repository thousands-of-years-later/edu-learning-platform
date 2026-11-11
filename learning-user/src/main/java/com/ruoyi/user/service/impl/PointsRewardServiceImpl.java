package com.ruoyi.user.service.impl;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.user.domain.PointsSource;
import com.ruoyi.user.mapper.PointsAdMapper;
import com.ruoyi.user.mapper.PointsLogMapper;
import com.ruoyi.user.mapper.PointsWatchMapper;
import com.ruoyi.user.service.IPointsRewardService;
import com.ruoyi.user.service.IPointsService;

/**
 * 积分发放 服务层实现
 *
 * 关键点：
 *   1) "每条内容每天只发一次"靠 learning_points_watch 的唯一索引 + claimAward 的条件更新，
 *      并发时只有一个请求能把 awarded 从 0 改成 1；
 *   2) "每天 50 分"直接 sum 当天流水（watchCourse + watchPost），不另存计数器；
 *   3) 真正加余额、写流水复用 IPointsService.awardPoints，保证积分账户与流水一致。
 *
 * @author edu
 */
@Service
public class PointsRewardServiceImpl implements IPointsRewardService
{
    /** 业务时区：日期按东八区算 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 课程/帖子两类内容来源 */
    private static final List<String> CONTENT_TYPES = Arrays.asList(
            PointsSource.WATCH_COURSE, PointsSource.WATCH_POST);

    /** 单次上报秒数上限（1 小时），防止客户端异常传超大值 */
    private static final int MAX_SECONDS = 3600;

    @Autowired
    private PointsWatchMapper pointsWatchMapper;

    @Autowired
    private PointsAdMapper pointsAdMapper;

    @Autowired
    private PointsLogMapper pointsLogMapper;

    @Autowired
    private IPointsService pointsService;

    @Override
    @Transactional
    public Map<String, Object> watch(Long userId, String targetType, Long targetId, Integer seconds)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
        String type = StringUtils.trim(targetType);
        if (!"post".equals(type) && !"courseVideo".equals(type))
        {
            throw new ServiceException("内容类型不合法（只支持 post / courseVideo）");
        }
        if (targetId == null)
        {
            throw new ServiceException("内容ID不能为空");
        }
        if (seconds == null || seconds <= 0 || seconds > MAX_SECONDS)
        {
            throw new ServiceException("本次观看秒数不合法（1~" + MAX_SECONDS + " 秒）");
        }

        // 内容必须真实存在：否则客户端随便传一个 ID 就能刷分
        if ("post".equals(type) && pointsWatchMapper.countPublishedPost(targetId) == 0)
        {
            throw new ServiceException("帖子不存在或未发布");
        }
        if ("courseVideo".equals(type) && pointsWatchMapper.countCourseVideo(targetId) == 0)
        {
            throw new ServiceException("课程小节不存在或已删除");
        }

        String source = "post".equals(type) ? PointsSource.WATCH_POST : PointsSource.WATCH_COURSE;
        LocalDate today = LocalDate.now(ZONE);
        Date awardDate = Date.valueOf(today);
        Date begin = Date.valueOf(today);
        Date end = Date.valueOf(today.plusDays(1));

        int todayPoints = pointsLogMapper.sumPointsByTypesBetween(userId, CONTENT_TYPES, begin, end);
        int awardedPoints = 0;
        int watchedSeconds = 0;

        // 每天 50 分封顶：已经满了就不再累计，直接返回 0（前端不用提示）
        if (todayPoints + PointsSource.CONTENT_POINTS <= PointsSource.CONTENT_DAILY_CAP)
        {
            pointsWatchMapper.upsertSeconds(userId, type, targetId, awardDate, seconds);
            watchedSeconds = pointsWatchMapper.selectWatchedSeconds(userId, type, targetId, awardDate);

            // 看满 30 秒才有资格；claimAward 并发安全，只有一方能拿到 1
            if (watchedSeconds >= PointsSource.CONTENT_MIN_SECONDS
                    && pointsWatchMapper.claimAward(userId, type, targetId, awardDate,
                            PointsSource.CONTENT_MIN_SECONDS, PointsSource.CONTENT_POINTS) == 1)
            {
                pointsService.awardPoints(userId, source, PointsSource.CONTENT_POINTS, targetId,
                        "观看" + ("post".equals(type) ? "帖子" : "课程小节") + "满 30 秒");
                awardedPoints = PointsSource.CONTENT_POINTS;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("awardedPoints", awardedPoints);
        result.put("todayContentPoints", todayPoints + awardedPoints);
        result.put("todayContentLimit", PointsSource.CONTENT_DAILY_CAP);
        result.put("watchedSeconds", watchedSeconds);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> ad(Long userId, String adId, Integer seconds)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
        String ad = StringUtils.trim(adId);
        if (StringUtils.isEmpty(ad) || ad.length() > 64)
        {
            throw new ServiceException("广告标识不能为空且不能超过 64 个字符");
        }
        if (seconds == null || seconds < PointsSource.AD_MIN_SECONDS || seconds > MAX_SECONDS)
        {
            throw new ServiceException("广告观看秒数不合法（至少 " + PointsSource.AD_MIN_SECONDS + " 秒）");
        }

        LocalDate today = LocalDate.now(ZONE);
        Date viewDate = Date.valueOf(today);
        int todayAdCount = pointsAdMapper.countByUserAndDate(userId, viewDate);
        int awardedPoints = 0;

        if (todayAdCount < PointsSource.AD_DAILY_LIMIT)
        {
            // 同一条广告当天只记一次；重复插入返回 0
            if (pointsAdMapper.insertViewIfAbsent(userId, ad, viewDate, seconds,
                    PointsSource.AD_POINTS) == 1)
            {
                pointsService.awardPoints(userId, PointsSource.WATCH_AD, PointsSource.AD_POINTS,
                        null, "观看广告奖励");
                awardedPoints = PointsSource.AD_POINTS;
                todayAdCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("awardedPoints", awardedPoints);
        result.put("todayAdCount", todayAdCount);
        result.put("todayAdLimit", PointsSource.AD_DAILY_LIMIT);
        return result;
    }
}
