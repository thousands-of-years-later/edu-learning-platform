package com.ruoyi.course.domain;

import jakarta.validation.constraints.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 课程视频表 learning_course_video
 *
 * chapterId 可空：为空表示该视频不挂章节、直接属于课程。
 * 只有一张视频表，不区分「有章节 / 无章节」两套关系。
 *
 * @author edu
 */
public class CourseVideo extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 视频ID */
    @Excel(name = "视频ID", cellType = ColumnType.NUMERIC)
    private Long videoId;

    /** 所属课程ID */
    @Excel(name = "所属课程", cellType = ColumnType.NUMERIC)
    private Long courseId;

    /** 所属章节ID（可为空，为空表示无章节） */
    private Long chapterId;

    /** 视频标题 */
    @Excel(name = "视频标题")
    private String videoTitle;

    /** 视频地址 */
    private String videoUrl;

    /** 显示顺序 */
    @Excel(name = "显示顺序", cellType = ColumnType.NUMERIC)
    private Integer orderNum;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    /** 所属章节标题（关联 learning_course_chapter 查出，非本表列） */
    @Excel(name = "所属章节")
    private String chapterTitle;

    public Long getVideoId()
    {
        return videoId;
    }

    public void setVideoId(Long videoId)
    {
        this.videoId = videoId;
    }

    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    public Long getChapterId()
    {
        return chapterId;
    }

    public void setChapterId(Long chapterId)
    {
        this.chapterId = chapterId;
    }

    @NotBlank(message = "视频标题不能为空")
    @Size(min = 0, max = 100, message = "视频标题长度不能超过100个字符")
    public String getVideoTitle()
    {
        return videoTitle;
    }

    public void setVideoTitle(String videoTitle)
    {
        this.videoTitle = videoTitle;
    }

    @NotBlank(message = "请先上传视频文件")
    @Size(min = 0, max = 500, message = "视频地址长度不能超过500个字符")
    public String getVideoUrl()
    {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl)
    {
        this.videoUrl = videoUrl;
    }

    public Integer getOrderNum()
    {
        return orderNum;
    }

    public void setOrderNum(Integer orderNum)
    {
        this.orderNum = orderNum;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getChapterTitle()
    {
        return chapterTitle;
    }

    public void setChapterTitle(String chapterTitle)
    {
        this.chapterTitle = chapterTitle;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("videoId", getVideoId())
            .append("courseId", getCourseId())
            .append("chapterId", getChapterId())
            .append("videoTitle", getVideoTitle())
            .append("videoUrl", getVideoUrl())
            .append("orderNum", getOrderNum())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
