package com.ruoyi.user.service;

import java.util.List;
import com.ruoyi.user.domain.AgentMessage;
import com.ruoyi.user.domain.AgentSession;
import com.ruoyi.user.domain.AgentTask;

/** 智能体会话 / 消息 / 任务 */
public interface IAgentService
{
    AgentSession createSession(Long userId, String title);

    AgentSession requireSession(Long userId, Long sessionId);

    List<AgentSession> listSessions(Long userId);

    AgentMessage addMessage(Long userId, Long sessionId, String role, String content,
                            String contentType, String refsJson, int tokens);

    List<AgentMessage> listMessages(Long userId, Long sessionId);

    AgentTask createTask(Long userId, Long sessionId, String taskType, String inputJson, String idempotencyKey);

    AgentTask requireTask(Long userId, Long taskId);

    void markRunning(Long taskId);

    void markSuccess(Long taskId, String outputJson);

    void markFailed(Long taskId, String errorMsg);
}
