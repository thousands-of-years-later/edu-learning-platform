package com.ruoyi.course.domain;

import jakarta.validation.constraints.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 课程表 learning_course
 *
 * 课程是学习平台的内容主体：一个课程下有若干章节（可选）和若干视频。
 * 点赞、收藏、评论本阶段只做计数展示（三个 int 列），不建「每个用户一条记录」的关联表。
 *
 * @author edu
 */
public class Course extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 排序方式：按点赞数（learning_course.like_count） */
    public static final String SORT_BY_LIKE_COUNT = "likeCount";

    /** 排序方式：按上传日期（learning_course.create_time） */
    public static final String SORT_BY_CREATE_TIME = "createTime";

    /** 排序方式：按综合评分（平均分 = rating_score / rating_count，无人评分排最后） */
    public static final String SORT_BY_AVG_SCORE = "avgScore";

    /** 默认排序方式：上传日期倒序 */
    public static final String SORT_BY_DEFAULT = SORT_BY_CREATE_TIME;

    /** 课程ID */
    @Excel(name = "课程ID", cellType = ColumnType.NUMERIC)
    private Long courseId;

    /** 课程名称 */
    @Excel(name = "课程名称")
    private String courseName;

    /** 所属类别ID（逻辑关联 learning_category.category_id） */
    @Excel(name = "所属类别", cellType = ColumnType.NUMERIC)
    private Long categoryId;

    /** 封面图片地址 */
    private String coverUrl;

    /** 课程简介 */
    @Excel(name = "课程简介")
    private String intro;

    /** 点赞数 */
    @Excel(name = "点赞数", cellType = ColumnType.NUMERIC)
    private Integer likeCount;

    /** 收藏数 */
    @Excel(name = "收藏数", cellType = ColumnType.NUMERIC)
    private Integer favoriteCount;

    /** 评论数 */
    @Excel(name = "评论数", cellType = ColumnType.NUMERIC)
    private Integer commentCount;

    /** 阅读数（首次观看时 +1，由观看历史维护；客户端列表展示用，不进后台导出） */
    private Integer viewCount;

    /** 评分人数（冗余统计，由课程评分模块维护，后台表单不写入） */
    @Excel(name = "评分人数", cellType = ColumnType.NUMERIC)
    private Integer ratingCount;

    /** 评分总分（各用户评分之和，平均分 = ratingScore / ratingCount；冗余统计，由课程评分模块维护） */
    @Excel(name = "评分总分", cellType = ColumnType.NUMERIC)
    private Integer ratingScore;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    /** 所属类别名称（关联 learning_category 查出，非本表列） */
    private String categoryName;

    /** 排序方式（App 列表入参，非本表列；由 Mapper 白名单 <choose> 翻译成 ORDER BY） */
    private String sortBy;

    /** 综合评分（平均分 = ratingScore / ratingCount，保留 1 位小数；无人评分返回 null，非本表列） */
    private Double avgScore;

    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    @NotBlank(message = "课程名称不能为空")
    @Size(min = 0, max = 100, message = "课程名称长度不能超过100个字符")
    public String getCourseName()
    {
        return courseName;
    }

    public void setCourseName(String courseName)
    {
        this.courseName = courseName;
    }

    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
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

    @Size(min = 0, max = 1000, message = "课程简介长度不能超过1000个字符")
    public String getIntro()
    {
        return intro;
    }

    public void setIntro(String intro)
    {
        this.intro = intro;
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

    @Min(value = 0, message = "阅读数不能为负数")
    public Integer getViewCount()
    {
        return viewCount;
    }

    public void setViewCount(Integer viewCount)
    {
        this.viewCount = viewCount;
    }

    @Min(value = 0, message = "评分人数不能为负数")
    public Integer getRatingCount()
    {
        return ratingCount;
    }

    public void setRatingCount(Integer ratingCount)
    {
        this.ratingCount = ratingCount;
    }

    @Min(value = 0, message = "评分总分不能为负数")
    public Integer getRatingScore()
    {
        return ratingScore;
    }

    public void setRatingScore(Integer ratingScore)
    {
        this.ratingScore = ratingScore;
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

    public String getCategoryName()
    {
        return categoryName;
    }

    public void setCategoryName(String categoryName)
    {
        this.categoryName = categoryName;
    }

    public String getSortBy()
    {
        return sortBy;
    }

    public void setSortBy(String sortBy)
    {
        this.sortBy = sortBy;
    }

    public Double getAvgScore()
    {
        return avgScore;
    }

    public void setAvgScore(Double avgScore)
    {
        this.avgScore = avgScore;
    }

    /** 排序方式是否为白名单内的合法值（null / 空串由调用方决定是否套默认值） */
    public static boolean isValidSortBy(String sortBy)
    {
        return SORT_BY_LIKE_COUNT.equals(sortBy)
                || SORT_BY_CREATE_TIME.equals(sortBy)
                || SORT_BY_AVG_SCORE.equals(sortBy);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("courseId", getCourseId())
            .append("courseName", getCourseName())
            .append("categoryId", getCategoryId())
            .append("coverUrl", getCoverUrl())
            .append("intro", getIntro())
            .append("likeCount", getLikeCount())
            .append("favoriteCount", getFavoriteCount())
            .append("commentCount", getCommentCount())
            .append("viewCount", getViewCount())
            .append("ratingCount", getRatingCount())
            .append("ratingScore", getRatingScore())
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
