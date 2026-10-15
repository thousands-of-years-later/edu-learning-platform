package com.ruoyi.community.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 用户关注表 learning_follow
 *
 * 只做【用户关注用户】这一种关系（不做点赞/评论那种 target_type 多态表）：
 * 每个用户对另一个用户最多一条关注记录（唯一索引 uk_user_follow 保证），
 * 取消关注是【硬删除】，不是改状态位，因此唯一索引是安全的
 * （与 learning_interaction / learning_course_rating 同一取舍）。
 *
 * 本表没有 del_flag，也没有任何冗余计数列：粉丝数/关注数一律由本表 count(*) 实时统计，
 * 不往 sys_user 加 fans_count 之类的字段。
 *
 * userName / avatar 是【展示用户】的昵称与头像（关联 sys_user 查出，非本表列）。
 * 两个列表里「展示用户」不是同一个人，取用时看清楚：
 *   /following（我关注的人）→ 展示 follow_user_id 那一位；
 *   /fans（我的粉丝）      → 展示 user_id 那一位。
 *
 * 故意不继承 BaseEntity：本表没有 create_by / update_by / remark，
 * 继承了反而会让 getParams() 往每个返回行里多塞一个空的 params。
 *
 * @author edu
 */
public class Follow implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 关注ID */
    private Long followId;

    /** 关注者用户ID（谁点的关注，sys_user.user_id） */
    private Long userId;

    /** 被关注用户ID（被关注的人，sys_user.user_id） */
    private Long followUserId;

    /** 关注时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 展示用户的昵称（关联 sys_user 查出，非本表列） */
    private String userName;

    /** 展示用户的头像（关联 sys_user 查出，非本表列） */
    private String avatar;

    public Long getFollowId()
    {
        return followId;
    }

    public void setFollowId(Long followId)
    {
        this.followId = followId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getFollowUserId()
    {
        return followUserId;
    }

    public void setFollowUserId(Long followUserId)
    {
        this.followUserId = followUserId;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getAvatar()
    {
        return avatar;
    }

    public void setAvatar(String avatar)
    {
        this.avatar = avatar;
    }
}
