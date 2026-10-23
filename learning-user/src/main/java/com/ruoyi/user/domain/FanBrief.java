package com.ruoyi.user.domain;

import java.io.Serializable;

/**
 * 粉丝预览（我的主页顶部"张/李/王/赵 等 356 人关注"那一行）
 *
 * 不是一张表：数据来自 learning_follow + sys_user，只取最近关注的几个人做头像展示。
 *
 * @author edu
 */
public class FanBrief implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 粉丝的用户ID */
    private Long userId;

    /** 昵称（头像为空时前端用首字兜底，设计稿里那些"张/李/王"圆形头像就是这种） */
    private String userName;

    /** 头像地址，可能为 null */
    private String avatar;

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
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
