package com.ruoyi.course.domain;

import jakarta.validation.constraints.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 课程章节表 learning_course_chapter
 *
 * 章节是【可选】的：一个章节就是一段文字，下面挂若干视频。
 * 不用章节时，视频直接挂在课程下（learning_course_video.chapter_id 为空）。
 *
 * @author edu
 */
public class CourseChapter extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 章节ID */
    @Excel(name = "章节ID", cellType = ColumnType.NUMERIC)
    private Long chapterId;

    /** 所属课程ID */
    @Excel(name = "所属课程", cellType = ColumnType.NUMERIC)
    private Long courseId;

    /** 章节标题 */
    @Excel(name = "章节标题")
    private String chapterTitle;

    /** 章节内容（一段文字） */
    @Excel(name = "章节内容")
    private String chapterContent;

    /** 显示顺序 */
    @Excel(name = "显示顺序", cellType = ColumnType.NUMERIC)
    private Integer orderNum;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    public Long getChapterId()
    {
        return chapterId;
    }

    public void setChapterId(Long chapterId)
    {
        this.chapterId = chapterId;
    }

    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    @NotBlank(message = "章节标题不能为空")
    @Size(min = 0, max = 100, message = "章节标题长度不能超过100个字符")
    public String getChapterTitle()
    {
        return chapterTitle;
    }

    public void setChapterTitle(String chapterTitle)
    {
        this.chapterTitle = chapterTitle;
    }

    @Size(min = 0, max = 2000, message = "章节内容长度不能超过2000个字符")
    public String getChapterContent()
    {
        return chapterContent;
    }

    public void setChapterContent(String chapterContent)
    {
        this.chapterContent = chapterContent;
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

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("chapterId", getChapterId())
            .append("courseId", getCourseId())
            .append("chapterTitle", getChapterTitle())
            .append("chapterContent", getChapterContent())
            .append("orderNum", getOrderNum())
            .append("delFlag", getDelFlag())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
