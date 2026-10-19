package com.ruoyi.interaction.service;

import java.util.List;
import com.ruoyi.interaction.domain.Note;

/**
 * 笔记 服务层
 *
 * 全部接口都是「我的笔记」：userId 一律由调用方从 API 上下文带入，服务层不接受
 * 前端自报的用户ID；改/删都由 SQL 的 where 条件兜底（CAS），查不到就报错。
 *
 * @author edu
 */
public interface INoteService
{
    /**
     * 新增一条笔记
     *
     * @param userId      作者用户ID（API 上下文）
     * @param targetType  挂载类型：post / video
     * @param targetId    挂载目标ID（post_id 或课程小节的 video_id）
     * @param noteTitle   标题（≤20字）
     * @param noteContent 正文（纯文本，≤800字）
     * @param progress    视频笔记的播放位置(秒)，可为 null；文章笔记传了会被丢弃
     * @return 新笔记ID
     */
    public Long insertNote(Long userId, String targetType, Long targetId,
                           String noteTitle, String noteContent, Integer progress);

    /**
     * 查询我的笔记列表（分页由上层 PageHelper 驱动）
     *
     * @param userId     作者用户ID
     * @param targetType 可选过滤：post / video
     * @param targetId   可选过滤：某个小节/文章下的笔记（需同时给 targetType）
     * @param keyword    可选搜索：标题或正文模糊
     * @return 笔记集合（含来源标题与「来源已删除」标记），按最近编辑倒序
     */
    public List<Note> selectNoteList(Long userId, String targetType, Long targetId, String keyword);

    /**
     * 查询我的某条笔记详情（别人的笔记按「不存在」处理，不泄露存在性）
     *
     * @param userId 作者用户ID
     * @param noteId 笔记ID
     * @return 笔记（含来源标题 / 课程ID / 来源是否已删除）
     */
    public Note selectNoteById(Long userId, Long noteId);

    /**
     * 修改自己的笔记（只改标题与正文）
     *
     * @param userId      作者用户ID
     * @param noteId      笔记ID
     * @param noteTitle   新标题（≤20字）
     * @param noteContent 新正文（纯文本，≤800字）
     * @return 影响行数（恒为 1，0 行会抛业务异常）
     */
    public int updateNote(Long userId, Long noteId, String noteTitle, String noteContent);

    /**
     * 删除自己的笔记（逻辑删除）
     *
     * @param userId 作者用户ID
     * @param noteId 笔记ID
     * @return 影响行数（恒为 1，0 行会抛业务异常）
     */
    public int deleteNote(Long userId, Long noteId);

    /**
     * 校验列表的过滤参数（必须在 PageHelper 的 startPage() 之前调用）
     *
     * 理由：PageHelper 的分页参数挂在 ThreadLocal 上，由它之后的第一条查询消费；
     * 若校验失败发生在 startPage() 之后且没有查询消费它，分页参数会残留到本线程的下一条查询。
     * 规则：targetId 必须配 targetType（post 5 和 video 5 是不同的目标，不能只按 ID 过滤）。
     *
     * @param targetType 挂载类型（可空）
     * @param targetId   挂载目标ID（可空）
     */
    public void checkQuery(String targetType, Long targetId);
}
