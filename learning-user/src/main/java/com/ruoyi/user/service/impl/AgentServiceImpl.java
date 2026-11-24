package com.ruoyi.user.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.user.domain.AgentMessage;
import com.ruoyi.user.domain.AgentSession;
import com.ruoyi.user.domain.AgentTask;
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

    @Autowired
    private AgentSessionMapper sessionMapper;

    @Autowired
    private AgentMessageMapper messageMapper;

    @Autowired
    private AgentTaskMapper taskMapper;

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
        requireSession(userId, sessionId);
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setContentType(StringUtils.isEmpty(contentType) ? "text" : contentType);
        message.setRefsJson(refsJson);
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
