package com.ruoyi.user.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 课程小节学习进度 learning_course_progress
 *
 * 一个用户一个小节一行（唯一索引 uk_progress_user_video）。
 * finished 一旦置 1 不会被后续上报改回 0（见 Mapper 的 greatest 写法）。
 *
 * @author edu
 */
public class CourseProgress implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long progressId;

    private Long userId;

    private Long courseId;

    private Long videoId;

    /** 是否学完：0 否 / 1 是 */
    private Integer finished;

    /** 该小节累计学习秒数 */
    private Integer watchedSeconds;

    private Date createTime;

    private Date updateTime;

    public Long getProgressId()
    {
        return progressId;
    }

    public void setProgressId(Long progressId)
    {
        this.progressId = progressId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    public Long getVideoId()
    {
        return videoId;
    }

    public void setVideoId(Long videoId)
    {
        this.videoId = videoId;
    }

    public Integer getFinished()
    {
        return finished;
    }

    public void setFinished(Integer finished)
    {
        this.finished = finished;
    }

    public Integer getWatchedSeconds()
    {
        return watchedSeconds;
    }

    public void setWatchedSeconds(Integer watchedSeconds)
    {
        this.watchedSeconds = watchedSeconds;
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
}
