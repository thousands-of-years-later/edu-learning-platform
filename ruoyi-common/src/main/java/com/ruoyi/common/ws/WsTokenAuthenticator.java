package com.ruoyi.common.ws;

/**
 * WebSocket 握手鉴权器
 *
 * 由 learning-api 模块提供实现：把 /api/v1 的 Token 解析成 userId。
 * 本接口放在 common 是为了让握手逻辑不反向依赖业务认证模块。
 *
 * @author edu
 */
public interface WsTokenAuthenticator
{
    /**
     * 校验 token，返回 userId；无效 token 返回 null
     */
    Long authenticate(String token);
}
