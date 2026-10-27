package com.ruoyi.api.service;

import com.ruoyi.api.domain.ApiToken;
import com.ruoyi.common.core.api.ApiUser;

/**
 * API 访问令牌 服务层
 *
 * @author edu
 */
public interface IApiTokenService
{
    /**
     * 签发 Token（登录成功后调用）
     *
     * @param userId  用户ID
     * @param loginIp 客户端IP（由调用方传入，服务层不自己去猜）
     * @return 刚签发的令牌（含 token 与 expireTime）
     */
    public ApiToken createToken(Long userId, String loginIp);

    /**
     * 校验 Token
     *
     * @param token 访问令牌
     * @return 调用者信息；Token 不存在 / 已过期 / 账号已停用或删除时返回 null
     */
    public ApiUser validate(String token);

    /**
     * 吊销 Token（退出登录）
     *
     * @param token 访问令牌，为空时什么都不做
     */
    public void revoke(String token);

    /**
     * 吊销某个用户的全部 Token（修改/重置密码后强制所有设备重新登录）
     *
     * @param userId 用户ID，为空时什么都不做
     */
    public void revokeAll(Long userId);
}
