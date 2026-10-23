package com.ruoyi.community.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.community.domain.PostDetail;
import com.ruoyi.community.domain.PostSummary;

/**
 * 客户端「我的文章 / 文章详情」查询
 *
 * 与后台 PostMapper 分开：
 *   后台列表的可见性靠 ownerId 做"越权隔离"，客户端要的是【严格按作者 + 只含已发布】，
 *   混用会互相牵制。写操作仍复用 PostServiceImpl（它已经带作者校验与级联清理）。
 *
 * 返回的是精简 DTO（PostSummary / PostDetail），不是 Post 实体：
 *   Post 里的 delFlag / auditRemark / ownerId / sortBy 不该出现在客户端响应里。
 *
 * @author edu
 */
public interface MyPostMapper
{
    /**
     * 我的已发布文章（设计稿 18_我的主页「我的文章」）
     *
     * "已发布"是查询条件（status='0'），不作为展示字段返回。
     * 按发布时间倒序，post_id 兜底保证分页稳定。
     *
     * @param userId 我的用户ID
     * @return 只含 postId / postTitle / likeCount / favoriteCount
     */
    public List<PostSummary> selectMyPublishedList(@Param("userId") Long userId);

    /**
     * 文章详情（设计稿 16_文章详情）
     *
     * 只返回已发布且未删除的文章；草稿/待审核/已下架的返回 null，
     * 由服务层统一报"文章不存在或已下架"（不给客户端探测非公开文章的机会）。
     *
     * @param postId 文章ID
     */
    public PostDetail selectPublishedById(@Param("postId") Long postId);
}
