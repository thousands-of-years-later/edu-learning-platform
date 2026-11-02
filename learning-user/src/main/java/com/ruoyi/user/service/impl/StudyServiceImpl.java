package com.ruoyi.user.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.user.domain.StudyDay;
import com.ruoyi.user.domain.StudyRecord;
import com.ruoyi.user.mapper.StudyMapper;
import com.ruoyi.user.service.IStudyService;

/**
 * 学习数据 服务层处理
 *
 * 几条口径：
 *   1) 学习时长按天累加，客户端每次上报的是"本次新增秒数"，不是总时长；
 *   2) 单次上报 1~3600 秒：太小没意义、太大说明客户端异常（比如把整段时长一次报上来）；
 *   3) 课程小节的 finished 只增不减（SQL 里用 greatest），看完了不会因为再上报变回未完成；
 *   4) "连续学习天数"允许今天还没学：从今天往前数，今天没有就从昨天开始数，
 *      这样早上打开 App 不会看到"连续 0 天"；
 *   5) "待办"= 已经开始看但没学完的小节数；"已完成课程"= 整门课所有小节都学完。
 *
 * @author edu
 */
@Service
public class StudyServiceImpl implements IStudyService
{
    /** 业务时区：签到、学习日期、本周都按东八区算 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 单次上报秒数上限（1 小时），防止客户端异常把时长刷爆 */
    private static final int MAX_SECONDS_PER_REPORT = 3600;

    /** 视频总时长上限（24 小时），超出说明客户端传错了 */
    private static final int MAX_VIDEO_DURATION = 86400;

    /** 算连续学习天数时往前查多少天（够用且不至于扫全表） */
    private static final int STREAK_LOOKBACK_DAYS = 90;

    /** 柱状图标签：周一到周日 */
    private static final String[] WEEKDAY_LABELS = { "一", "二", "三", "四", "五", "六", "日" };

    /** 内容类型：课程小节 */
    private static final String TARGET_COURSE_VIDEO = "courseVideo";

    /** 内容类型：短视频 */
    private static final String TARGET_SHORT_VIDEO = "shortVideo";

    @Autowired
    private StudyMapper studyMapper;

    @Override
    @Transactional
    public Map<String, Object> report(Long userId, String targetType, Long videoId, Integer seconds,
                                      Integer finished, Integer duration)
    {
        checkUser(userId);
        String type = StringUtils.trim(targetType);
        if (!TARGET_COURSE_VIDEO.equals(type) && !TARGET_SHORT_VIDEO.equals(type))
        {
            throw new ServiceException("内容类型不合法（只支持 courseVideo / shortVideo）");
        }
        if (videoId == null)
        {
            throw new ServiceException("内容ID不能为空");
        }
        if (seconds == null || seconds <= 0 || seconds > MAX_SECONDS_PER_REPORT)
        {
            throw new ServiceException("单次上报学习时长不合法（1~" + MAX_SECONDS_PER_REPORT + " 秒）");
        }
        // 视频总时长：可选。不合法就不填，不影响本次时长上报（时长只是展示/算百分比用的）
        if (duration != null && (duration <= 0 || duration > MAX_VIDEO_DURATION))
        {
            throw new ServiceException("视频时长不合法（1~" + MAX_VIDEO_DURATION + " 秒）");
        }
        int done = (finished != null && finished > 0) ? 1 : 0;

        if (TARGET_COURSE_VIDEO.equals(type))
        {
            // 课程ID由服务端按小节反查，不信客户端传的课程ID（避免把 A 课的进度记到 B 课）
            Long courseId = studyMapper.selectVideoCourseId(videoId);
            if (courseId == null)
            {
                throw new ServiceException("课程小节不存在或已删除");
            }
            studyMapper.upsertProgress(userId, courseId, videoId, seconds, done);
            // 小节时长为空时补填一次（后台管理员填过就不覆盖）
            if (duration != null)
            {
                studyMapper.fillVideoDurationIfEmpty(videoId, duration);
            }
        }
        else if (studyMapper.countShortVideo(videoId) == 0)
        {
            throw new ServiceException("短视频不存在或已下架");
        }

        LocalDate today = LocalDate.now(ZONE);
        studyMapper.addSeconds(userId, today, seconds);

        Map<String, Object> result = new LinkedHashMap<>();
        int todaySeconds = studyMapper.selectDaySeconds(userId, today);
        result.put("todaySeconds", todaySeconds);
        result.put("todayMinutes", toMinutes(todaySeconds));
        return result;
    }

    @Override
    public Map<String, Object> stats(Long userId)
    {
        checkUser(userId);
        LocalDate today = LocalDate.now(ZONE);

        Map<String, Object> result = new LinkedHashMap<>();
        // 1) 今日学习
        int todaySeconds = studyMapper.selectDaySeconds(userId, today);
        result.put("todayMinutes", toMinutes(todaySeconds));

        // 2) 连续学习天数
        result.put("continuousDays", calcContinuousDays(userId, today));

        // 3) 本周 7 天柱状图（周一到周日，没学的补 0）
        result.put("weekMinutes", buildWeek(userId, today));

        // 4) 底部三个数字
        result.put("completedCourses", studyMapper.countCompletedCourses(userId));
        result.put("noteCount", studyMapper.countNotes(userId));
        result.put("todoCount", studyMapper.countTodoVideos(userId));
        return result;
    }

    /**
     * 连续学习天数
     *
     * 从"今天"往前数，今天没学就从昨天开始数（早上刚打开 App 时不会显示 0 天）；
     * 遇到第一个没有记录的日子就停。
     */
    private int calcContinuousDays(Long userId, LocalDate today)
    {
        LocalDate fromDate = today.minusDays(STREAK_LOOKBACK_DAYS);
        List<LocalDate> dates = studyMapper.selectStudyDates(userId, fromDate);
        if (dates == null || dates.isEmpty())
        {
            return 0;
        }
        java.util.Set<LocalDate> studied = new java.util.HashSet<>(dates);
        LocalDate cursor = studied.contains(today) ? today : today.minusDays(1);
        int days = 0;
        while (studied.contains(cursor))
        {
            days++;
            cursor = cursor.minusDays(1);
        }
        return days;
    }

    /**
     * 本周柱状图：周一 ~ 周日固定 7 条
     */
    private List<StudyDay> buildWeek(Long userId, LocalDate today)
    {
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        List<StudyRecord> records = studyMapper.selectBetween(userId, monday, sunday);

        Map<LocalDate, Integer> secondsByDate = new HashMap<>();
        if (records != null)
        {
            for (StudyRecord record : records)
            {
                secondsByDate.put(record.getStudyDate(), record.getSeconds() == null ? 0 : record.getSeconds());
            }
        }

        List<StudyDay> week = new ArrayList<>(7);
        for (int i = 0; i < 7; i++)
        {
            LocalDate date = monday.plusDays(i);
            int seconds = secondsByDate.getOrDefault(date, 0);
            week.add(new StudyDay(date.toString(), WEEKDAY_LABELS[i], toMinutes(seconds)));
        }
        return week;
    }

    /** 秒 -> 分钟（四舍五入；不足 1 分钟按 0 算） */
    private int toMinutes(int seconds)
    {
        return (int) Math.round(seconds / 60.0);
    }

    private void checkUser(Long userId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
    }
}
