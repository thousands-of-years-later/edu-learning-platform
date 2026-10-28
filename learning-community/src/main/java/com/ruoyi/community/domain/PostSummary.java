package com.ruoyi.community.domain;

import java.io.Serializable;

/**
 * 「我的文章」列表项（我的主页用）
 *
 * 保留设计稿上显示的字段：
 *   18_我的主页 → 标题 + 「点赞数 · 收藏数」+ 右侧删除按钮
 * 另外返回 status / auditRemark：App 端发布后文章先落「待审核」，
 * 作者需要知道自己文章的审核状态；被驳回时还要能看到原因。
 * 分类、作者、评论数、时间仍不返回。
 *
 * 不用 Post 实体：Post 有 delFlag / ownerId / sortBy 这些内部字段，
 * 直接返回给客户端既多余又容易泄露。
 *
 * @author edu
 */
public class PostSummary implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long postId;

    private String postTitle;

    /** 状态（0已发布 1草稿 2已下架 3待审核 4已驳回） */
    private String status;

    /** 最近一次审核备注（被驳回时展示原因；其他状态通常为空） */
    private String auditRemark;

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

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getAuditRemark()
    {
        return auditRemark;
    }

    public void setAuditRemark(String auditRemark)
    {
        this.auditRemark = auditRemark;
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
