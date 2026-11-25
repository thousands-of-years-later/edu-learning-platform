package com.ruoyi.user.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.user.domain.AgentMemory;
import com.ruoyi.user.domain.AgentMessage;
import com.ruoyi.user.domain.AgentSession;
import com.ruoyi.user.domain.AgentTask;
import com.ruoyi.user.mapper.AgentMemoryMapper;
import com.ruoyi.user.mapper.AgentMessageMapper;
import com.ruoyi.user.mapper.AgentSessionMapper;
import com.ruoyi.user.mapper.AgentTaskMapper;
import com.ruoyi.user.service.IAgentService;

/** 智能体会话/消息/任务服务实现 */
@Service
public class AgentServiceImpl implements IAgentService
{
    private static final int TITLE_MAX = 100;
    private static final int SESSION_TITLE_PREVIEW = 30;
    private static final int MEMORY_MAX = 500;
    private static final int MEMORY_LIMIT = 50;

    @Autowired
    private AgentSessionMapper sessionMapper;

    @Autowired
    private AgentMessageMapper messageMapper;

    @Autowired
    private AgentTaskMapper taskMapper;

    @Autowired
    private AgentMemoryMapper memoryMapper;

    @Override
    @Transactional
    public AgentSession createSession(Long userId, String title)
    {
        return createSession(userId, title, "chat");
    }

    @Override
    @Transactional
    public AgentSession createSession(Long userId, String title, String scene)
    {
        checkUser(userId);
        AgentSession session = new AgentSession();
        session.setUserId(userId);
        session.setTitle(StringUtils.isEmpty(title) ? "新会话" : cut(title, TITLE_MAX));
        session.setScene(StringUtils.isEmpty(scene) ? "chat" : scene);
        sessionMapper.insertSession(session);
        return session;
    }

    @Override
    public AgentSession requireSession(Long userId, Long sessionId)
    {
        checkUser(userId);
        if (sessionId == null)
        {
            throw new ServiceException("会话ID不能为空");
        }
        AgentSession session = sessionMapper.selectSession(userId, sessionId);
        if (session == null)
        {
            throw new ServiceException("会话不存在或无权访问");
        }
        return session;
    }

    @Override
    public List<AgentSession> listSessions(Long userId)
    {
        checkUser(userId);
        return sessionMapper.selectSessions(userId);
    }

    @Override
    @Transactional
    public AgentMessage addMessage(Long userId, Long sessionId, String role, String content,
                                   String contentType, String refsJson, int tokens)
    {
        return addMessage(userId, sessionId, role, content, contentType, refsJson, tokens, null, null);
    }

    @Override
    @Transactional
    public AgentMessage addMessage(Long userId, Long sessionId, String role, String content,
                                   String contentType, String refsJson, int tokens,
                                   Long taskId, String resultJson)
    {
        requireSession(userId, sessionId);
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setContentType(StringUtils.isEmpty(contentType) ? "text" : contentType);
        message.setRefsJson(refsJson);
        message.setTaskId(taskId);
        message.setResultJson(resultJson);
        message.setTokens(Math.max(tokens, 0));
        messageMapper.insertMessage(message);

        // 首条用户消息用问题前 30 字作为标题，后续消息不覆盖
        String title = null;
        if ("user".equals(role) && StringUtils.isNotEmpty(content))
        {
            List<AgentMessage> messages = messageMapper.selectMessages(userId, sessionId);
            if (messages != null && messages.size() == 1)
            {
                title = cut(content, SESSION_TITLE_PREVIEW);
            }
        }
        sessionMapper.touchSession(sessionId, title);
        return message;
    }

    @Override
    public List<AgentMessage> listMessages(Long userId, Long sessionId)
    {
        requireSession(userId, sessionId);
        return messageMapper.selectMessages(userId, sessionId);
    }

    @Override
    public List<AgentMessage> listMessagesPage(Long userId, Long sessionId, Long beforeMessageId, Integer pageSize)
    {
        requireSession(userId, sessionId);
        int size = (pageSize == null || pageSize < 1) ? 30 : Math.min(pageSize, 100);
        List<AgentMessage> rows = messageMapper.selectMessagesBefore(userId, sessionId, beforeMessageId, size);
        // 查询是倒序（为了取最近一页），返回给前端要正序展示
        List<AgentMessage> ordered = new ArrayList<>(rows == null ? Collections.emptyList() : rows);
        Collections.reverse(ordered);
        return ordered;
    }

    @Override
    @Transactional
    public void deleteSession(Long userId, Long sessionId)
    {
        // 先校验归属：不是自己的会话直接抛异常，不会走到删除
        requireSession(userId, sessionId);
        messageMapper.deleteBySession(userId, sessionId);
        sessionMapper.deleteSession(userId, sessionId);
    }

    @Override
    @Transactional
    public AgentSession renameSession(Long userId, Long sessionId, String title)
    {
        requireSession(userId, sessionId);
        String value = title == null ? "" : title.trim();
        if (StringUtils.isEmpty(value))
        {
            throw new ServiceException("会话标题不能为空");
        }
        sessionMapper.updateTitle(userId, sessionId, cut(value, TITLE_MAX));
        return sessionMapper.selectSession(userId, sessionId);
    }

    @Override
    @Transactional
    public AgentTask createTask(Long userId, Long sessionId, String taskType, String inputJson, String idempotencyKey)
    {
        checkUser(userId);
        if (StringUtils.isEmpty(taskType))
        {
            throw new ServiceException("任务类型不能为空");
        }
        AgentTask task = new AgentTask();
        task.setUserId(userId);
        task.setSessionId(sessionId);
        task.setTaskType(taskType);
        task.setInputJson(inputJson);
        task.setIdempotencyKey(idempotencyKey);
        taskMapper.insertTask(task);
        return taskMapper.selectTask(userId, task.getTaskId());
    }

    @Override
    public AgentTask requireTask(Long userId, Long taskId)
    {
        checkUser(userId);
        if (taskId == null)
        {
            throw new ServiceException("任务ID不能为空");
        }
        AgentTask task = taskMapper.selectTask(userId, taskId);
        if (task == null)
        {
            throw new ServiceException("任务不存在或无权访问");
        }
        return task;
    }

    @Override
    public List<AgentTask> listTasksByIds(Long userId, List<Long> taskIds)
    {
        if (taskIds == null || taskIds.isEmpty())
        {
            return List.of();
        }
        return taskMapper.selectTasksByIds(userId, taskIds);
    }

    @Override
    public void markRunning(Long taskId)
    {
        taskMapper.markRunning(taskId);
    }

    @Override
    public void markSuccess(Long taskId, String outputJson)
    {
        taskMapper.markSuccess(taskId, outputJson);
    }

    @Override
    public void markFailed(Long taskId, String errorMsg)
    {
        taskMapper.markFailed(taskId, errorMsg);
    }

    @Override
    public List<AgentMemory> listMemories(Long userId)
    {
        checkUser(userId);
        return memoryMapper.selectMemories(userId);
    }

    @Override
    @Transactional
    public AgentMemory addMemory(Long userId, String content, String source)
    {
        checkUser(userId);
        if (StringUtils.isEmpty(content))
        {
            throw new ServiceException("记忆内容不能为空");
        }
        if (memoryMapper.selectMemories(userId).size() >= MEMORY_LIMIT)
        {
            throw new ServiceException("记忆条数已达上限（" + MEMORY_LIMIT + "），请先删除部分记忆");
        }
        AgentMemory memory = new AgentMemory();
        memory.setUserId(userId);
        memory.setContent(cut(content, MEMORY_MAX));
        memory.setSource(StringUtils.isEmpty(source) ? "manual" : cut(source, 32));
        memoryMapper.insertMemory(memory);
        return memory;
    }

    @Override
    @Transactional
    public void updateMemory(Long userId, Long memoryId, String content)
    {
        checkUser(userId);
        if (memoryId == null)
        {
            throw new ServiceException("记忆ID不能为空");
        }
        if (StringUtils.isEmpty(content))
        {
            throw new ServiceException("记忆内容不能为空");
        }
        int rows = memoryMapper.updateMemory(userId, memoryId, cut(content, MEMORY_MAX));
        if (rows == 0)
        {
            throw new ServiceException("记忆不存在或无权访问");
        }
    }

    @Override
    @Transactional
    public void deleteMemory(Long userId, Long memoryId)
    {
        checkUser(userId);
        if (memoryId == null)
        {
            throw new ServiceException("记忆ID不能为空");
        }
        memoryMapper.deleteMemory(userId, memoryId);
    }

    private void checkUser(Long userId)
    {
        if (userId == null)
        {
            throw new ServiceException("请先登录");
        }
    }

    private String cut(String text, int max)
    {
        String value = text == null ? "" : text.trim();
        return value.length() <= max ? value : value.substring(0, max);
    }
}
