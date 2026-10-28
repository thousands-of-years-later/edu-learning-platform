package com.ruoyi.community.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.community.domain.PostDetail;
import com.ruoyi.community.domain.PostListSummary;
import com.ruoyi.community.domain.PostSummary;

/**
 * 客户端「文章列表 / 我的文章 / 文章详情」查询
 *
 * 与后台 PostMapper 分开：
 *   后台列表的可见性靠 ownerId 做"越权隔离"，客户端要的是【严格按作者】，
 *   混用会互相牵制。写操作仍复用 PostServiceImpl（它已经带作者校验与级联清理）。
 *
 * 返回的是精简 DTO（PostSummary / PostListSummary / PostDetail），不是 Post 实体：
 *   Post 里的 delFlag / auditRemark / ownerId / sortBy 不该出现在客户端响应里。
 *
 * @author edu
 */
public interface MyPostMapper
{
    /**
     * 我的文章（设计稿 18_我的主页「我的文章」）
     *
     * 返回自己的全部未删除文章（含草稿、待审核、已驳回、已下架），
     * 客户端据 status 展示「审核中 / 已驳回」等状态。
     * 按发布时间倒序，post_id 兜底保证分页稳定。
     *
     * @param userId 我的用户ID
     * @return postId / postTitle / status / auditRemark / likeCount / favoriteCount
     */
    public List<PostSummary> selectMyList(@Param("userId") Long userId);

    /**
     * 已发布文章列表（社区首页 / 分类页）
     *
     * 只查已发布（status='0'）且未删除（del_flag='0'）的文章，按发布时间倒序，
     * post_id 兜底保证分页稳定。
     *
     * @param categoryId 类别ID；为 null 时查全部分类
     * @return 含 postId / postTitle / userName / avatar / viewCount /
     *         likeCount / favoriteCount / commentCount
     */
    public List<PostListSummary> selectPublishedList(@Param("categoryId") Long categoryId);

    /**
     * 文章详情（设计稿 16_文章详情）
     *
     * 已发布且未删除的文章所有人可见；作者还可以看到自己的草稿/待审核/已驳回/已下架文章。
     * 其他用户查非公开文章返回 null，由控制器统一报"文章不存在或已下架"
     * （不给客户端探测非公开文章的机会）。
     *
     * @param postId 文章ID
     * @param viewerId 当前登录用户ID（只用于判断是否作者本人）
     */
    public PostDetail selectVisibleById(@Param("postId") Long postId, @Param("viewerId") Long viewerId);
}
