package com.ruoyi.community.mapper;

import java.util.List;
import com.ruoyi.community.domain.Post;

/**
 * 社区帖子 数据层
 *
 * @author edu
 */
public interface PostMapper
{
    /**
     * 查询帖子列表（不含正文，避免列表与导出把 longtext 拖出来）
     *
     * @param post 帖子信息（ownerId 为可见性范围，由控制层强制赋值）
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
     * 刻意不更新 user_id / create_by / create_time，作者归属不可通过编辑接口变更。
     *
     * @param post 帖子信息
     * @return 结果
     */
    public int updatePost(Post post);

    /**
     * 审核帖子（只更新 状态 / 精华 / 审核备注 三列，不动标题、正文、类别与作者）
     *
     * where 带 status = '3'：只有待审核的帖子能被审核，并发重复审核时影响行数为 0。
     *
     * @param post 帖子信息（postId、status、isFeatured、auditRemark、updateBy 必填）
     * @return 结果
     */
    public int updatePostAudit(Post post);

    /**
     * 批量逻辑删除帖子，并清零 like_count / favorite_count / comment_count
     *
     * @param postIds 需要删除的帖子ID数组
     * @return 结果
     */
    public int deletePostByIds(Long[] postIds);

    /**
     * 删除指向这些帖子的点赞收藏（级联清理，硬删除）
     *
     * learning_interaction 在 learning-interation 模块，按 CategoryMapper.countPostByCategoryId
     * 的先例直接写纯 SQL；该表没有 del_flag（取消点赞即硬删除），这里同样是硬删除。
     * target_type 必须带上：三种目标的 ID 各自独立编号，只按 target_id 过滤会串到别的类型上。
     *
     * @param postIds 被删除的帖子ID数组
     * @return 结果
     */
    public int deleteInteractionByPostIds(Long[] postIds);

    /**
     * 逻辑删除指向这些帖子的评论（级联清理）
     *
     * 同上走纯 SQL。learning_comment 与内容表一致用 del_flag 软删除，
     * where 带 del_flag = '0'，重复执行不会反复改写 update_time。
     *
     * @param postIds 被删除的帖子ID数组
     * @return 结果
     */
    public int softDeleteCommentByPostIds(Long[] postIds);
}
