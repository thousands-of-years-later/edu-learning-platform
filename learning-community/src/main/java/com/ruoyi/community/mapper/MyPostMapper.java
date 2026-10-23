package com.ruoyi.community.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.community.domain.Post;

/**
 * 客户端「我的文章 / 文章详情」查询
 *
 * 与后台 PostMapper 分开：
 *   后台列表的可见性靠 ownerId 做"越权隔离"（非管理员也能看到全部已发布），
 *   而客户端要的是【严格按作者 + 只含已发布】的列表，混用会互相牵制。
 * 只做只读查询与"我的已发布文章"，写操作仍复用 PostServiceImpl（它已经带作者校验与级联清理）。
 *
 * @author edu
 */
public interface MyPostMapper
{
    /**
     * 我的已发布文章（客户端「我的主页」用，分页由上层 PageHelper 驱动）
     *
     * 只返回 status='0'（已发布）且未删除的；草稿/待审核/已下架不出现。
     * 按发布时间倒序，post_id 兜底保证分页稳定。
     */
    public List<Post> selectMyPublishedList(@Param("userId") Long userId);

    /**
     * 文章详情：只返回已发布且未删除的文章
     *
     * 未发布/已下架的返回 null，由服务层统一报"文章不存在或已下架"
     * （不给客户端探测草稿/待审核文章的机会）。
     */
    public Post selectPublishedById(@Param("postId") Long postId);
}
