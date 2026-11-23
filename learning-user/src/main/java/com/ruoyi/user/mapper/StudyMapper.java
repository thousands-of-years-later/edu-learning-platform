package com.ruoyi.user.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    /**
     * 累加「帖子阅读」学习时长：同时计入当天总时长与文章时长。
     * 与课程视频分开记 article_seconds，便于区分学习内容结构。
     */
    public int addArticleSeconds(@Param("userId") Long userId,
                                 @Param("studyDate") LocalDate studyDate,
                                 @Param("seconds") int seconds);

    /** 某天的学习秒数；没有记录返回 0 */
    public int selectDaySeconds(@Param("userId") Long userId, @Param("studyDate") LocalDate studyDate);

    /** 某天的文章阅读秒数；没有记录返回 0 */
    public int selectDayArticleSeconds(@Param("userId") Long userId, @Param("studyDate") LocalDate studyDate);

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

    /**
     * 小节时长为空时补填一次（客户端播放上报会带视频总时长）
     *
     * where 带 duration is null：管理员在后台填过的值不会被客户端覆盖。
     *
     * @return 影响行数；0 = 已经有值 / 小节不存在
     */
    public int fillVideoDurationIfEmpty(@Param("videoId") Long videoId, @Param("duration") int duration);

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

    /** 区间内文章阅读秒数（本周文章分钟） */
    public int sumArticleSecondsBetween(@Param("userId") Long userId,
                                        @Param("start") LocalDate start,
                                        @Param("end") LocalDate end);

    /** 累计课程秒数（总秒数减去文章阅读秒数） */
    public int sumCourseSeconds(@Param("userId") Long userId);

    /**
     * 在学课程数：有学习进度，但课程下还有未完成的小节。
     */
    public int countLearningCourses(@Param("userId") Long userId);

    /**
     * 区间内阅读满 30 秒的帖子数（learning_points_watch）。
     */
    public int countReadPostsBetween(@Param("userId") Long userId,
                                     @Param("start") LocalDate start,
                                     @Param("end") LocalDate end);

    /**
     * 我的课程列表（在学 / 已完成），带小节完成数与最近学习小节。
     *
     * @param status learning=在学（有进度但未全部完成）；completed=已完成
     */
    public List<Map<String, Object>> selectLearningCourses(@Param("userId") Long userId,
                                                           @Param("status") String status);
}
