package com.ruoyi.common.ws;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket 会话注册表（单节点内存版）
 *
 * 两个映射：
 *  - topicSessions：topic -> 订阅该 topic 的会话集合
 *  - userSessions ：sessionId -> userId（握手鉴权时写入，清理时移除）
 *
 * 所有操作都是线程安全的；二期上多实例时把这里换成 Redis Pub/Sub，
 * 业务调用方（WsEventPublisher）不用改。
 *
 * @author edu
 */
@Component
public class WsSessionRegistry
{
    private final Map<String, Set<WebSocketSession>> topicSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> userSessions = new ConcurrentHashMap<>();

    public void subscribe(String topic, WebSocketSession session)
    {
        topicSessions.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unsubscribe(String topic, WebSocketSession session)
    {
        Set<WebSocketSession> sessions = topicSessions.get(topic);
        if (sessions != null)
        {
            sessions.remove(session);
            if (sessions.isEmpty())
            {
                topicSessions.remove(topic);
            }
        }
    }

    /**
     * 连接关闭时清理：从所有 topic 移除，并删除用户映射
     */
    public void remove(WebSocketSession session)
    {
        String id = session.getId();
        for (Set<WebSocketSession> sessions : topicSessions.values())
        {
            sessions.remove(session);
        }
        userSessions.remove(id);
    }

    public Set<WebSocketSession> sessionsOf(String topic)
    {
        Set<WebSocketSession> sessions = topicSessions.get(topic);
        return sessions == null ? Collections.emptySet() : Collections.unmodifiableSet(sessions);
    }

    public void setUserId(WebSocketSession session, Long userId)
    {
        if (userId != null)
        {
            userSessions.put(session.getId(), userId);
        }
    }

    public Long userIdOf(WebSocketSession session)
    {
        return userSessions.get(session.getId());
    }
}
