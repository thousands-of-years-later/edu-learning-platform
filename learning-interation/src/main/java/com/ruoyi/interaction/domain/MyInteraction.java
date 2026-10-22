package com.ruoyi.interaction.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 「我的点赞 / 我的收藏」列表项（不是一张表，是查询结果）
 *
 * 数据来源是 learning_interaction（谁、对哪类内容、点赞还是收藏、时间），
 * 标题/封面/作者用标量子查询从三张内容表现取，所以这里没有对应的 insert/update。
 *
 * 三种内容的展示差异（设计稿 22_我的收藏 / 23_我的点赞）：
 *   课程     ：封面 + 课程名 + 时间
 *   短视频   ：封面 + 视频标题 + 时间
 *   文章     ：【没有封面】，显示标题 + 作者 + 时间
 * 所以 coverUrl 只有课程/短视频有值，author 只有文章/短视频有值（课程没有"作者"概念）。
 *
 * targetType 对外用字符串（post / course / shortVideo），与观看历史、笔记的接口口径一致；
 * 落库时再转成 learning_interaction.target_type 的 char(1) 编码（1/2/3）。
 *
 * @author edu
 */
public class MyInteraction implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 对外类型：文章（帖子） */
    public static final String TYPE_POST = "post";

    /** 对外类型：课程 */
    public static final String TYPE_COURSE = "course";

    /** 对外类型：短视频 */
    public static final String TYPE_SHORT_VIDEO = "shortVideo";

    private Long interactionId;

    /** 内容类型：post / course / shortVideo */
    private String targetType;

    /** 内容ID：帖子ID / 课程ID / 短视频ID */
    private Long targetId;

    /** 标题：帖子标题 / 课程名 / 视频标题 */
    private String title;

    /** 封面：课程封面 / 短视频封面；文章没有封面，恒为 null */
    private String coverUrl;

    /** 作者：文章的帖子作者、短视频的上传者；课程为 null */
    private String author;

    /** 点赞/收藏时间（列表按它倒序） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /**
     * 对外类型是否合法（只认 post / course / shortVideo）
     */
    public static boolean isValidTargetType(String t)
    {
        return TYPE_POST.equals(t) || TYPE_COURSE.equals(t) || TYPE_SHORT_VIDEO.equals(t);
    }

    /**
     * 对外类型 -> 互动表的 char(1) 编码（post=1 / course=2 / shortVideo=3）
     *
     * 复用 {@link Interaction} 里的常量，避免两处编码不一致；
     * 不合法或为空返回 null（列表不传类型 = 查全部）。
     */
    public static String toDbTargetType(String targetType)
    {
        if (TYPE_POST.equals(targetType))
        {
            return Interaction.TARGET_POST;
        }
        if (TYPE_COURSE.equals(targetType))
        {
            return Interaction.TARGET_COURSE;
        }
        if (TYPE_SHORT_VIDEO.equals(targetType))
        {
            return Interaction.TARGET_VIDEO;
        }
        return null;
    }

    public Long getInteractionId()
    {
        return interactionId;
    }

    public void setInteractionId(Long interactionId)
    {
        this.interactionId = interactionId;
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

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getCoverUrl()
    {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl)
    {
        this.coverUrl = coverUrl;
    }

    public String getAuthor()
    {
        return author;
    }

    public void setAuthor(String author)
    {
        this.author = author;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}
