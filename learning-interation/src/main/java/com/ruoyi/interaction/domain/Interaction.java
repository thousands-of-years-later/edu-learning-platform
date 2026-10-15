package com.ruoyi.interaction.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 点赞收藏表 learning_interaction
 *
 * 一张表管三种内容（帖子 / 课程 / 短视频）× 两种行为（点赞 / 收藏）。
 * 取消点赞是【硬删除】（直接删行），所以这里可以放心建唯一索引
 * (user_id, target_type, target_id, type) —— 它同时负责防重复与并发安全。
 *
 * 计数字段（learning_post.like_count 等）只由本模块维护，
 * 后台编辑内容时不再改动它们；内容被删除时由内容模块在删除语句里连同 del_flag 一起清零
 * （见 ShortVideoMapper.deleteShortVideoByIds 等，以及 sql/learning_orphan_cleanup.sql）。
 *
 * @author edu
 */
public class Interaction implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 目标类型：社区帖子 */
    public static final String TARGET_POST = "1";

    /** 目标类型：课程 */
    public static final String TARGET_COURSE = "2";

    /** 目标类型：短视频 */
    public static final String TARGET_VIDEO = "3";

    /** 行为：点赞 */
    public static final String TYPE_LIKE = "1";

    /** 行为：收藏 */
    public static final String TYPE_FAVORITE = "2";

    private Long id;

    /** 用户ID（sys_user.user_id） */
    private Long userId;

    /** 目标类型（1帖子 2课程 3短视频） */
    private String targetType;

    /** 目标ID */
    private Long targetId;

    /** 行为类型（1点赞 2收藏） */
    private String type;

    private Date createTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
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

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    /** 目标类型是否合法 */
    public static boolean isValidTargetType(String t)
    {
        return TARGET_POST.equals(t) || TARGET_COURSE.equals(t) || TARGET_VIDEO.equals(t);
    }

    /** 行为类型是否合法 */
    public static boolean isValidType(String t)
    {
        return TYPE_LIKE.equals(t) || TYPE_FAVORITE.equals(t);
    }
}