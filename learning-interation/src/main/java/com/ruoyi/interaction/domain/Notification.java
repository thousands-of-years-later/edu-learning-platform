package com.ruoyi.interaction.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 用户通知表 learning_notification
 *
 * 点赞 / 收藏 / 评论 / 关注发生后写给【被触发者】的收件箱，一行只属于一个接收者
 * （user_id = 被触发者，from_user_id = 触发者）。
 * 注意与后台的 sys_notice（管理员发公告）是两套东西，互不关联。
 *
 * 类型编码：1点赞 2收藏 3评论 4关注 5回复评论 —— 其中 1/2 与 {@link Interaction}.TYPE_LIKE/TYPE_FAVORITE
 * 【刻意同值】，点赞/收藏通知直接透传 Interaction 的 type，不用做翻译。
 * 5回复评论 只发给【被回复评论的作者】（二级评论产生）；内容归属人收到的仍是 3评论，
 * 但归属人就是被回复人时只发 5 那条，不重复打扰（见 NotificationServiceImpl.notifyReply）。
 *
 * 课程（target_type=2）永远不会产生通知：learning_course 没有 user_id 归属人列，
 * 没有「被触发者」可言，服务层对课程直接跳过。关注类没有内容目标，targetType / targetId 为 null。
 *
 * 取消点赞/收藏/关注【不删除】已产生的通知（也没有删除接口），代价是「赞 → 取消 → 再赞」
 * 会有两条通知，MVP 接受，见 sql/learning_notification.sql 头注释。
 *
 * content 只在【评论类】有值：评论原文（trim 后、最长500字），这样列表接口不用回查评论表。
 * 纯文本，原样存不做转义；渲染端只能以文本节点输出（th:text / 小程序 <text> / textContent），
 * 禁止 innerHTML（与 Comment.content 同一约定）。
 *
 * 故意不继承 BaseEntity：本表没有 create_by / update_by / remark，
 * 继承了反而会让 getParams() 往每个返回行里多塞一个空的 params。
 *
 * @author edu
 */
public class Notification implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 通知类型：点赞（与 Interaction.TYPE_LIKE 同值，可直接透传） */
    public static final String TYPE_LIKE = "1";

    /** 通知类型：收藏（与 Interaction.TYPE_FAVORITE 同值，可直接透传） */
    public static final String TYPE_FAVORITE = "2";

    /** 通知类型：评论 */
    public static final String TYPE_COMMENT = "3";

    /** 通知类型：关注 */
    public static final String TYPE_FOLLOW = "4";

    /** 通知类型：回复评论（发给被回复评论的作者；与 3评论 区分，客户端据此显示「回复了你的评论」） */
    public static final String TYPE_REPLY = "5";

    /** 读取状态：未读 */
    public static final String IS_READ_NO = "0";

    /** 读取状态：已读 */
    public static final String IS_READ_YES = "1";

    /** 评论摘要最大长度，必须与 learning_notification.content 的 varchar(500) 保持一致 */
    public static final int MAX_CONTENT_LENGTH = 500;

    private Long notificationId;

    /** 接收者用户ID（被触发者，sys_user.user_id） */
    private Long userId;

    /** 触发者用户ID（点赞/收藏/评论/关注的人，sys_user.user_id） */
    private Long fromUserId;

    /** 通知类型（1点赞 2收藏 3评论 4关注 5回复评论） */
    private String type;

    /** 目标类型（1帖子 3短视频；关注类为 null；课程永不产生通知） */
    private String targetType;

    /** 目标ID（关注类为 null） */
    private Long targetId;

    /** 评论摘要（仅评论类有值，最长500字） */
    private String content;

    /**
     * 读取状态（0未读 1已读）
     *
     * 注意 getter 必须叫 getIsRead()：Jackson 的 is 前缀只认 boolean，
     * 写成 isRead() 这个 String 字段会从 JSON 里消失。
     */
    private String isRead;

    /** 读取时间（未读为 null） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date readTime;

    /** 产生时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 触发者登录名（关联 sys_user 查出，非本表列） */
    private String fromUserName;

    /** 触发者头像（关联 sys_user 查出，非本表列） */
    private String fromUserAvatar;

    public Long getNotificationId()
    {
        return notificationId;
    }

    public void setNotificationId(Long notificationId)
    {
        this.notificationId = notificationId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getFromUserId()
    {
        return fromUserId;
    }

    public void setFromUserId(Long fromUserId)
    {
        this.fromUserId = fromUserId;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getTargetType()
    {
        return targetType;
    }

    public void setTargetType(String targetType)
    {
        this.targetType = targetType;
    }

    public Long getTargetId()
    {
        return targetId;
    }

    public void setTargetId(Long targetId)
    {
        this.targetId = targetId;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getIsRead()
    {
        return isRead;
    }

    public void setIsRead(String isRead)
    {
        this.isRead = isRead;
    }

    public Date getReadTime()
    {
        return readTime;
    }

    public void setReadTime(Date readTime)
    {
        this.readTime = readTime;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public String getFromUserName()
    {
        return fromUserName;
    }

    public void setFromUserName(String fromUserName)
    {
        this.fromUserName = fromUserName;
    }

    public String getFromUserAvatar()
    {
        return fromUserAvatar;
    }

    public void setFromUserAvatar(String fromUserAvatar)
    {
        this.fromUserAvatar = fromUserAvatar;
    }

    /** 读取状态是否合法（null / 空 表示「不筛选」，也算合法） */
    public static boolean isValidRead(String t)
    {
        return t == null || t.isEmpty() || IS_READ_NO.equals(t) || IS_READ_YES.equals(t);
    }
}
