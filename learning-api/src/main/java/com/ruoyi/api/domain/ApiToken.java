package com.ruoyi.api.domain;

import java.util.Date;
import java.util.UUID;

/**
 * API 访问令牌 learning_api_token
 *
 * 为什么是「随机 UUID 存表」而不是 JWT（建表脚本见 sql/learning_api_token.sql）：
 *   1. 仓库的缓存是 Ehcache（JVM 内），多实例之间不共享。登录态放缓存的话，
 *      用户在 A 机器登录、下一个请求被负载均衡打到 B 机器就直接 401；
 *      落 MySQL 天然多实例共享，也省掉一套 Redis 依赖。
 *   2. 存表意味着服务端随时能【吊销】某一个 Token（退出登录、封号），
 *      JWT 只能等它自己过期。
 *
 * @author edu
 */
public class ApiToken
{
    /** 主键 */
    private Long tokenId;

    /** 访问令牌（UUID 去横线） */
    private String token;

    /** 用户ID（逻辑关联 sys_user.user_id） */
    private Long userId;

    /** 签发时的客户端IP（排查 Token 泄露用） */
    private String loginIp;

    /** 过期时间 */
    private Date expireTime;

    /** 签发时间 */
    private Date createTime;

    /**
     * 生成一个 Token 字符串
     *
     * 用 UUID.randomUUID()：它是 122 位随机数（底层是 SecureRandom），猜不出来，
     * 也不用额外引加密库；去掉横线只是为了放进请求头时少一层转义。
     *
     * @return 32 位十六进制随机串
     */
    public static String randomToken()
    {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public Long getTokenId()
    {
        return tokenId;
    }

    public void setTokenId(Long tokenId)
    {
        this.tokenId = tokenId;
    }

    public String getToken()
    {
        return token;
    }

    public void setToken(String token)
    {
        this.token = token;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getLoginIp()
    {
        return loginIp;
    }

    public void setLoginIp(String loginIp)
    {
        this.loginIp = loginIp;
    }

    public Date getExpireTime()
    {
        return expireTime;
    }

    public void setExpireTime(Date expireTime)
    {
        this.expireTime = expireTime;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}
