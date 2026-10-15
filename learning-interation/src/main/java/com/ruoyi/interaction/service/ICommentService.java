package com.ruoyi.interaction.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.interaction.domain.Comment;

/**
 * 评论 服务层
 *
 * @author edu
 */
public interface ICommentService
{
    /**
     * 发表评论 / 回复评论
     *
     * @param userId     评论人用户ID（由控制器从登录态取，绝不接受前端传入）
     * @param targetType 目标类型（1帖子 2课程 3短视频）
     * @param targetId   目标ID（课程评论传 course_id，不是课程内视频的 video_id）
     * @param content    评论内容（纯文本）
     * @param parentId   被回复的评论ID（null = 一级评论，旧客户端行为完全不变；
     *                   非 null = 二级评论：挂在 parentId 所属【一级评论】下，
     *                   被回复人由服务端从被回复评论的作者推导，前端不用也不能传）
     * @return {commentId: 新评论ID, count: 该目标的最新评论总数（含二级评论）}
     */
    public Map<String, Object> insertComment(Long userId, String targetType, Long targetId, String content, Long parentId);

    /**
     * 校验目标是否可评论（参数合法 + 存在且可见），不合法直接抛 ServiceException
     *
     * 【必须在控制器的 startPage() 之前调用】：PageHelper 的分页参数挂在 ThreadLocal 上，
     * 会被它之后的【第一条】查询消费掉 —— 本方法里就有查询（existsXxx），
     * 先开分页会让分页作用在存在性校验上，真正要分页的列表查询反而没有 LIMIT。
     *
     * @param targetType 目标类型（1帖子 2课程 3短视频）
     * @param targetId   目标ID
     */
    public void checkTarget(String targetType, Long targetId);

    /**
     * 查询某个目标下的评论列表（一级评论 + 其 children 二级回复；分页由控制器 startPage() 驱动）
     *
     * 调用方必须先调 checkTarget()：本方法只做参数校验与树组装，
     * 【不做存在性校验】（那会是一条额外查询，见 checkTarget 的说明）。
     *
     * 分页的是【一级评论】（TableDataInfo.total = 一级评论数），每条一级评论的 children
     * 是它的全部二级回复（一次 in 查询取回，不是 N+1；无回复时是空数组）。
     *
     * @param targetType 目标类型（1帖子 2课程 3短视频）
     * @param targetId   目标ID
     * @return 一级评论集合（children 已填充）
     */
    public List<Comment> selectCommentList(String targetType, Long targetId);

    /**
     * 删除自己发表的评论（逻辑删除）
     *
     * 目标类型/ID 取自评论本身，不接受前端传入，避免删自己的评论却减了别人的计数。
     *
     * 删【一级评论】会级联软删它名下的全部二级评论（那些回复的作者可能是别人），
     * 计数按实际删除行数减少；删【二级评论】只删自己那一条。
     *
     * @param userId    操作用户ID（由控制器从登录态取）
     * @param commentId 评论ID（一级或二级都行）
     * @return {count: 该目标的最新评论总数（含二级评论）}
     */
    public Map<String, Object> deleteComment(Long userId, Long commentId);
}
