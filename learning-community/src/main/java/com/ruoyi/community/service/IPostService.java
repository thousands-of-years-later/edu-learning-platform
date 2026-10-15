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
     * 非管理员提交「发布」不是直接发布，而是落「3 待审核」等管理员审核；
     * 管理员（可管理全部）提交「发布」才直接发布。
     *
     * @param post 帖子信息
     * @param canManageAll 是否可管理全部帖子（超级管理员）
     * @return 结果
     */
    public int insertPost(Post post, boolean canManageAll);

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
     * 审核帖子（仅审核人）
     *
     * 只有当前状态为「3 待审核」的帖子能被审核；结果只能是 0 通过 / 4 驳回。
     * 驳回强制取消精华；审核备注以本次提交为准（留空即清空）。
     *
     * @param post 帖子信息（postId、status=0/4、isFeatured、auditRemark、updateBy）
     * @param canAudit 是否具备审核权限（超级管理员或持有 community:post:audit）
     * @return 结果
     */
    public int auditPost(Post post, boolean canAudit);

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
