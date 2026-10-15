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
     * 评论成功后调用
     *
     * @param content 评论原文（调用方保证已 trim 且不超过 Notification.MAX_CONTENT_LENGTH）
     */
    public void notifyComment(Long fromUserId, String targetType, Long targetId, String content);

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
     * 我的通知列表（分页由控制器 startPage() 驱动）
     *
     * @param isRead 读取状态筛选（null / 空 = 全部）
     */
    public List<Notification> selectMyList(Long userId, String isRead);

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
