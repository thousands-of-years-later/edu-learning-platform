package com.ruoyi.api.ws;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.api.service.IApiTokenService;
import com.ruoyi.common.core.api.ApiUser;
import com.ruoyi.common.ws.WsTokenAuthenticator;

/**
 * WebSocket 握手鉴权实现：复用 /api/v1 的 Token 校验
 *
 * 校验成功返回 userId，失败或异常返回 null（握手被拒）。
 *
 * @author edu
 */
@Component
public class ApiWsTokenAuthenticator implements WsTokenAuthenticator
{
    @Autowired
    private IApiTokenService apiTokenService;

    @Override
    public Long authenticate(String token)
    {
        if (token == null || token.isEmpty())
        {
            return null;
        }
        try
        {
            ApiUser user = apiTokenService.validate(token);
            return user == null ? null : user.getUserId();
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
