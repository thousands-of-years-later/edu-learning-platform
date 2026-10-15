package com.ruoyi.video.domain;

import jakarta.validation.constraints.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 短视频表 learning_short_video
 *
 * 短视频是「视频」Tab 的内容主体，由管理员在后台录入，没有分类、没有章节。
 * 点赞、收藏、评论与 course / post 一致：只做计数展示（三个 int 列），
 * 不建「每个用户一条记录」的关联表。
 *
 * @author edu
 */
public class ShortVideo extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：正常（小程序可见） */
    public static final String STATUS_NORMAL = "0";

    /** 状态：停用（小程序不可见） */
    public static final String STATUS_DISABLED = "1";

    /** 排序方式：按点赞数（learning_short_video.like_count，搜索接口用） */
    public static final String SORT_BY_LIKE_COUNT = "likeCount";

    /** 短视频ID */
    @Excel(name = "视频ID", cellType = ColumnType.NUMERIC)
    private Long videoId;

    /** 视频标题 */
    @Excel(name = "视频标题")
    private String videoTitle;

    /** 上传者用户ID（逻辑关联 sys_user.user_id） */
    private Long userId;

    /** 视频地址（上传返回的相对路径 /profile/...）。刻意不加 @Excel：路径对导出无意义 */
    private String videoUrl;

    /** 封面图片地址（上传返回的相对路径 /profile/...） */
    private String coverUrl;

    /** 视频简介 */
    @Excel(name = "视频简介")
    private String intro;

    /** 时长（秒） */
    @Excel(name = "时长(秒)", cellType = ColumnType.NUMERIC)
    private Integer duration;

    /** 播放数 */
    @Excel(name = "播放数", cellType = ColumnType.NUMERIC)
    private Integer viewCount;

    /** 点赞数 */
    @Excel(name = "点赞数", cellType = ColumnType.NUMERIC)
    private Integer likeCount;

    /** 收藏数 */
    @Excel(name = "收藏数", cellType = ColumnType.NUMERIC)
    private Integer favoriteCount;

    /** 评论数 */
    @Excel(name = "评论数", cellType = ColumnType.NUMERIC)
    private Integer commentCount;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    /** 排序方式（App 搜索接口入参，非本表列；由 Mapper 白名单翻译成 ORDER BY） */
    private String sortBy;

    /** 上传者登录名（关联 sys_user 查出，非本表列） */
    @Excel(name = "上传者")
    private String userName;

    public Long getVideoId()
    {
        return videoId;
    }

    public void setVideoId(Long videoId)
    {
        this.videoId = videoId;
    }

    @NotBlank(message = "视频标题不能为空")
    @Size(min = 0, max = 200, message = "视频标题长度不能超过200个字符")
    public String getVideoTitle()
    {
        return videoTitle;
    }

    public void setVideoTitle(String videoTitle)
    {
        this.videoTitle = videoTitle;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    @Size(min = 0, max = 500, message = "视频地址长度不能超过500个字符")
    public String getVideoUrl()
    {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl)
    {
        this.videoUrl = videoUrl;
    }

    @Size(min = 0, max = 500, message = "封面图片地址长度不能超过500个字符")
    public String getCoverUrl()
    {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl)
    {
        this.coverUrl = coverUrl;
    }

    @Size(min = 0, max = 1000, message = "视频简介长度不能超过1000个字符")
    public String getIntro()
    {
        return intro;
    }

    public void setIntro(String intro)
    {
        this.intro = intro;
    }

    @Min(value = 0, message = "时长不能为负数")
    public Integer getDuration()
    {
        return duration;
    }

    public void setDuration(Integer duration)
    {
        this.duration = duration;
    }

    @Min(value = 0, message = "播放数不能为负数")
    public Integer getViewCount()
    {
        return viewCount;
    }

    public void setViewCount(Integer viewCount)
    {
        this.viewCount = viewCount;
    }

    @Min(value = 0, message = "点赞数不能为负数")
    public Integer getLikeCount()
    {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount)
    {
        this.likeCount = likeCount;
    }

    @Min(value = 0, message = "收藏数不能为负数")
    public Integer getFavoriteCount()
    {
        return favoriteCount;
    }

    public void setFavoriteCount(Integer favoriteCount)
    {
        this.favoriteCount = favoriteCount;
    }

    @Min(value = 0, message = "评论数不能为负数")
    public Integer getCommentCount()
    {
        return commentCount;
    }

    public void setCommentCount(Integer commentCount)
    {
        this.commentCount = commentCount;
    }

    @Size(min = 0, max = 1, message = "状态长度不能超过1个字符")
    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getSortBy()
    {
        return sortBy;
    }

    public void setSortBy(String sortBy)
    {
        this.sortBy = sortBy;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("videoId", getVideoId())
            .append("videoTitle", getVideoTitle())
            .append("userId", getUserId())
            .append("videoUrl", getVideoUrl())
            .append("coverUrl", getCoverUrl())
            .append("intro", getIntro())
            .append("duration", getDuration())
            .append("viewCount", getViewCount())
            .append("likeCount", getLikeCount())
            .append("favoriteCount", getFavoriteCount())
            .append("commentCount", getCommentCount())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}