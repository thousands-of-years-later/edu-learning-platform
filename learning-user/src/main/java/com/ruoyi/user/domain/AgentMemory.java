package com.ruoyi.user.domain;

import java.io.Serializable;
import java.util.Date;

/** 智能体长期记忆 learning_agent_memory */
public class AgentMemory implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long memoryId;
    private Long userId;
    private String content;
    private String source;
    private String status;
    private Date createTime;
    private Date updateTime;

    public Long getMemoryId() { return memoryId; }
    public void setMemoryId(Long memoryId) { this.memoryId = memoryId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
