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
     * 批量逻辑删除帖子
     *
     * @param postIds 需要删除的帖子ID数组
     * @return 结果
     */
    public int deletePostByIds(Long[] postIds);
}
