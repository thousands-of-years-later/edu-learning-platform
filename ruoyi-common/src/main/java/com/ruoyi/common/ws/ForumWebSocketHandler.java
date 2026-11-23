package com.ruoyi.common.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 论坛 WebSocket 处理器
 *
 * 客户端消息协议：
 *   {"action":"subscribe","topics":["forum:all","post:123"]}
 *   {"action":"unsubscribe","topics":["post:123"]}
 *   {"action":"ping"}
 *
 * 服务端消息（事件由 WsEventPublisher 发送）：
 *   {"type":"post.created","eventId":"...","ts":...,"data":{...}}
 *   {"type":"comment.created", ...}
 *   {"type":"like.changed", ...}
 *   {"type":"pong","ts":...}
 *
 * @author edu
 */
@Component
public class ForumWebSocketHandler extends TextWebSocketHandler
{
    private static final Logger log = LoggerFactory.getLogger(ForumWebSocketHandler.class);

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private WsSessionRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception
    {
        // 握手拦截器已把 userId 放进 attributes，这里绑定到注册表，
        // 供订阅 user:{id} 时做越权校验
        Object userId = session.getAttributes().get("userId");
        if (userId instanceof Long)
        {
            registry.setUserId(session, (Long) userId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception
    {
        JsonNode root;
        try
        {
            root = mapper.readTree(message.getPayload());
        }
        catch (Exception e)
        {
            send(session, "error", "消息格式错误");
            return;
        }

        String action = root.path("action").asText();
        if ("subscribe".equals(action))
        {
            subscribe(session, root);
        }
        else if ("unsubscribe".equals(action))
        {
            unsubscribe(session, root);
        }
        else if ("ping".equals(action))
        {
            send(session, "pong", null);
        }
        else
        {
            send(session, "error", "不支持的 action: " + action);
        }
    }

    private void subscribe(WebSocketSession session, JsonNode root)
    {
        Long userId = registry.userIdOf(session);
        if (root.path("topics").isArray())
        {
            for (JsonNode topicNode : root.path("topics"))
            {
                String topic = topicNode.asText();
                if (topic == null || topic.isEmpty())
                {
                    continue;
                }
                // user:{id} 只允许订阅自己，防止越权监听他人动态
                if (topic.startsWith("user:") && (userId == null
                        || !topic.equals("user:" + userId)))
                {
                    send(session, "error", "无权订阅该主题");
                    continue;
                }
                registry.subscribe(topic, session);
            }
        }
    }

    private void unsubscribe(WebSocketSession session, JsonNode root)
    {
        if (root.path("topics").isArray())
        {
            for (JsonNode topicNode : root.path("topics"))
            {
                String topic = topicNode.asText();
                if (topic != null && !topic.isEmpty())
                {
                    registry.unsubscribe(topic, session);
                }
            }
        }
    }

    private void send(WebSocketSession session, String type, String msg)
    {
        try
        {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", type);
            payload.put("ts", System.currentTimeMillis());
            if (msg != null)
            {
                payload.put("msg", msg);
            }
            session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
        }
        catch (Exception e)
        {
            log.warn("WebSocket 回复失败: {}", session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception
    {
        registry.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception
    {
        registry.remove(session);
    }
}
