package com.ruoyi.community.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 文章详情（设计稿 16_文章详情）
 *
 * 字段严格对齐设计稿上显示的东西：
 *   大标题
 *   作者行：头像 + 昵称 + 发布时间，右侧「技术分享」这类分类标签
 *   正文（Markdown 原文，前端渲染）
 *   底部：点赞数、评论数（收藏按钮没有数字，收藏状态由 /interaction/status 单独查）
 *
 * 不返回 delFlag / auditRemark / ownerId / sortBy / status 这些内部字段。
 * 话题标签（#Android #Kotlin #Compose）暂时没有：库里没有任何话题表，等做话题页时再补。
 *
 * @author edu
 */
public class PostDetail implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long postId;

    private String postTitle;

    /** 正文（Markdown 原文） */
    private String content;

    private Long userId;

    /** 作者昵称 */
    private String userName;

    /** 作者头像，可能为 null（前端用昵称首字兜底） */
    private String avatar;

    /** 所属分类名（设计稿右上角"技术分享"那个标签） */
    private String categoryName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 点赞数 */
    private Integer likeCount;

    /** 评论数 */
    private Integer commentCount;

    public Long getPostId()
    {
        return postId;
    }

    public void setPostId(Long postId)
    {
        this.postId = postId;
    }

    public String getPostTitle()
    {
        return postTitle;
    }

    public void setPostTitle(String postTitle)
    {
        this.postTitle = postTitle;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getAvatar()
    {
        return avatar;
    }

    public void setAvatar(String avatar)
    {
        this.avatar = avatar;
    }

    public String getCategoryName()
    {
        return categoryName;
    }

    public void setCategoryName(String categoryName)
    {
        this.categoryName = categoryName;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Integer getLikeCount()
    {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount)
    {
        this.likeCount = likeCount;
    }

    public Integer getCommentCount()
    {
        return commentCount;
    }

    public void setCommentCount(Integer commentCount)
    {
        this.commentCount = commentCount;
    }
}
