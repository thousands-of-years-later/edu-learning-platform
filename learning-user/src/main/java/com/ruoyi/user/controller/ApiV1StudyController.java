package com.ruoyi.user.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.user.service.IStudyService;

/**
 * 学习数据 接口 v1（Token 认证，给小程序 / App 用）
 *
 * 对应设计稿 20_学习数据：
 *   今日学习（分钟）、连续学习（天）、本周学习时长（周一到周日）、
 *   已完成课程 / 笔记 / 待办。
 *
 * 数据来源：
 *   · 播放页每 30 秒（或暂停、退出时）调一次 /report 上报本次新增秒数；
 *   · 打开"学习数据"页调 /stats 一次拿全。
 *
 * 用户ID一律取 ApiContext，只能看/记自己的数据。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/learn")
public class ApiV1StudyController extends BaseController
{
    @Autowired
    private IStudyService studyService;

    /**
     * 上报学习时长（播放中周期调用）
     *
     * @param targetType courseVideo（课程小节）；短视频已不再接入学习数据
     * @param videoId    课程小节ID
     * @param seconds    本次新增学习秒数（1~3600）
     * @param finished   是否学完（仅课程小节有效：0/1，置 1 后不会回退）
     * @param duration   视频总时长（秒，可选）：课程小节时长为空时补填一次，已有值不覆盖
     * @return {todayMinutes, todaySeconds}
     */
    @PostMapping("/report")
    public AjaxResult report(String targetType, Long videoId, Integer seconds, Integer finished, Integer duration)
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.putAll(studyService.report(ApiContext.getUserId(), targetType, videoId, seconds, finished, duration));
        return ajax;
    }

    /**
     * 上报学习时长（JSON 请求体）
     *
     * @param body 字段同表单：targetType、videoId、seconds、finished、duration
     */
    @PostMapping(value = "/report", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult reportJson(@RequestBody Map<String, String> body)
    {
        String rawSeconds = body.get("seconds");
        String rawFinished = body.get("finished");
        String rawDuration = body.get("duration");
        Integer seconds = Convert.toInt(rawSeconds);
        Integer finished = Convert.toInt(rawFinished);
        Integer duration = Convert.toInt(rawDuration);
        if (seconds == null && rawSeconds != null && rawSeconds.length() > 0)
        {
            return AjaxResult.error("学习时长不合法");
        }
        if (finished == null && rawFinished != null && rawFinished.length() > 0)
        {
            return AjaxResult.error("完成标记不合法");
        }
        if (duration == null && rawDuration != null && rawDuration.length() > 0)
        {
            return AjaxResult.error("视频时长不合法");
        }
        return report(body.get("targetType"), Convert.toLong(body.get("videoId")), seconds, finished, duration);
    }

    /**
     * 学习数据总览（设计稿 20_学习数据 一屏的全部数字）
     *
     * @return {todayMinutes, todayCourseMinutes, todayArticleMinutes, continuousDays,
     *          weekMinutes:[{date, weekday, minutes, courseMinutes, articleMinutes} × 7],
     *          completedCourses, learningCourses, totalCourseMinutes,
     *          readPostsToday, readPostsWeek, articleMinutesWeek, noteCount}
     */
    @GetMapping("/stats")
    public AjaxResult stats()
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.putAll(studyService.stats(ApiContext.getUserId()));
        return ajax;
    }

    /**
     * 我的课程列表
     *
     * @param status   learning=在学；completed=已完成
     * @param pageNum  页码，默认 1
     * @param pageSize 每页条数，默认 20，最大 50
     * @return {total, rows:[{courseId, courseTitle, coverUrl, totalLessons,
     *          finishedLessons, progressPercent, lastLessonTitle, lastStudyTime}]}
     */
    @GetMapping("/courses")
    public AjaxResult courses(String status, Integer pageNum, Integer pageSize)
    {
        int page = pageNum == null ? 1 : pageNum;
        int size = pageSize == null ? 20 : pageSize;
        if (page < 1)
        {
            return AjaxResult.error("页码不合法");
        }
        if (size < 1 || size > 50)
        {
            return AjaxResult.error("每页条数不合法（1~50）");
        }

        PageHelper.startPage(page, size);
        List<Map<String, Object>> list = studyService.learningCourses(ApiContext.getUserId(), status);
        PageInfo<Map<String, Object>> pageInfo = new PageInfo<>(list);

        AjaxResult ajax = AjaxResult.success();
        ajax.put("total", pageInfo.getTotal());
        ajax.put("rows", list);
        return ajax;
    }
}
