package com.ruoyi.user.domain;

import java.io.Serializable;
import java.util.Date;

/** 智能体会话 learning_agent_session */
public class AgentSession implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long sessionId;
    private Long userId;
    private String title;
    private String scene;
    private String status;
    private Date lastMessageTime;
    private Date createTime;
    private Date updateTime;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(Date lastMessageTime) { this.lastMessageTime = lastMessageTime; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
