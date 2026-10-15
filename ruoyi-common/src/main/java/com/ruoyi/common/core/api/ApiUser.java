package com.ruoyi.common.core.api;

/**
 * 当前 API 调用者（由 Token 解析出来的用户）
 *
 * 只放鉴权与展示用得上的最小字段：控制器真正需要的是 userId，
 * 需要更多资料时按 userId 自己查，避免这里变成第二个 SysUser。
 *
 * @author edu
 */
public class ApiUser
{
    /** 用户ID（sys_user.user_id） */
    private Long userId;

    /** 登录账号 */
    private String loginName;

    /** 用户昵称 */
    private String userName;

    public ApiUser()
    {
    }

    public ApiUser(Long userId, String loginName, String userName)
    {
        this.userId = userId;
        this.loginName = loginName;
        this.userName = userName;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getLoginName()
    {
        return loginName;
    }

    public void setLoginName(String loginName)
    {
        this.loginName = loginName;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }
}
