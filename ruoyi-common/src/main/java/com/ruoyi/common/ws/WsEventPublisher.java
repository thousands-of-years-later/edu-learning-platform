package com.ruoyi.common.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 事件发布器
 *
 * 业务写库成功后调用 {@link #publishAfterCommit}：
 *  - 当前在事务里：注册到事务提交后发送，避免“库没提交、消息先到”
 *  - 不在事务里：立即发送
 *
 * 事件不落库、不保证必达，靠客户端重连 + sinceId 补齐。
 * 单个会话发送失败只关闭该会话，不影响其他订阅者。
 *
 * @author edu
 */
@Component
public class WsEventPublisher
{
    private static final Logger log = LoggerFactory.getLogger(WsEventPublisher.class);

    private final AtomicLong eventSeq = new AtomicLong(System.currentTimeMillis());
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private WsSessionRegistry registry;

    /**
     * 发布事件；当前在事务内时延后到事务提交后发送
     */
    public void publishAfterCommit(String type, Map<String, Object> data, String... topics)
    {
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    publish(type, data, topics);
                }
            });
        }
        else
        {
            publish(type, data, topics);
        }
    }

    /**
     * 立即发布事件
     */
    public void publish(String type, Map<String, Object> data, String... topics)
    {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("eventId", "e-" + eventSeq.incrementAndGet());
        payload.put("ts", System.currentTimeMillis());
        payload.put("data", data == null ? new HashMap<String, Object>() : data);

        String json;
        try
        {
            json = mapper.writeValueAsString(payload);
        }
        catch (Exception e)
        {
            log.error("序列化 WebSocket 事件失败: type={}", type, e);
            return;
        }

        // 多个 topic 可能命中同一会话，去重后再发
        Set<WebSocketSession> targets = new LinkedHashSet<>();
        for (String topic : topics)
        {
            targets.addAll(registry.sessionsOf(topic));
        }
        for (WebSocketSession session : targets)
        {
            try
            {
                if (session.isOpen())
                {
                    session.sendMessage(new TextMessage(json));
                }
            }
            catch (IOException e)
            {
                log.warn("WebSocket 推送失败，关闭会话: {}", session.getId());
                registry.remove(session);
                closeQuietly(session);
            }
        }
    }

    private void closeQuietly(WebSocketSession session)
    {
        try
        {
            session.close();
        }
        catch (IOException ignored)
        {
            // 忽略关闭异常
        }
    }
}
