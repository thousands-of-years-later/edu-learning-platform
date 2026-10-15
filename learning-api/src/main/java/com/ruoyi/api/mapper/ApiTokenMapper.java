package com.ruoyi.api.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.api.domain.ApiToken;
import com.ruoyi.common.core.api.ApiUser;

/**
 * API 访问令牌 数据层
 *
 * 校验时直接 join sys_user 取账号信息，不反向依赖 ruoyi-system，
 * 与 learning-interation 的 InteractionMapper 同一思路（目标存在性直接写 SQL）。
 *
 * @author edu
 */
public interface ApiTokenMapper
{
    /**
     * 插入一条令牌
     *
     * @param apiToken 令牌（token / userId / loginIp / expireTime / createTime 必填）
     * @return 影响行数
     */
    public int insertToken(ApiToken apiToken);

    /**
     * 按 Token 查当前用户
     *
     * 已过期、账号已停用、账号已删除都会查不到（判断写在 SQL 里，
     * 避免应用层漏判某个状态位），查不到即视为未登录。
     *
     * @param token 访问令牌
     * @return 调用者信息，无效时返回 null
     */
    public ApiUser selectUserByToken(@Param("token") String token);

    /**
     * 删除一个 Token（退出登录）
     *
     * @param token 访问令牌
     * @return 影响行数（Token 不存在时返回 0）
     */
    public int deleteToken(@Param("token") String token);

    /**
     * 清理某个用户已过期的 Token
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    public int deleteExpiredByUserId(@Param("userId") Long userId);
}
