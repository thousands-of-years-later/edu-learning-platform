package com.ruoyi.user.mapper;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.user.domain.StudyRecord;

/**
 * 学习数据 数据层（学习时长按天 + 课程小节进度）
 *
 * 本模块只依赖 ruoyi-common，课程/小节/笔记这些表的统计直接写纯 SQL 查，
 * 不反向依赖 learning-course / learning-interation（与 UserHomeMapper 同一做法）。
 *
 * @author edu
 */
public interface StudyMapper
{
    /**
     * 累加某天的学习秒数（没有当天记录就插入）
     *
     * on duplicate key update：并发上报也只是相加，不会插出第二条
     */
    public int addSeconds(@Param("userId") Long userId,
                          @Param("studyDate") LocalDate studyDate,
                          @Param("seconds") int seconds);

    /** 某天的学习秒数；没有记录返回 0 */
    public int selectDaySeconds(@Param("userId") Long userId, @Param("studyDate") LocalDate studyDate);

    /**
     * 某个区间内每天的学习秒数（本周柱状图用）
     */
    public List<StudyRecord> selectBetween(@Param("userId") Long userId,
                                          @Param("start") LocalDate start,
                                          @Param("end") LocalDate end);

    /**
     * 从 fromDate 起有学习记录的日期（倒序），用于算连续学习天数
     */
    public List<LocalDate> selectStudyDates(@Param("userId") Long userId, @Param("fromDate") LocalDate fromDate);

    /**
     * 课程小节的进度累加（没有就插入）
     *
     * finished 用 greatest(旧值, 新值)：已经学完的不会被后续上报改回未完成；
     * watched_seconds 累加；并发上报同样不会插重复（唯一索引 user_id + video_id）。
     */
    public int upsertProgress(@Param("userId") Long userId,
                              @Param("courseId") Long courseId,
                              @Param("videoId") Long videoId,
                              @Param("seconds") int seconds,
                              @Param("finished") int finished);

    /** 小节所属课程（顺带校验小节存在且未删除）；不存在返回 null */
    public Long selectVideoCourseId(@Param("videoId") Long videoId);

    /** 短视频是否存在且可看（短视频观看也计入学习时长，但不记小节进度） */
    public int countShortVideo(@Param("videoId") Long videoId);

    /**
     * 已完成课程数：该课程下所有未删除的小节都 finished=1（至少有一个小节）
     */
    public int countCompletedCourses(@Param("userId") Long userId);

    /**
     * 待办数：已经开始看但还没学完的小节数
     */
    public int countTodoVideos(@Param("userId") Long userId);

    /** 我的笔记数（未删除） */
    public int countNotes(@Param("userId") Long userId);
}
