package com.ruoyi.interaction.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
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
 *   点赞/收藏/一级评论 -> 内容归属人：帖子 -> learning_post.user_id，
 *                        短视频 -> learning_short_video.user_id；
 *   回复（二级评论）    -> 被回复评论的作者（type=5）+ 内容归属人（type=3，见 notifyReply 的去重）；
 *   关注               -> 被关注者本人。
 * 课程没有 user_id 归属人列，【归属人侧永远不产生通知】（tableOf 返回 null 直接跳过）；
 * 但回复课程评论时被回复人仍然会收到 type=5 —— 那条不依赖归属人。
 * 自我操作（自己赞自己 / 自评自论 / 回复自己）不打扰任何人，静默跳过。
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

    /** 客户端「点赞」tab：只查 type=1 */
    private static final List<String> TAB_LIKE = java.util.Arrays.asList(Notification.TYPE_LIKE);

    /** 客户端「收藏」tab：只查 type=2 */
    private static final List<String> TAB_FAVORITE = java.util.Arrays.asList(Notification.TYPE_FAVORITE);

    /** 客户端「评论」tab：评论 3 + 回复 5 */
    private static final List<String> TAB_COMMENT =
            java.util.Arrays.asList(Notification.TYPE_COMMENT, Notification.TYPE_REPLY);

    /** 客户端「关注」tab：只查 type=4 */
    private static final List<String> TAB_FOLLOW = java.util.Arrays.asList(Notification.TYPE_FOLLOW);

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
    public void notifyReply(Long fromUserId, String targetType, Long targetId, Long toUserId, String content)
    {
        // 与 notifyComment 完全相同的长度纪律：调用方已保证 trim 且不超长，
        // 这里【不做 Java substring 截断】兜底（substring 按 UTF-16 切，可能切坏 emoji 的代理对）
        if (content != null && content.length() > Notification.MAX_CONTENT_LENGTH)
        {
            throw new ServiceException("评论内容不能超过" + Notification.MAX_CONTENT_LENGTH + "个字");
        }

        // 1、被回复人：回复自己的评论不打扰自己（与自赞自评静默跳过同一规矩）
        if (toUserId != null && !toUserId.equals(fromUserId))
        {
            Notification n = new Notification();
            n.setUserId(toUserId);
            n.setFromUserId(fromUserId);
            n.setType(Notification.TYPE_REPLY);
            n.setTargetType(targetType);
            n.setTargetId(targetId);
            n.setContent(content);
            notificationMapper.insertNotification(n);
        }

        // 2、内容归属人：与一级评论一致照旧通知（回复也计入 comment_count，楼主应当知道讨论在继续），
        //    但两种情况不重复打扰 —— 归属人就是回复者；归属人就是被回复人（回复楼主自己的评论，
        //    最常见），同一件事发「回复 + 评论」两条纯属噪音。
        //    课程无归属人（ownerOf 返回 null），只通知被回复人 —— 比原先「课程评论零通知」是改进。
        //    归属人通知仍要求内容可见（selectTargetOwner 带 status/del_flag），
        //    内容刚被删时宁可漏发也不给不可见内容发通知（与 notifyComment 同一取舍）
        Long owner = ownerOf(targetType, targetId);
        if (owner != null && !owner.equals(fromUserId) && !owner.equals(toUserId))
        {
            Notification n = new Notification();
            n.setUserId(owner);
            n.setFromUserId(fromUserId);
            n.setType(Notification.TYPE_COMMENT);
            n.setTargetType(targetType);
            n.setTargetId(targetId);
            n.setContent(content);
            notificationMapper.insertNotification(n);
        }
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
        // 老的会话接口（/api/notification）不带类型过滤，等价于查全部
        return selectMyList(userId, isRead, null);
    }

    @Override
    public List<Notification> selectMyList(Long userId, String isRead, String type)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
        // 这里【只做参数归一，不查库】：控制器是在 startPage() 之后调本方法的，
        // 而 PageHelper 的分页参数挂在 ThreadLocal 上、会被其后第一条查询消费掉，
        // 在这里查一次库分页就会作用在那条查询上、真正的列表反而没有 LIMIT
        // （表现是分页静默失效、一次返回全量，与 CommentServiceImpl.selectCommentList 同一纪律）。
        // 合法性校验拆到了 checkType()，由控制器在 startPage() 之前调用。
        return notificationMapper.selectMyList(userId, isRead, tabTypes(StringUtils.trim(type)));
    }

    @Override
    public void checkType(String type)
    {
        // 空 = 不过滤（老客户端、或前端"全部"场景）
        String t = StringUtils.trim(type);
        if (StringUtils.isNotEmpty(t) && tabTypes(t) == null)
        {
            throw new ServiceException("通知类型不合法（1点赞 2收藏 3评论 4关注）");
        }
    }

    /**
     * 客户端 tab -> 实际查询的 type 集合
     *
     * 空 = null（查全部）；非法 = null（由 checkType 提前报错，这里再返回 null 只会变成"查全部"，
     * 所以调用方必须先用 checkType 校验过）。
     * 「评论」tab 展开成 3 + 5：一级评论 3 与回复 5 在客户端是同一类消息。
     */
    private List<String> tabTypes(String type)
    {
        if (StringUtils.isEmpty(type))
        {
            return null;
        }
        if (Notification.TYPE_LIKE.equals(type))
        {
            return TAB_LIKE;
        }
        if (Notification.TYPE_FAVORITE.equals(type))
        {
            return TAB_FAVORITE;
        }
        if (Notification.TYPE_COMMENT.equals(type))
        {
            return TAB_COMMENT;
        }
        if (Notification.TYPE_FOLLOW.equals(type))
        {
            return TAB_FOLLOW;
        }
        return null;
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
