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
     * @param canManageAll 是否可管理全部帖子（超级管理员）
     * @return 结果
     */
    @Override
    public int insertPost(Post post, boolean canManageAll)
    {
        post.setPostTitle(trimPostTitle(post.getPostTitle()));
        // 新增只允许「已发布 / 草稿」，提交其余值（含直接建一条已下架/待审核的帖子）一律落草稿。
        // 下架、驳回都是管理动作，走编辑/审核接口触发。
        if (Post.STATUS_PUBLISHED.equals(post.getStatus()))
        {
            // 管理员选「发布」直接发布；普通用户选「发布」只是送审（落 3 待审核）
            post.setStatus(canManageAll ? Post.STATUS_PUBLISHED : Post.STATUS_PENDING);
        }
        else
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
     * 批量逻辑删除帖子，并级联清理挂在它上面的点赞收藏与评论
     *
     * 先删内容、后清子表：删除语句把 del_flag='2' 与计数清零写在同一句，帖子先落删除态后，
     * 并发的点赞/评论会被 existsPost 的 del_flag='0' 拦住，或让 updateCount 影响 0 行而回滚。
     * 反过来先清子表的话，中间窗口里提交的互动会永远留在库里。
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
        // 1、软删帖子并清零三个计数（同一句 SQL）
        int rows = postMapper.deletePostByIds(postIds);
        // 2、清掉挂在帖子上的点赞收藏（硬删除）与评论（软删除）
        postMapper.deleteInteractionByPostIds(postIds);
        postMapper.softDeleteCommentByPostIds(postIds);
        return rows;
    }

    /**
     * 状态收敛
     *
     * 管理员：可选 已发布 / 草稿 / 已下架，提交非法值（含伪造的 3/4）则沿用原状态。
     * 非管理员：
     *   · 原状态是「已下架」时强制保持已下架，否则用户能自己把管理员下架的帖子重新发布出去；
     *   · 提交「已发布」一律落「3 待审核」——新建送审、驳回(4)后重提、已发布(0)改动重提
     *     都走这一条（审核链路闭环，不允许绕过审核直接发布）；
     *   · 提交「草稿」从任何状态（含 3/4，撤回重写）落到「1 草稿」；
     *   · 其余（伪造的 2/3/4）一律沿用原状态，不给自助下架、自助送审、自助驳回的口子。
     *
     * @param submitted 表单提交的状态
     * @param current 库里的原状态
     * @param canManageAll 是否可管理全部帖子
     * @return 最终写入的状态
     */
    private String resolveStatus(String submitted, String current, boolean canManageAll)
    {
        if (canManageAll)
        {
            boolean allowed = Post.STATUS_PUBLISHED.equals(submitted)
                    || Post.STATUS_DRAFT.equals(submitted)
                    || Post.STATUS_OFFLINE.equals(submitted);
            return allowed ? submitted : current;
        }
        if (Post.STATUS_OFFLINE.equals(current))
        {
            return Post.STATUS_OFFLINE;
        }
        if (Post.STATUS_PUBLISHED.equals(submitted))
        {
            return Post.STATUS_PENDING;
        }
        if (Post.STATUS_DRAFT.equals(submitted))
        {
            return Post.STATUS_DRAFT;
        }
        return current;
    }

    /**
     * 审核帖子（仅审核人）
     *
     * 只允许审核「3 待审核」的帖子；结果只能是通过(0)或驳回(4)。
     * 只组装审核要写的字段，标题/正文/类别/作者一概不经过这条链路。
     * 不加 @Transactional：单条 update，且 mapper 的 where 带 status='3' 已做原子兜底。
     *
     * @param post 帖子信息（postId、status、isFeatured、auditRemark、updateBy）
     * @param canAudit 是否具备审核权限
     * @return 结果
     */
    @Override
    public int auditPost(Post post, boolean canAudit)
    {
        if (!canAudit)
        {
            throw new ServiceException("只有管理员可以审核帖子");
        }
        if (post.getPostId() == null)
        {
            throw new ServiceException("帖子不存在或已被删除");
        }
        Post old = postMapper.selectPostById(post.getPostId());
        if (StringUtils.isNull(old))
        {
            throw new ServiceException("帖子不存在或已被删除");
        }
        // 只有待审核的帖子能走审核接口：已发布/草稿/已驳回/已下架都不行
        if (!Post.STATUS_PENDING.equals(old.getStatus()))
        {
            throw new ServiceException("该帖子不在待审核状态，不能审核");
        }
        String result = post.getStatus();
        if (!Post.STATUS_PUBLISHED.equals(result) && !Post.STATUS_REJECTED.equals(result))
        {
            throw new ServiceException("审核结果只能是「通过」或「驳回」");
        }
        Post update = new Post();
        update.setPostId(old.getPostId());
        update.setStatus(result);
        // 驳回的帖子不能是精华（它本来也不公开）；通过时以复选框为准，未勾选即不设精华
        boolean featured = Post.STATUS_PUBLISHED.equals(result)
                && Post.FEATURED_YES.equals(post.getIsFeatured());
        update.setIsFeatured(featured ? Post.FEATURED_YES : Post.FEATURED_NO);
        // 审核备注以本次提交为准：留空即清空——通过时顺带清掉上一次的驳回原因，
        // 避免已通过帖子的详情页还挂着「驳回原因」（本列语义是「最近一次备注」，不存历史）
        String auditRemark = StringUtils.trim(post.getAuditRemark());
        if (StringUtils.isNotEmpty(auditRemark) && auditRemark.length() > 500)
        {
            throw new ServiceException("审核备注长度不能超过500个字符");
        }
        update.setAuditRemark(StringUtils.isEmpty(auditRemark) ? null : auditRemark);
        update.setUpdateBy(post.getUpdateBy());
        int rows = postMapper.updatePostAudit(update);
        if (rows == 0)
        {
            // 走到这里说明查库与 update 之间帖子被改/被删（并发审核兜底）
            throw new ServiceException("该帖子已被审核或状态已变更，请刷新后重试");
        }
        return rows;
    }

    /**
     * 发布态必须有正文
     *
     * 正文不能加 @NotBlank（那样草稿存不下去），所以在这里按最终状态判断：
     * 草稿允许正文为空，已发布 / 已下架 / 待审核 / 已驳回 都必须有内容
     * （送审的帖子同样不能是空壳）。
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
