package com.ruoyi.course.domain;

import jakarta.validation.constraints.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 课程轮播图 learning_banner
 *
 * 每个课程类别各有一个轮播窗口，另有一个独立的「全部」窗口：
 * 「全部」用 categoryId = 0（ALL_CATEGORY_ID）表示，是单独维护的一批数据，
 * 不是各类别的汇总；其余取值逻辑关联 learning_category.category_id。
 * 一条轮播只属于一个类别，点击只跳课程（course_id 逻辑关联 learning_course.course_id）。
 *
 * categoryName / courseName 是仅供展示的只读字段，由 Mapper 的标量子查询带出，
 * 其中 categoryId = 0 时 categoryName 由服务层填成「全部」。
 *
 * 注意：类名与 org.springframework.boot.Banner 重名，引用时不要用通配 import。
 *
 * @author edu
 */
public class Banner extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 「全部」窗口的类别ID：不在 learning_category 里，是约定值 */
    public static final Long ALL_CATEGORY_ID = 0L;

    /** 「全部」窗口在后台列表/导出里的展示名称 */
    public static final String ALL_CATEGORY_NAME = "全部";

    /** 轮播图ID */
    @Excel(name = "轮播图ID", cellType = ColumnType.NUMERIC)
    private Long bannerId;

    /** 所属类别ID（0=全部；其余逻辑关联 learning_category.category_id） */
    @Excel(name = "所属类别ID", cellType = ColumnType.NUMERIC)
    private Long categoryId;

    /** 所属类别名称（仅展示用，categoryId=0 时为「全部」） */
    @Excel(name = "所属类别")
    private String categoryName;

    /** 轮播标题 */
    @Excel(name = "标题")
    private String title;

    /** 轮播图片地址（上传返回的相对路径 /profile/...） */
    @Excel(name = "图片地址")
    private String imageUrl;

    /** 点击跳转的课程ID（逻辑关联 learning_course.course_id） */
    @Excel(name = "跳转课程ID", cellType = ColumnType.NUMERIC)
    private Long courseId;

    /** 跳转课程名称（仅展示用） */
    @Excel(name = "跳转课程")
    private String courseName;

    /** 显示顺序 */
    @Excel(name = "显示顺序", cellType = ColumnType.NUMERIC)
    private Integer orderNum;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    public Long getBannerId()
    {
        return bannerId;
    }

    public void setBannerId(Long bannerId)
    {
        this.bannerId = bannerId;
    }

    /**
     * 所属类别ID；0 表示「全部」窗口，所以只校验非空、不校验正数
     */
    @NotNull(message = "所属类别不能为空")
    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
    }

    public String getCategoryName()
    {
        return categoryName;
    }

    public void setCategoryName(String categoryName)
    {
        this.categoryName = categoryName;
    }

    @NotBlank(message = "轮播标题不能为空")
    @Size(min = 0, max = 100, message = "轮播标题长度不能超过100个字符")
    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    @NotBlank(message = "轮播图片不能为空")
    @Size(min = 0, max = 500, message = "轮播图片地址长度不能超过500个字符")
    public String getImageUrl()
    {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl)
    {
        this.imageUrl = imageUrl;
    }

    /**
     * 跳转课程ID；轮播的点击目标只有课程一种，必填
     */
    @NotNull(message = "跳转课程不能为空")
    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    public String getCourseName()
    {
        return courseName;
    }

    public void setCourseName(String courseName)
    {
        this.courseName = courseName;
    }

    @Min(value = 0, message = "显示顺序不能小于0")
    public Integer getOrderNum()
    {
        return orderNum;
    }

    public void setOrderNum(Integer orderNum)
    {
        this.orderNum = orderNum;
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

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("bannerId", getBannerId())
            .append("categoryId", getCategoryId())
            .append("categoryName", getCategoryName())
            .append("title", getTitle())
            .append("imageUrl", getImageUrl())
            .append("courseId", getCourseId())
            .append("courseName", getCourseName())
            .append("orderNum", getOrderNum())
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
