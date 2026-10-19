package com.ruoyi.interaction.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 笔记表 learning_note
 *
 * 「笔记」是用户挂在自己看的内容下面的私有笔记：只有作者本人能看、能改、能删。
 * 两类挂载目标（target_type 用字符串，不用数字）：
 *   post  = 社区帖子（learning_post.post_id）—— 文章笔记
 *   video = 课程小节（learning_course_video.video_id）—— 视频笔记
 *           【不是】短视频：短视频没有笔记功能，
 *           也【不要】和 Interaction.TARGET_VIDEO（"3"=短视频）混用。
 *
 * 为什么用字符串而不是 1/2：Interaction / Comment 里 "2" 表示「课程」，而笔记的 video
 * 指的是课程下的一个【小节】，同一个值在不同表里含义不同是埋雷；
 * learning_category.category_type 已经是 course/article 这种字符串，有先例。
 *
 * 内容被逻辑删除后笔记【保留】（用户资产），接口通过 sourceDelFlag → sourceDeleted
 * 告诉客户端显示「来源已删除」。这是产品设计，不是孤儿数据，清理脚本不要动本表。
 *
 * progress 只在 video 类型下有意义：记这条笔记时视频播放到的秒数，仅用于列表展示
 * 「进度 22:00」；从笔记跳回视频时一律从头播放，服务端不拿它做任何跳转。
 * learning_course_video 没有时长字段，所以只能校验 >= 0，校验不了上限。
 *
 * 正文是【纯文本】，原样存 trim 后的用户输入，不做转义；渲染端只能以文本节点输出
 * （th:text / 小程序 <text> / textContent），禁止 innerHTML。
 *
 * 故意不继承 BaseEntity：本表没有 create_by / update_by / remark，
 * 继承了反而会让 getParams() 往每个返回行里多塞一个空的 params。
 *
 * @author edu
 */
public class Note implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 挂载类型：社区帖子（文章笔记） */
    public static final String TARGET_POST = "post";

    /** 挂载类型：课程小节（视频笔记，挂 learning_course_video.video_id） */
    public static final String TARGET_VIDEO = "video";

    /** 删除标志：存在 */
    public static final String DEL_FLAG_NORMAL = "0";

    /** 删除标志：已删除 */
    public static final String DEL_FLAG_DELETED = "2";

    /** 标题最大长度，必须与 learning_note.note_title 的 varchar(20) 保持一致 */
    public static final int MAX_TITLE_LENGTH = 20;

    /** 正文最大长度，必须与 learning_note.note_content 的 varchar(800) 保持一致 */
    public static final int MAX_CONTENT_LENGTH = 800;

    private Long noteId;

    /** 作者用户ID（sys_user.user_id），一律取 API 上下文，绝不接受前端传入 */
    private Long userId;

    /** 挂载类型：post / video */
    private String targetType;

    /** 挂载目标ID：post_id 或课程小节的 video_id */
    private Long targetId;

    /** 标题（纯文本，≤20字） */
    private String noteTitle;

    /** 正文（纯文本，≤800字） */
    private String noteContent;

    /** 视频笔记：记笔记时的播放位置(秒)；文章笔记为 null */
    private Integer progress;

    private String delFlag;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    // ===== 以下是展示字段：learning_note 表里没有对应列，由 Mapper 的子查询填充 =====

    /** 来源标题：文章笔记=帖子标题；视频笔记="课程名 · 小节名"；来源行不存在时为 null */
    private String sourceTitle;

    /** 来源所属课程ID（仅视频笔记有值），客户端跳「课程视频页」要用 */
    private Long courseId;

    /** 来源的删除标志（'0' 正常；null 或 '2' = 来源已删除），服务层换算成 sourceDeleted */
    private String sourceDelFlag;

    /** 来源是否已删除（true 时客户端显示「来源已删除」） */
    private Boolean sourceDeleted;

    /**
     * 挂载类型是否合法
     *
     * 只接受 post / video 两个常量；数字（1/2/3）一律不认 —— 那是互动/评论的编码，
     * 混进来就会把「短视频 3」错当成笔记目标。
     */
    public static boolean isValidTargetType(String t)
    {
        return TARGET_POST.equals(t) || TARGET_VIDEO.equals(t);
    }

    public Long getNoteId()
    {
        return noteId;
    }

    public void setNoteId(Long noteId)
    {
        this.noteId = noteId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getTargetType()
    {
        return targetType;
    }

    public void setTargetType(String targetType)
    {
        this.targetType = targetType;
    }

    public Long getTargetId()
    {
        return targetId;
    }

    public void setTargetId(Long targetId)
    {
        this.targetId = targetId;
    }

    public String getNoteTitle()
    {
        return noteTitle;
    }

    public void setNoteTitle(String noteTitle)
    {
        this.noteTitle = noteTitle;
    }

    public String getNoteContent()
    {
        return noteContent;
    }

    public void setNoteContent(String noteContent)
    {
        this.noteContent = noteContent;
    }

    public Integer getProgress()
    {
        return progress;
    }

    public void setProgress(Integer progress)
    {
        this.progress = progress;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    public String getSourceTitle()
    {
        return sourceTitle;
    }

    public void setSourceTitle(String sourceTitle)
    {
        this.sourceTitle = sourceTitle;
    }

    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    public String getSourceDelFlag()
    {
        return sourceDelFlag;
    }

    public void setSourceDelFlag(String sourceDelFlag)
    {
        this.sourceDelFlag = sourceDelFlag;
    }

    public Boolean getSourceDeleted()
    {
        return sourceDeleted;
    }

    public void setSourceDeleted(Boolean sourceDeleted)
    {
        this.sourceDeleted = sourceDeleted;
    }
}
