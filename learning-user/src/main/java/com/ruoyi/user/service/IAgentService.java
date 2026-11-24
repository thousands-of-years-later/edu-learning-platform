package com.ruoyi.user.service;

import java.util.List;
import com.ruoyi.user.domain.AgentMessage;
import com.ruoyi.user.domain.AgentSession;
import com.ruoyi.user.domain.AgentTask;

/** 智能体会话 / 消息 / 任务 */
public interface IAgentService
{
    AgentSession createSession(Long userId, String title);

    AgentSession createSession(Long userId, String title, String scene);

    AgentSession requireSession(Long userId, Long sessionId);

    List<AgentSession> listSessions(Long userId);

    AgentMessage addMessage(Long userId, Long sessionId, String role, String content,
                            String contentType, String refsJson, int tokens);

    List<AgentMessage> listMessages(Long userId, Long sessionId);

    /** 游标分页取消息，返回升序；beforeMessageId 为空表示取最新一页 */
    List<AgentMessage> listMessagesPage(Long userId, Long sessionId, Long beforeMessageId, Integer pageSize);

    /** 软删会话及其消息，重复调用幂等 */
    void deleteSession(Long userId, Long sessionId);

    /** 重命名会话，返回更新后的会话 */
    AgentSession renameSession(Long userId, Long sessionId, String title);

    AgentTask createTask(Long userId, Long sessionId, String taskType, String inputJson, String idempotencyKey);

    AgentTask requireTask(Long userId, Long taskId);

    void markRunning(Long taskId);

    void markSuccess(Long taskId, String outputJson);

    void markFailed(Long taskId, String errorMsg);
}
