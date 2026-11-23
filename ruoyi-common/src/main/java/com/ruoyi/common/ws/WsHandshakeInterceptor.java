package com.ruoyi.common.ws;

import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import com.ruoyi.common.utils.StringUtils;

/**
 * WebSocket 握手拦截器
 *
 * 连接参数示例：ws://host/ws/forum?token=<api_token>
 * 只做鉴权：token 无效就拒绝握手；鉴权通过把 userId 写进 session attributes，
 * 后续订阅 user:{id} 时据此校验越权。
 *
 * @author edu
 */
@Component
public class WsHandshakeInterceptor implements HandshakeInterceptor
{
    private final ObjectProvider<WsTokenAuthenticator> authenticatorProvider;

    public WsHandshakeInterceptor(ObjectProvider<WsTokenAuthenticator> authenticatorProvider)
    {
        this.authenticatorProvider = authenticatorProvider;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception
    {
        WsTokenAuthenticator authenticator = authenticatorProvider.getIfAvailable();
        if (authenticator == null)
        {
            return false;
        }
        String token = null;
        if (request instanceof ServletServerHttpRequest servletRequest)
        {
            token = servletRequest.getServletRequest().getParameter("token");
        }
        if (StringUtils.isEmpty(token))
        {
            return false;
        }
        Long userId = authenticator.authenticate(token);
        if (userId == null)
        {
            return false;
        }
        attributes.put("userId", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception)
    {
    }
}
