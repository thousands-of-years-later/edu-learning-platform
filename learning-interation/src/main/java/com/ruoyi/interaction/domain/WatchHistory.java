package com.ruoyi.interaction.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 观看历史 learning_watch_history
 *
 * 三种内容共用一个时间线（设计稿 21_观看历史）：
 *   shortVideo = 短视频（target_id 是 learning_short_video.video_id）
 *   course     = 课程（target_id 是 learning_course.course_id）
 *   post       = 文章/帖子（target_id 是 learning_post.post_id）
 *
 * 【进度跟随最新】：重看时直接覆盖 position，不取最大值 ——
 * 历史里的"看到 X 分 Y 秒"永远等于最近一次播放的位置，断点续播也从这里接着播。
 *
 * 【课程按课程粒度】：一门课只留一条，videoId 记住上次看的小节，
 * position 是【该小节内】的秒数；article 没有进度，position / duration 恒为 null。
 *
 * 唯一索引 (user_id, target_type, target_id) 保证同一条内容只有一行（重看=更新），
 * 也让并发上报天然安全（第二个请求会撞唯一键转去更新）。
 *
 * 故意不继承 BaseEntity：本表没有 create_by / update_by / remark。
 *
 * @author edu
 */
public class WatchHistory implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 内容类型：短视频 */
    public static final String TARGET_SHORT_VIDEO = "shortVideo";

    /** 内容类型：课程 */
    public static final String TARGET_COURSE = "course";

    /** 内容类型：文章（帖子） */
    public static final String TARGET_POST = "post";

    /** 内容删除标志：正常（内容表 del_flag 的取值） */
    public static final String DEL_FLAG_NORMAL = "0";

    private Long historyId;

    /** 用户ID（sys_user.user_id，只从 Token 上下文取） */
    private Long userId;

    /** 内容类型：shortVideo / course / post */
    private String targetType;

    /** 内容ID：短视频ID / 课程ID / 帖子ID */
    private Long targetId;

    /** 课程：上次观看的小节ID（断点续播定位用），其它类型为 null */
    private Long videoId;

    /** 视频：最近一次看到第几秒；文章为 null */
    private Integer position;

    /** 视频：总时长(秒)；文章为 null */
    private Integer duration;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lastViewTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    // ===== 以下是展示字段：表里没有对应列，由 Mapper 的子查询填充 =====

    /** 标题：短视频=视频标题；课程=课程名；文章=帖子标题；内容已删/不存在时为 null */
    private String title;

    /** 封面：短视频=自己的封面；课程=课程封面；文章没有封面列，恒为 null */
    private String coverUrl;

    /** 副标题：课程=上次观看的小节标题；其它为 null */
    private String subtitle;

    /** 内容的删除标志（'0' 正常；null/其它 = 已删），服务层换算成 sourceDeleted */
    private String sourceDelFlag;

    /** 内容是否已删除（true 时前端显示"已下架"且不可点） */
    private Boolean sourceDeleted;

    /**
     * 类型是否合法（只认三种）
     */
    public static boolean isValidTargetType(String t)
    {
        return TARGET_SHORT_VIDEO.equals(t) || TARGET_COURSE.equals(t) || TARGET_POST.equals(t);
    }

    /**
     * 是否是"有进度"的类型（短视频 / 课程）
     */
    public static boolean isVideoType(String t)
    {
        return TARGET_SHORT_VIDEO.equals(t) || TARGET_COURSE.equals(t);
    }

    public Long getHistoryId()
    {
        return historyId;
    }

    public void setHistoryId(Long historyId)
    {
        this.historyId = historyId;
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

    public Long getVideoId()
    {
        return videoId;
    }

    public void setVideoId(Long videoId)
    {
        this.videoId = videoId;
    }

    public Integer getPosition()
    {
        return position;
    }

    public void setPosition(Integer position)
    {
        this.position = position;
    }

    public Integer getDuration()
    {
        return duration;
    }

    public void setDuration(Integer duration)
    {
        this.duration = duration;
    }

    public Date getLastViewTime()
    {
        return lastViewTime;
    }

    public void setLastViewTime(Date lastViewTime)
    {
        this.lastViewTime = lastViewTime;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
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

    public String getSubtitle()
    {
        return subtitle;
    }

    public void setSubtitle(String subtitle)
    {
        this.subtitle = subtitle;
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
