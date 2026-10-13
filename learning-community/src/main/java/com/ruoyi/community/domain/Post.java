package com.ruoyi.community.domain;

import jakarta.validation.constraints.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 社区帖子表 learning_post
 *
 * 帖子是社区模块的内容主体，挂在社区类别（learning_category.category_type = 'article'）下。
 * 正文是 Markdown 原文，图片以 ![](/profile/...) 相对路径内联，没有视频。
 * 点赞、收藏、评论与 course 一致：只做计数展示（三个 int 列），不建「每个用户一条记录」的关联表。
 *
 * @author edu
 */
public class Post extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：已发布（所有人可见） */
    public static final String STATUS_PUBLISHED = "0";

    /** 状态：草稿（仅作者与管理员可见） */
    public static final String STATUS_DRAFT = "1";

    /** 状态：已下架（仅管理员可见、可恢复） */
    public static final String STATUS_OFFLINE = "2";

    /** 帖子ID */
    @Excel(name = "帖子ID", cellType = ColumnType.NUMERIC)
    private Long postId;

    /** 帖子标题 */
    @Excel(name = "帖子标题")
    private String postTitle;

    /** 所属类别ID（逻辑关联 learning_category.category_id） */
    private Long categoryId;

    /** 作者用户ID（逻辑关联 sys_user.user_id，归属校验的依据） */
    private Long userId;

    /** 正文（Markdown 原文）。刻意不加 @Excel：内容可超 Excel 单元格 32767 字符上限 */
    private String content;

    /** 点赞数 */
    @Excel(name = "点赞数", cellType = ColumnType.NUMERIC)
    private Integer likeCount;

    /** 收藏数 */
    @Excel(name = "收藏数", cellType = ColumnType.NUMERIC)
    private Integer favoriteCount;

    /** 评论数 */
    @Excel(name = "评论数", cellType = ColumnType.NUMERIC)
    private Integer commentCount;

    /** 状态（0已发布 1草稿 2已下架） */
    @Excel(name = "状态", readConverterExp = "0=已发布,1=草稿,2=已下架")
    private String status;

    /** 删除标志（0代表存在 2代表删除） */
    private String delFlag;

    /** 所属类别名称（关联 learning_category 查出，非本表列） */
    @Excel(name = "所属类别")
    private String categoryName;

    /** 作者昵称（关联 sys_user 查出，非本表列） */
    @Excel(name = "作者")
    private String userName;

    /**
     * 列表可见性范围（非本表列，仅作查询条件使用）
     *
     * 非管理员由控制器强制赋值为当前登录用户ID，查到的是「所有人已发布的 + 自己的全部（含草稿）」；
     * 管理员为 null，不限制。绝不能用 userId 当列表过滤条件，那个字段前端可以绑。
     */
    private Long ownerId;

    public Long getPostId()
    {
        return postId;
    }

    public void setPostId(Long postId)
    {
        this.postId = postId;
    }

    @NotBlank(message = "帖子标题不能为空")
    @Size(min = 0, max = 200, message = "帖子标题长度不能超过200个字符")
    public String getPostTitle()
    {
        return postTitle;
    }

    public void setPostTitle(String postTitle)
    {
        this.postTitle = postTitle;
    }

    public Long getCategoryId()
    {
        return categoryId;
    }

    public void setCategoryId(Long categoryId)
    {
        this.categoryId = categoryId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    // 正文不能加 @NotBlank：草稿允许空正文，「发布时正文必填」由控制器判断
    @Size(min = 0, max = 65535, message = "帖子内容长度不能超过65535个字符")
    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
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

    public String getCategoryName()
    {
        return categoryName;
    }

    public void setCategoryName(String categoryName)
    {
        this.categoryName = categoryName;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public Long getOwnerId()
    {
        return ownerId;
    }

    public void setOwnerId(Long ownerId)
    {
        this.ownerId = ownerId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("postId", getPostId())
            .append("postTitle", getPostTitle())
            .append("categoryId", getCategoryId())
            .append("userId", getUserId())
            .append("likeCount", getLikeCount())
            .append("favoriteCount", getFavoriteCount())
            .append("commentCount", getCommentCount())
            .append("status", getStatus())
            .append("delFlag", getDelFlag())
            .append("categoryName", getCategoryName())
            .append("userName", getUserName())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
