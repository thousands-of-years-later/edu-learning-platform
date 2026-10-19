package com.ruoyi.interaction.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.interaction.domain.Note;
import com.ruoyi.interaction.mapper.NoteMapper;
import com.ruoyi.interaction.service.INoteService;

/**
 * 笔记 服务层处理
 *
 * 几条贯穿全类的规矩：
 *   1) userId 只由调用方从 API 上下文传入，本类不接受「前端自报的用户ID」；
 *   2) 改 / 删走 SQL 的 where 兜底（CAS），影响 0 行 = 不存在 / 不是我的 / 已删除，
 *      三种情况返回同一句错误，不给调用方探测「这个 id 是否存在」的机会；
 *   3) 笔记保留、内容删除只影响展示：来源被删后 note 仍在，由 sourceDeleted 让客户端显示
 *      「来源已删除」——这是产品设计，不是孤儿数据。
 *
 * @author edu
 */
@Service
public class NoteServiceImpl implements INoteService
{
    @Autowired
    private NoteMapper noteMapper;

    @Override
    public Long insertNote(Long userId, String targetType, Long targetId,
                           String noteTitle, String noteContent, Integer progress)
    {
        checkUser(userId);
        String type = checkTargetType(targetType);
        if (targetId == null)
        {
            throw new ServiceException("目标ID不能为空");
        }
        // 只允许给「看得见的内容」记笔记：草稿/已下架帖、停用课程、已删小节都拦掉，
        // 否则等于给客户端开了一个「探测哪些 ID 是未发布内容」的口子（与点赞收藏同一口径）
        boolean targetVisible = Note.TARGET_POST.equals(type)
                ? noteMapper.existsPost(targetId) > 0
                : noteMapper.existsCourseVideo(targetId) > 0;
        if (!targetVisible)
        {
            throw new ServiceException("内容不存在或当前不可记笔记");
        }

        Note note = new Note();
        note.setUserId(userId);
        note.setTargetType(type);
        note.setTargetId(targetId);
        note.setNoteTitle(checkTitle(noteTitle));
        note.setNoteContent(checkContent(noteContent));
        // progress 只属于视频笔记：文章笔记即使传了也丢掉，
        // 否则库里会出现「文章笔记带播放进度」这种没有意义、还会让前端误展示的脏数据
        note.setProgress(Note.TARGET_VIDEO.equals(type) ? checkProgress(progress) : null);
        noteMapper.insertNote(note);
        return note.getNoteId();
    }

    @Override
    public List<Note> selectNoteList(Long userId, String targetType, Long targetId, String keyword)
    {
        checkUser(userId);
        String type = StringUtils.isEmpty(StringUtils.trim(targetType)) ? null : checkTargetType(targetType);
        String kw = StringUtils.isEmpty(StringUtils.trim(keyword)) ? null : StringUtils.trim(keyword);
        List<Note> list = noteMapper.selectNoteList(userId, type, targetId, kw);
        for (Note note : list)
        {
            fillSourceDeleted(note);
        }
        return list;
    }

    @Override
    public Note selectNoteById(Long userId, Long noteId)
    {
        checkUser(userId);
        if (noteId == null)
        {
            throw new ServiceException("笔记ID不能为空");
        }
        Note note = noteMapper.selectNoteById(noteId);
        // 别人的笔记按「不存在」处理：能区分就等于告诉了调用方这个 id 存在
        if (note == null || !userId.equals(note.getUserId()))
        {
            throw new ServiceException("笔记不存在或已被删除");
        }
        fillSourceDeleted(note);
        return note;
    }

    @Override
    public int updateNote(Long userId, Long noteId, String noteTitle, String noteContent)
    {
        checkUser(userId);
        if (noteId == null)
        {
            throw new ServiceException("笔记ID不能为空");
        }
        Note note = new Note();
        note.setNoteId(noteId);
        note.setUserId(userId);
        note.setNoteTitle(checkTitle(noteTitle));
        note.setNoteContent(checkContent(noteContent));
        if (noteMapper.updateNote(note) == 0)
        {
            throw new ServiceException("笔记不存在或已被删除");
        }
        return 1;
    }

    @Override
    public int deleteNote(Long userId, Long noteId)
    {
        checkUser(userId);
        if (noteId == null)
        {
            throw new ServiceException("笔记ID不能为空");
        }
        if (noteMapper.softDeleteById(noteId, userId) == 0)
        {
            throw new ServiceException("笔记不存在或已被删除");
        }
        return 1;
    }

    @Override
    public void checkQuery(String targetType, Long targetId)
    {
        if (StringUtils.isNotEmpty(StringUtils.trim(targetType)))
        {
            checkTargetType(targetType);
        }
        else if (targetId != null)
        {
            // post 的 5 和 video 的 5 是两个不同的目标，只按 ID 过滤会把两类笔记混在一起
            throw new ServiceException("按目标查询笔记时必须同时指定目标类型");
        }
        // 不带任何过滤 = 查全部笔记，是合法用法
    }

    /**
     * 由 sourceDelFlag 换算「来源是否已删除」
     *
     * 来源行整行不存在时子查询返回 null，同样算「来源已删除」：
     * 笔记本身保留（用户资产），但客户端必须能显示「来源已删除」而不是一片空白。
     */
    private void fillSourceDeleted(Note note)
    {
        note.setSourceDeleted(!Note.DEL_FLAG_NORMAL.equals(note.getSourceDelFlag()));
    }

    private void checkUser(Long userId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
    }

    /**
     * 校验并归一挂载类型（trim 后只认 post / video）
     */
    private String checkTargetType(String targetType)
    {
        String type = StringUtils.trim(targetType);
        if (StringUtils.isEmpty(type))
        {
            throw new ServiceException("目标类型不能为空");
        }
        if (!Note.isValidTargetType(type))
        {
            throw new ServiceException("目标类型不合法（只支持 post / video）");
        }
        return type;
    }

    /**
     * 校验并归一标题：trim 后不能为空、不能超过 20 字
     *
     * 长度与 learning_note.note_title 的 varchar(20) 对齐，服务层先拦能给客户端明确文案，
     * 数据库列长度是最后一道防线（避免 1406 Data too long 这种看不懂的报错）。
     */
    private String checkTitle(String noteTitle)
    {
        String title = StringUtils.trim(noteTitle);
        if (StringUtils.isEmpty(title))
        {
            throw new ServiceException("笔记标题不能为空");
        }
        if (title.length() > Note.MAX_TITLE_LENGTH)
        {
            throw new ServiceException("笔记标题不能超过" + Note.MAX_TITLE_LENGTH + "个字");
        }
        return title;
    }

    /**
     * 校验并归一正文：trim 后不能为空、不能超过 800 字
     */
    private String checkContent(String noteContent)
    {
        String content = StringUtils.trim(noteContent);
        if (StringUtils.isEmpty(content))
        {
            throw new ServiceException("笔记正文不能为空");
        }
        if (content.length() > Note.MAX_CONTENT_LENGTH)
        {
            throw new ServiceException("笔记正文不能超过" + Note.MAX_CONTENT_LENGTH + "个字");
        }
        return content;
    }

    /**
     * 校验播放进度：null 合法（客户端可以不知道当前位置），负数不合法
     *
     * learning_course_video 没有时长字段，所以校验不了「不超过视频时长」，只能卡下限。
     */
    private Integer checkProgress(Integer progress)
    {
        if (progress == null)
        {
            return null;
        }
        if (progress < 0)
        {
            throw new ServiceException("播放进度不能小于 0");
        }
        return progress;
    }
}
