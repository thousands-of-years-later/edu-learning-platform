package com.ruoyi.community.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 「社区 / 分类」文章列表项
 *
 * 设计稿上的文章卡片要显示：标题 + 作者（头像/昵称）+ 发布时间 + 阅读数 + 评论数 + 点赞数。
 * 收藏数保留，兼容旧客户端；isFeatured 用于在列表里标记精华文章。
 * 三种排序（精华按时间 / 全部按点赞 / 全部按时间）共用本返回结构，
 * 所以 createTime、likeCount、viewCount 三个字段在任何排序下都返回。
 *
 * 为什么不复用 PostSummary：
 *   PostSummary 是「我的文章」列表（设计稿 18_我的主页）的返回类型，
 *   那个页面只显示标题 + 点赞数 + 收藏数（外加删除按钮），
 *   多返回作者资料与统计字段会让两个接口的契约互相牵制。
 *
 * 作者昵称取 sys_user.user_name（本系统没有独立的 nick_name 列），
 * 头像取 sys_user.avatar（未设置时由 Mapper 归一成 null）。
 *
 * @author edu
 */
public class PostListSummary implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long postId;

    private String postTitle;

    /** 作者昵称 */
    private String userName;

    /** 作者头像，可能为 null（前端用昵称首字兜底） */
    private String avatar;

    /** 精华标志（0否 1是），客户端用来在列表里打「精华」标签 */
    private String isFeatured;

    /** 发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 阅读数（首次阅读时 +1，由观看历史维护） */
    private Integer viewCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 收藏数 */
    private Integer favoriteCount;

    /** 评论数（按评论表实时 count(*)） */
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

    public String getIsFeatured()
    {
        return isFeatured;
    }

    public void setIsFeatured(String isFeatured)
    {
        this.isFeatured = isFeatured;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Integer getViewCount()
    {
        return viewCount;
    }

    public void setViewCount(Integer viewCount)
    {
        this.viewCount = viewCount;
    }

    public Integer getLikeCount()
    {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount)
    {
        this.likeCount = likeCount;
    }

    public Integer getFavoriteCount()
    {
        return favoriteCount;
    }

    public void setFavoriteCount(Integer favoriteCount)
    {
        this.favoriteCount = favoriteCount;
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
