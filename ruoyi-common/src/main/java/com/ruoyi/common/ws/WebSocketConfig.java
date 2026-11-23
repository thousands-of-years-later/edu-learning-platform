package com.ruoyi.common.ws;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册论坛实时推送端点
 *
 * 端点：/ws/forum（连接时带 token 查询参数）
 * 注意：/ws/** 需要在 Shiro 配置里放行，鉴权由 WsHandshakeInterceptor 自行完成。
 *
 * @author edu
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer
{
    @Autowired
    private ForumWebSocketHandler forumWebSocketHandler;

    @Autowired
    private WsHandshakeInterceptor wsHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry)
    {
        registry.addHandler(forumWebSocketHandler, "/ws/forum")
                .addInterceptors(wsHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
