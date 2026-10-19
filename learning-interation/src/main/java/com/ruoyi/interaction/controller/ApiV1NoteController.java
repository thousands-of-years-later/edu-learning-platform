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
import com.ruoyi.interaction.domain.Note;
import com.ruoyi.interaction.service.INoteService;

/**
 * 笔记 接口 v1（Token 认证，给小程序用）
 *
 * 只有这一套入口（没有 /api/note 会话版）：笔记是新功能，没有存量客户端，
 * 不像互动/评论那样要兼容老小程序，没必要维护两个入口。
 *
 * 用户ID一律取 ApiContext（由 ApiTokenFilter 校验 Token 后写入），绝不接受前端传入；
 * 所有接口都只操作「我的笔记」，别人的笔记按「不存在」处理。
 *
 * 每个写接口都提供【表单】与【JSON】两个出口，靠 consumes 区分（与 ApiV1CommentController 同一写法）。
 *
 * 挂载目标两类：
 *   targetType=post  → targetId 是帖子ID（文章笔记）
 *   targetType=video → targetId 是课程小节的 video_id（视频笔记，不是短视频）
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/note")
public class ApiV1NoteController extends BaseController
{
    @Autowired
    private INoteService noteService;

    /**
     * 新增笔记
     *
     * @param targetType  挂载类型：post（帖子）/ video（课程小节）
     * @param targetId    挂载目标ID（帖子ID 或 课程小节的 video_id）
     * @param noteTitle   标题（纯文本，≤20字）
     * @param noteContent 正文（纯文本，≤800字）
     * @param progress    视频笔记的播放位置(秒)，可选；文章笔记会被忽略
     * @return {noteId: 新笔记ID}
     */
    @PostMapping("/add")
    public AjaxResult add(String targetType, Long targetId, String noteTitle, String noteContent, Integer progress)
    {
        Long noteId = noteService.insertNote(ApiContext.getUserId(), targetType, targetId, noteTitle, noteContent, progress);
        return AjaxResult.success().put("noteId", noteId);
    }

    /**
     * 新增笔记（JSON 请求体）
     *
     * @param body 请求体，字段同表单：targetType、targetId、noteTitle、noteContent、progress（可选）
     */
    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult addJson(@RequestBody Map<String, String> body)
    {
        String rawProgress = body.get("progress");
        Integer progress = Convert.toInt(rawProgress);
        // Convert.toInt 对「有值但转不动」（如 "abc"）是【无声返回 null】的，而 null 在本接口
        // 是合法语义（不传进度）。不在这里挡掉，客户端发个坏值就会静默变成「没有进度」；
        // 表单出口对同样的坏值是直接报错的，两个出口必须一致。
        if (progress == null && StringUtils.isNotEmpty(rawProgress))
        {
            return AjaxResult.error("播放进度不合法");
        }
        return add(body.get("targetType"), Convert.toLong(body.get("targetId")),
                body.get("noteTitle"), body.get("noteContent"), progress);
    }

    /**
     * 修改我的笔记（只改标题与正文；播放进度不参与编辑）
     *
     * @param noteId      笔记ID
     * @param noteTitle   新标题（≤20字）
     * @param noteContent 新正文（纯文本，≤800字）
     */
    @PostMapping("/edit")
    public AjaxResult edit(Long noteId, String noteTitle, String noteContent)
    {
        return toAjax(noteService.updateNote(ApiContext.getUserId(), noteId, noteTitle, noteContent));
    }

    /**
     * 修改我的笔记（JSON 请求体）
     *
     * @param body 请求体，字段同表单：noteId、noteTitle、noteContent
     */
    @PostMapping(value = "/edit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult editJson(@RequestBody Map<String, String> body)
    {
        return edit(Convert.toLong(body.get("noteId")), body.get("noteTitle"), body.get("noteContent"));
    }

    /**
     * 删除我的笔记（逻辑删除）
     *
     * @param noteId 笔记ID
     */
    @PostMapping("/remove")
    public AjaxResult remove(Long noteId)
    {
        return toAjax(noteService.deleteNote(ApiContext.getUserId(), noteId));
    }

    /**
     * 删除我的笔记（JSON 请求体）
     *
     * @param body 请求体，字段同表单：noteId
     */
    @PostMapping(value = "/remove", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AjaxResult removeJson(@RequestBody Map<String, String> body)
    {
        return remove(Convert.toLong(body.get("noteId")));
    }

    /**
     * 我的笔记列表（分页，按最近编辑倒序）
     *
     * @param targetType 可选：post / video（不传 = 全部，对应「全部 / 文章笔记 / 视频笔记」三个 tab）
     * @param targetId   可选：某个小节/帖子下我写的笔记（必须同时传 targetType）
     * @param keyword    可选：标题或正文模糊搜索
     * @return {rows: [笔记（含 sourceTitle / courseId / sourceDeleted）], total}（分页参数 pageNum / pageSize）
     */
    @GetMapping("/list")
    public TableDataInfo list(String targetType, Long targetId, String keyword)
    {
        // 校验必须在 startPage() 之前：PageHelper 的分页参数挂在 ThreadLocal 上，
        // 会被它之后的第一条查询消费；非法参数若在 startPage() 之后抛出，分页参数会残留到本线程的下一条查询
        noteService.checkQuery(targetType, targetId);
        startPage();
        List<Note> list = noteService.selectNoteList(ApiContext.getUserId(), targetType, targetId, keyword);
        return getDataTable(list);
    }

    /**
     * 我的笔记详情
     *
     * @param noteId 笔记ID
     * @return {code, msg, data: 笔记}（来源被删时 sourceDeleted=true，客户端显示「来源已删除」）
     */
    @GetMapping("/info")
    public AjaxResult info(Long noteId)
    {
        return AjaxResult.success(noteService.selectNoteById(ApiContext.getUserId(), noteId));
    }
}
