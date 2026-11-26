package com.ruoyi.user.service;

import java.util.List;
import com.ruoyi.user.domain.AgentMemory;
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

    /** 带任务/结果快照的消息：taskId 给 PPT、视频用，resultJson 给 SQL 表格用 */
    AgentMessage addMessage(Long userId, Long sessionId, String role, String content,
                            String contentType, String refsJson, int tokens,
                            Long taskId, String resultJson);

    List<AgentMessage> listMessages(Long userId, Long sessionId);

    /** 游标分页取消息，返回升序；beforeMessageId 为空表示取最新一页 */
    List<AgentMessage> listMessagesPage(Long userId, Long sessionId, Long beforeMessageId, Integer pageSize);

    /** 软删会话及其消息，重复调用幂等 */
    void deleteSession(Long userId, Long sessionId);

    /** 重命名会话，返回更新后的会话 */
    AgentSession renameSession(Long userId, Long sessionId, String title);

    AgentTask createTask(Long userId, Long sessionId, String taskType, String inputJson, String idempotencyKey);

    AgentTask requireTask(Long userId, Long taskId);

    /** 历史消息联查任务：按 taskId 批量取，返回属于该用户的 */
    List<AgentTask> listTasksByIds(Long userId, List<Long> taskIds);

    void markRunning(Long taskId);

    void markSuccess(Long taskId, String outputJson);

    void markFailed(Long taskId, String errorMsg);

    /** 运行中更新进度（0-100，只改 RUNNING 的任务） */
    void updateProgress(Long taskId, Integer progress);

    /** 用户取消：PENDING/RUNNING → CANCELED */
    void markCanceled(Long taskId);

    /** 按幂等键查任务（不校验用户，调用方自行比对 userId） */
    AgentTask findByIdempotencyKey(String idempotencyKey);

    /** 失败时保留部分结果：errorMsg + outputJson（truncated/rounds/refs 等） */
    void markFailedWithOutput(Long taskId, String errorMsg, String outputJson);

    /** 用户长期记忆，按 memoryId 倒序，只返回生效中的 */
    List<AgentMemory> listMemories(Long userId);

    /** 新增一条长期记忆，内容上限 500 字，单用户最多 50 条 */
    AgentMemory addMemory(Long userId, String content, String source);

    /** 更新一条记忆内容，只能改自己的 */
    void updateMemory(Long userId, Long memoryId, String content);

    /** 软删一条记忆，幂等 */
    void deleteMemory(Long userId, Long memoryId);
}
