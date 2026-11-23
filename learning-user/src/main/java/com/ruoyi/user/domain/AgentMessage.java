package com.ruoyi.user.domain;

import java.io.Serializable;
import java.util.Date;

/** 智能体消息 learning_agent_message */
public class AgentMessage implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long messageId;
    private Long sessionId;
    private Long userId;
    private String role;
    private String content;
    private String contentType;
    private String refsJson;
    private Integer tokens;
    private String status;
    private Date createTime;

    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getRefsJson() { return refsJson; }
    public void setRefsJson(String refsJson) { this.refsJson = refsJson; }
    public Integer getTokens() { return tokens; }
    public void setTokens(Integer tokens) { this.tokens = tokens; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
