package com.ruoyi.interaction.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.interaction.domain.Interaction;
import com.ruoyi.interaction.domain.Notification;
import com.ruoyi.interaction.mapper.NotificationMapper;
import com.ruoyi.interaction.service.INotificationService;

/**
 * 用户通知 服务层实现
 *
 * notify* 方法【不加 @Transactional】：调用方（点赞/评论/关注）本身就在事务里，
 * 本 Bean 经代理注入后普通调用即加入调用方事务（REQUIRED）。这是刻意的 ——
 * 通知写失败要和触发动作一起回滚，所以不能吞异常、也不能 REQUIRES_NEW。
 *
 * 谁会被通知（被触发者）：
 *   帖子 -> learning_post.user_id，短视频 -> learning_short_video.user_id；
 *   课程没有 user_id 归属人列，【永远不产生通知】（tableOf 返回 null 直接跳过）；
 *   关注 -> 被关注者本人。
 * 自我操作（自己赞自己 / 自评自论）不打扰任何人，静默跳过。
 *
 * MVP 取舍：取消点赞/收藏/关注不删已产生的通知；「赞 → 取消 → 再赞」会产生两条通知。
 * 将来要治，最便宜的做法是插入前查「同 (user_id, from_user_id, type, target_type, target_id)
 * 的未读行是否已存在」——【不要】加唯一键，同一个人对同一目标多次评论是合法的。
 *
 * @author edu
 */
@Service
public class NotificationServiceImpl implements INotificationService
{
    @Autowired
    private NotificationMapper notificationMapper;

    @Override
    public void notifyInteraction(Long fromUserId, String targetType, Long targetId, String type)
    {
        Long toUserId = ownerOf(targetType, targetId);
        if (toUserId == null || toUserId.equals(fromUserId))
        {
            // 归属人查不到（课程 / 并发下目标刚被删或下架）或自己操作自己的内容：静默跳过
            return;
        }

        Notification n = new Notification();
        n.setUserId(toUserId);
        n.setFromUserId(fromUserId);
        // type 原样透传：点赞'1' / 收藏'2' 与 Notification.TYPE_LIKE/TYPE_FAVORITE 同值
        n.setType(type);
        n.setTargetType(targetType);
        n.setTargetId(targetId);
        notificationMapper.insertNotification(n);
    }

    @Override
    public void notifyComment(Long fromUserId, String targetType, Long targetId, String content)
    {
        Long toUserId = ownerOf(targetType, targetId);
        if (toUserId == null || toUserId.equals(fromUserId))
        {
            return;
        }
        // 调用方（CommentServiceImpl）已保证 content 是 trim 后且不超过 500 字。
        // 这里【不做 Java substring 截断】兜底：substring 按 UTF-16 切，可能把 emoji 切成
        // 半个代理对，落库变 '?' 甚至直接报 1366。真超了就是调用方违约，抛错比悄悄截断好。
        if (content != null && content.length() > Notification.MAX_CONTENT_LENGTH)
        {
            throw new ServiceException("评论内容不能超过" + Notification.MAX_CONTENT_LENGTH + "个字");
        }

        Notification n = new Notification();
        n.setUserId(toUserId);
        n.setFromUserId(fromUserId);
        n.setType(Notification.TYPE_COMMENT);
        n.setTargetType(targetType);
        n.setTargetId(targetId);
        n.setContent(content);
        notificationMapper.insertNotification(n);
    }

    @Override
    public void notifyFollow(Long fromUserId, Long toUserId)
    {
        // 被关注者就是接收者，无需查库。fromUserId == toUserId 已由 FollowServiceImpl
        // 的「不能关注自己」挡住，这里不重复判
        Notification n = new Notification();
        n.setUserId(toUserId);
        n.setFromUserId(fromUserId);
        n.setType(Notification.TYPE_FOLLOW);
        notificationMapper.insertNotification(n);
    }

    @Override
    public void checkQuery(String isRead)
    {
        // 本方法不查库，但控制器依旧在 startPage() 之前调它：抛异常时若已 startPage()，
        // PageHelper 的 ThreadLocal 分页参数不会被消费，可能泄漏给同线程的下一个请求
        if (!Notification.isValidRead(isRead))
        {
            throw new ServiceException("读取状态不合法");
        }
    }

    @Override
    public List<Notification> selectMyList(Long userId, String isRead)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
        // 这里【只做参数校验，不查库】：控制器是在 startPage() 之后调本方法的，
        // 而 PageHelper 的分页参数挂在 ThreadLocal 上、会被其后第一条查询消费掉，
        // 在这里查一次库分页就会作用在那条查询上、真正的列表反而没有 LIMIT
        // （表现是分页静默失效、一次返回全量，与 CommentServiceImpl.selectCommentList 同一纪律）。
        // isRead 的合法性校验拆到了 checkQuery()，由控制器在 startPage() 之前调用。
        return notificationMapper.selectMyList(userId, isRead);
    }

    @Override
    public int countUnread(Long userId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
        return notificationMapper.countUnread(userId);
    }

    @Override
    public int markRead(Long userId, Long notificationId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
        if (notificationId == null)
        {
            throw new ServiceException("通知ID不能为空");
        }
        // 影响 0 行有三种正常情况：已读过 / 不是自己的 / 不存在。都按成功返回
        // （幂等，与 FollowServiceImpl.cancelFollow 同一取舍），也避免被用来逐个探测
        // 别人的通知是否存在
        return notificationMapper.markRead(userId, notificationId);
    }

    @Override
    public int markReadAll(Long userId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
        return notificationMapper.markReadAll(userId);
    }

    /** 内容归属人（被触发者）；查不到（含课程）返回 null，调用方据此跳过 */
    private Long ownerOf(String targetType, Long targetId)
    {
        String table = tableOf(targetType);
        if (table == null)
        {
            return null;
        }
        // 存在性/可见性由 SQL 里的 del_flag='0' and status='0' 再兜一次：
        // 目标在触发动作校验之后、写通知之前被删/下架时，宁可漏发也不给不可见内容发通知
        return notificationMapper.selectTargetOwner(table, idColumnOf(targetType), targetId);
    }

    /**
     * 目标类型 -> 归属人所在的表
     * 【课程返回 null】= 课程没有 user_id 归属人列，永远不产生通知。
     * 只返回常量，不接受外部输入（tableOf/idColumnOf 与 InteractionServiceImpl 是同一套纪律，
     * 是 NotificationMapper.xml 里 ${} 拼接安全的前提）。
     */
    private String tableOf(String targetType)
    {
        if (Interaction.TARGET_POST.equals(targetType))
        {
            return "learning_post";
        }
        if (Interaction.TARGET_VIDEO.equals(targetType))
        {
            return "learning_short_video";
        }
        return null;
    }

    /**
     * 目标类型 -> 主键列名；只有帖子/短视频会走到这里（tableOf 已挡掉课程）
     */
    private String idColumnOf(String targetType)
    {
        return Interaction.TARGET_POST.equals(targetType) ? "post_id" : "video_id";
    }
}
