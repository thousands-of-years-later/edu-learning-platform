package com.ruoyi.community.domain;

import java.io.Serializable;

/**
 * 「我的文章」列表项（我的主页用）
 *
 * 只保留设计稿上真正显示的字段：
 *   18_我的主页 → 标题 + 「点赞数 · 收藏数」+ 右侧删除按钮
 * 状态、分类、作者、评论数、时间在列表上都不显示，所以不返回（"已发布"是查询条件，不是展示字段）。
 *
 * 不用 Post 实体：Post 有 delFlag / auditRemark / ownerId / sortBy 这些内部字段，
 * 直接返回给客户端既多余又容易泄露。
 *
 * @author edu
 */
public class PostSummary implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long postId;

    private String postTitle;

    /** 点赞数 */
    private Integer likeCount;

    /** 收藏数 */
    private Integer favoriteCount;

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
}
