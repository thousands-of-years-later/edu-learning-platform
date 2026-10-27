package com.ruoyi.interaction.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.interaction.domain.WatchHistory;
import com.ruoyi.interaction.service.IWatchHistoryService;

/**
 * 观看历史 接口 v1（Token 认证，给小程序用）
 *
 * 对应设计稿 21_观看历史：列表（封面 + 标题 + 时间 · 看到 X 分 Y 秒）、右上角清空。
 * 三种内容混在一条时间线：短视频（shortVideo）/ 课程（course）/ 文章（post）。
 *
 * 进度口径【跟随最新】：播放器每 15~30 秒（以及暂停、退出时）调一次 /report，
 * 重看会覆盖进度；播放页进入时先调 /progress 取续播位置。
 *
 * 用户ID一律取 ApiContext，只能看/删自己的历史。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/history")
public class ApiV1WatchHistoryController extends BaseController
{
    @Autowired
    private IWatchHistoryService watchHistoryService;

    /**
     * 上报观看（首次记录，之后覆盖进度与时间）
     *
     * @param targetType shortVideo（短视频）/ course（课程）/ post（文章）
     * @param targetId   短视频ID / 课程ID / 帖子ID
     * @param videoId    课程专用：本次观看的小节ID
     * @param position   视频：看到第几秒（文章不用传）
     * @param duration   视频：总时长（短视频以内容表为准，课程小节用这个值）
     * @return {historyId, targetType, targetId, videoId, position, duration,
     *          firstTime: 是否首次观看（短视频首次 +1 播放数，文章首次 +1 阅读数）}
     */
    @PostMapping("/report")
    public AjaxResult report(String targetType, Long targetId, Long videoId, Integer position, Integer duration)
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.putAll(watchHistoryService.report(ApiContext.getUserId(), targetType, targetId,
                videoId, position, duration));
        return ajax;
    }

    /**
     * 上报观看（JSON 请求体）
     *
     * @param body 字段同表单：targetType、targetId、videoId、position、duration
     */
    @PostMapping(value = "/report", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult reportJson(@RequestBody Map<String, String> body)
    {
        // Convert 系列对「有值但转不动」（如 "abc"）是静默返回 null 的，这里把坏值挡掉，
        // 否则客户端发个坏进度会被当成「没有进度」悄悄存进去
        String rawPosition = body.get("position");
        String rawDuration = body.get("duration");
        Integer position = Convert.toInt(rawPosition);
        Integer duration = Convert.toInt(rawDuration);
        if (position == null && StringUtils.isNotEmpty(rawPosition))
        {
            return AjaxResult.error("播放进度不合法");
        }
        if (duration == null && StringUtils.isNotEmpty(rawDuration))
        {
            return AjaxResult.error("视频时长不合法");
        }
        return report(body.get("targetType"), Convert.toLong(body.get("targetId")),
                Convert.toLong(body.get("videoId")), position, duration);
    }

    /**
     * 我的观看历史（分页，按最近观看时间倒序）
     *
     * @param targetType 可选：shortVideo / course / post（不传 = 全部）
     * @return {total, rows:[{historyId, targetType, targetId, videoId, title, coverUrl,
     *          subtitle, position, duration, lastViewTime, sourceDeleted}]}
     */
    @GetMapping("/list")
    public TableDataInfo list(String targetType)
    {
        // 校验放在 startPage() 之前：PageHelper 的分页参数挂在 ThreadLocal 上，
        // 非法参数若在之后抛出，分页参数会残留到本线程的下一条查询
        watchHistoryService.checkQuery(targetType);
        startPage();
        List<WatchHistory> list = watchHistoryService.list(ApiContext.getUserId(), targetType);
        return getDataTable(list);
    }

    /**
     * 查断点（播放页进入时调）
     *
     * @param targetType shortVideo / course
     * @param targetId   短视频ID / 课程ID
     * @return {found, videoId: 课程上次看的小节, position, duration, lastViewTime}
     */
    @GetMapping("/progress")
    public AjaxResult progress(String targetType, Long targetId)
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.putAll(watchHistoryService.progress(ApiContext.getUserId(), targetType, targetId));
        return ajax;
    }

    /**
     * 删除一条历史
     *
     * @param historyId 历史ID
     */
    @PostMapping("/remove")
    public AjaxResult remove(Long historyId)
    {
        return toAjax(watchHistoryService.remove(ApiContext.getUserId(), historyId));
    }

    /**
     * 删除一条历史（JSON 请求体）
     *
     * @param body 字段同表单：historyId
     */
    @PostMapping(value = "/remove", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult removeJson(@RequestBody Map<String, String> body)
    {
        return remove(Convert.toLong(body.get("historyId")));
    }

    /**
     * 按类型清空我的观看历史（设计稿右上角「清空」）
     *
     * 前端是「短视频 / 课程 / 文章」三个 tab，各自清空自己那一类，**不做清空全部**。
     * targetType 必填：少传一个参数就把三类历史一起清掉太危险，所以不提供"不传=清全部"的用法。
     *
     * @param targetType shortVideo / course / post
     * @return {msg, code, count: 实际删除的条数}
     */
    @PostMapping("/clear")
    public AjaxResult clear(String targetType)
    {
        int count = watchHistoryService.clear(ApiContext.getUserId(), targetType);
        return AjaxResult.success("已清空").put("count", count);
    }
}
