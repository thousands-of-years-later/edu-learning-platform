package com.ruoyi.community.service;

import java.util.List;
import com.ruoyi.community.domain.Post;

/**
 * 社区帖子 服务层
 *
 * @author edu
 */
public interface IPostService
{
    /**
     * 查询帖子列表
     *
     * @param post 帖子信息（ownerId 由控制层强制赋值，控制可见性范围）
     * @return 帖子集合
     */
    public List<Post> selectPostList(Post post);

    /**
     * 根据帖子ID查询帖子（含正文）
     *
     * @param postId 帖子ID
     * @return 帖子信息
     */
    public Post selectPostById(Long postId);

    /**
     * 新增帖子
     *
     * @param post 帖子信息
     * @return 结果
     */
    public int insertPost(Post post);

    /**
     * 修改帖子
     *
     * 归属校验放在本层：非管理员只能改自己的帖子。
     *
     * @param post 帖子信息
     * @param currentUserId 当前登录用户ID
     * @param canManageAll 是否可管理全部帖子（超级管理员）
     * @return 结果
     */
    public int updatePost(Post post, Long currentUserId, boolean canManageAll);

    /**
     * 批量逻辑删除帖子
     *
     * 归属校验放在本层：非管理员只能删自己的帖子，批量删除逐个校验。
     *
     * @param ids 需要删除的帖子ID串（逗号分隔）
     * @param currentUserId 当前登录用户ID
     * @param canManageAll 是否可管理全部帖子（超级管理员）
     * @return 结果
     */
    public int deletePostByIds(String ids, Long currentUserId, boolean canManageAll);
}
