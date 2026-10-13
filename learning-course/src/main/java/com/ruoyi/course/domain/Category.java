package com.ruoyi.course.domain;

import jakarta.validation.constraints.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 类别表 learning_category
 *
 * 课程和社区文章共用一张类别表，通过 category_type 区分归属模块：
 * 课程只能看到/维护 category_type = 'course' 的数据，社区文章只能看到 'article' 的。
 *
 * @author edu
 */
public class Category extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 类别类型：课程 */
    public static final String TYPE_COURSE = "course";

    /** 类别类型：社区文章 */
    public static final String TYPE_ARTICLE = "article";

    /** 类别类型合法值（编译期常量，供 @Pattern 使用） */
    public static final String TYPE_PATTERN = "^(" + TYPE_COURSE + "|" + TYPE_ARTICLE + ")$";

    /** 类别ID */
    @Excel(name = "类别ID", cellType = ColumnType.NUMERIC)
    private Long categoryId;

    /** 类别名称 */
    @Excel(name = "类别名称")
    private String categoryName;

    /** 类别类型（course课程 article社区文章） */
    @Excel(name = "类别类型", dictType = "sys_category_type")
    private String categoryType;

    /** 显示顺序 */
    @Excel(name = "显示顺序", cellType = ColumnType.NUMERIC)
    private Integer orderNum;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
    }

    @NotBlank(message = "类别名称不能为空")
    @Size(min = 0, max = 50, message = "类别名称长度不能超过50个字符")
    public String getCategoryName()
    {
        return categoryName;
    }

    public void setCategoryName(String categoryName)
    {
        this.categoryName = categoryName;
    }

    @NotBlank(message = "类别类型不能为空")
    @Pattern(regexp = TYPE_PATTERN, message = "类别类型不合法（仅支持 course/article）")
    public String getCategoryType()
    {
        return categoryType;
    }

    public void setCategoryType(String categoryType)
    {
        this.categoryType = categoryType;
    }

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
            .append("categoryId", getCategoryId())
            .append("categoryName", getCategoryName())
            .append("categoryType", getCategoryType())
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
