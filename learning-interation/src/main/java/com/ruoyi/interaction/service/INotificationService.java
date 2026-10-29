package com.ruoyi.interaction.service;

import java.util.List;
import com.ruoyi.interaction.domain.Notification;

/**
 * 用户通知 服务层
 *
 * 两类接口：
 *   1. notify* —— 由点赞/收藏/评论/关注的服务在【同一个事务里】调用，写一条通知给被触发者。
 *      本服务【不】自己开事务、也不吞异常：通知写失败就和触发动作一起回滚。
 *   2. 收件箱 —— 列表 / 未读数 / 标记已读，接收者维度。
 *
 * @author edu
 */
public interface INotificationService
{
    /**
     * 新增点赞/收藏后调用（取消不调用）
     *
     * @param fromUserId 触发者（点赞/收藏的人）
     * @param targetType 目标类型（1帖子 2课程 3短视频；课程无归属人，直接跳过）
     * @param targetId   目标ID
     * @param type       互动类型（1点赞 2收藏，与 Notification.TYPE_LIKE/TYPE_FAVORITE 同值，原样透传）
     */
    public void notifyInteraction(Long fromUserId, String targetType, Long targetId, String type);

    /**
     * 评论成功后调用（一级评论）
     *
     * @param content 评论原文（调用方保证已 trim 且不超过 Notification.MAX_CONTENT_LENGTH）
     */
    public void notifyComment(Long fromUserId, String targetType, Long targetId, String content);

    /**
     * 回复（二级评论）成功后调用
     *
     * 通知两类人，且【同一件事只打扰一次】：
     *   1. 被回复评论的作者（toUserId）：type=5 回复评论；
     *   2. 内容归属人（帖子/短视频作者）：type=3 评论 —— 但归属人就是回复者、
     *      或就是被回复人（回复楼主，最常见）时【跳过】，不发第二条。
     * 课程没有归属人：回复课程评论只通知被回复人。
     * 回复自己的评论：两边条件都不成立，零通知。
     *
     * @param toUserId 被回复评论的作者（由服务端从被回复评论推导，不接受前端传入）
     * @param content  回复原文（调用方保证已 trim 且不超过 Notification.MAX_CONTENT_LENGTH）
     */
    public void notifyReply(Long fromUserId, String targetType, Long targetId, Long toUserId, String content);

    /**
     * 新增关注后调用（重复关注是幂等重放，不调用）
     *
     * @param toUserId 被关注者 = 通知接收者
     */
    public void notifyFollow(Long fromUserId, Long toUserId);

    /**
     * 校验列表的 isRead 筛选参数（null / '0' / '1'）
     *
     * 控制器必须在 startPage() 之前调用：抛异常时若已经 startPage()，
     * PageHelper 的 ThreadLocal 分页参数不会被消费，可能泄漏给同线程的下一个请求。
     */
    public void checkQuery(String isRead);

    /**
     * 校验列表的 type 筛选参数（客户端 4 个 tab：1点赞 2收藏 3评论 4关注；null / 空 = 全部）
     *
     * 与 checkQuery 一样必须在 startPage() 之前调用。
     * 注释：3（评论）在查询时会展开成 3+5（评论与回复），回复也属于"评论"这一类。
     */
    public void checkType(String type);

    /**
     * 我的通知列表（分页由控制器 startPage() 驱动）
     *
     * @param isRead 读取状态筛选（null / 空 = 全部）
     */
    public List<Notification> selectMyList(Long userId, String isRead);

    /**
     * 我的通知列表（带类型过滤，客户端 4 个 tab 用）
     *
     * @param type 1点赞 / 2收藏 / 3评论（含回复）/ 4关注；null / 空 = 全部
     */
    public List<Notification> selectMyList(Long userId, String isRead, String type);

    /** 我的未读数（角标） */
    public int countUnread(Long userId);

    /**
     * 标记单条已读（幂等）
     *
     * @return 影响行数；0 = 已读过 / 不是自己的，都算成功
     */
    public int markRead(Long userId, Long notificationId);

    /** 我的全部未读标记已读（幂等） */
    public int markReadAll(Long userId);
}
