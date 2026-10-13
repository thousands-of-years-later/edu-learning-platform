package com.ruoyi.community.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.community.domain.Post;
import com.ruoyi.community.mapper.PostMapper;
import com.ruoyi.community.service.IPostService;

/**
 * 社区帖子 服务层处理
 *
 * 帖子与课程最大的不同：普通用户也能增删改，所以「只能动自己的帖子」这条硬性要求
 * 收敛到本层统一校验（控制器只管权限注解，不重复判断归属）。
 *
 * @author edu
 */
@Service
public class PostServiceImpl implements IPostService
{
    @Autowired
    private PostMapper postMapper;

    /**
     * 查询帖子列表
     *
     * @param post 帖子信息
     * @return 帖子集合
     */
    @Override
    public List<Post> selectPostList(Post post)
    {
        return postMapper.selectPostList(post);
    }

    /**
     * 根据帖子ID查询帖子
     *
     * @param postId 帖子ID
     * @return 帖子信息
     */
    @Override
    public Post selectPostById(Long postId)
    {
        return postMapper.selectPostById(postId);
    }

    /**
     * 新增帖子
     *
     * @param post 帖子信息
     * @return 结果
     */
    @Override
    public int insertPost(Post post)
    {
        post.setPostTitle(trimPostTitle(post.getPostTitle()));
        // 新增只允许「已发布 / 草稿」，提交其余值（含直接建一条已下架的帖子）一律落草稿。
        // 下架是管理动作，走编辑接口由管理员触发。
        if (!Post.STATUS_PUBLISHED.equals(post.getStatus()))
        {
            post.setStatus(Post.STATUS_DRAFT);
        }
        checkContentRequired(post.getStatus(), post.getContent());
        return postMapper.insertPost(post);
    }

    /**
     * 修改帖子
     *
     * @param post 帖子信息
     * @param currentUserId 当前登录用户ID
     * @param canManageAll 是否可管理全部帖子（超级管理员）
     * @return 结果
     */
    @Override
    public int updatePost(Post post, Long currentUserId, boolean canManageAll)
    {
        post.setPostTitle(trimPostTitle(post.getPostTitle()));
        Post old = postMapper.selectPostById(post.getPostId());
        if (StringUtils.isNull(old))
        {
            throw new ServiceException("帖子不存在或已被删除");
        }
        if (!canManageAll && !old.getUserId().equals(currentUserId))
        {
            throw new ServiceException("只能修改自己发布的帖子");
        }
        // 作者归属不可变：一律以库里的为准，防止表单伪造 userId / createBy 把帖子改挂到别人名下
        post.setUserId(old.getUserId());
        post.setCreateBy(old.getCreateBy());
        post.setStatus(resolveStatus(post.getStatus(), old.getStatus(), canManageAll));
        // 表单没提交正文时（content 为 null）沿用库里的，再判断发布态是否为空
        checkContentRequired(post.getStatus(), post.getContent() != null ? post.getContent() : old.getContent());
        return postMapper.updatePost(post);
    }

    /**
     * 批量逻辑删除帖子
     *
     * @param ids 需要删除的帖子ID串（逗号分隔）
     * @param currentUserId 当前登录用户ID
     * @param canManageAll 是否可管理全部帖子（超级管理员）
     * @return 结果
     */
    @Override
    @Transactional
    public int deletePostByIds(String ids, Long currentUserId, boolean canManageAll)
    {
        Long[] postIds = Convert.toLongArray(ids);
        // 空串会被 Convert 转成空数组，<foreach> 将拼出 in () 造成 SQL 语法错误，必须拦截
        if (postIds == null || postIds.length == 0)
        {
            throw new ServiceException("未选择要删除的帖子数据");
        }
        for (Long postId : postIds)
        {
            Post post = postMapper.selectPostById(postId);
            if (StringUtils.isNull(post))
            {
                throw new ServiceException(String.format("帖子ID[%s]不存在或已被删除", postId));
            }
            // 批量删除要校验每一个 id，不能只看第一个
            if (!canManageAll && !post.getUserId().equals(currentUserId))
            {
                throw new ServiceException(String.format("帖子[%s]不是本人发布，不能删除", post.getPostTitle()));
            }
        }
        return postMapper.deletePostByIds(postIds);
    }

    /**
     * 状态收敛
     *
     * 管理员：可选 已发布 / 草稿 / 已下架，提交非法值则沿用原状态。
     * 非管理员：只能选 已发布 / 草稿；且原状态是「已下架」时强制保持已下架，
     * 否则用户能自己把管理员下架的帖子重新发布出去。下架的解除只有管理员能做。
     *
     * @param submitted 表单提交的状态
     * @param current 库里的原状态
     * @param canManageAll 是否可管理全部帖子
     * @return 最终写入的状态
     */
    private String resolveStatus(String submitted, String current, boolean canManageAll)
    {
        if (Post.STATUS_OFFLINE.equals(current) && !canManageAll)
        {
            return Post.STATUS_OFFLINE;
        }
        boolean allowed = Post.STATUS_PUBLISHED.equals(submitted)
                || Post.STATUS_DRAFT.equals(submitted)
                || (canManageAll && Post.STATUS_OFFLINE.equals(submitted));
        return allowed ? submitted : current;
    }

    /**
     * 发布态必须有正文
     *
     * 正文不能加 @NotBlank（那样草稿存不下去），所以在这里按最终状态判断：
     * 草稿允许正文为空，已发布 / 已下架必须有内容。
     *
     * @param status 最终写入的状态
     * @param content 正文
     */
    private void checkContentRequired(String status, String content)
    {
        if (!Post.STATUS_DRAFT.equals(status) && StringUtils.isEmpty(content))
        {
            throw new ServiceException("帖子内容不能为空");
        }
    }

    /**
     * 标题去空白，并兜底拦截空值
     *
     * @param postTitle 帖子标题
     * @return 处理后的标题
     */
    private String trimPostTitle(String postTitle)
    {
        String title = StringUtils.trim(postTitle);
        if (StringUtils.isEmpty(title))
        {
            throw new ServiceException("帖子标题不能为空");
        }
        return title;
    }
}
