package com.ruoyi.user.domain;

import java.io.Serializable;
import java.util.Date;

/** 智能体单步执行记录 learning_agent_step */
public class AgentStep implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Long stepId;
    private Long taskId;
    private Long userId;
    private Integer seq;
    /** thought / tool / observation / final */
    private String stepType;
    private String toolName;
    private String argsJson;
    private String observationJson;
    /** ok / error */
    private String status;
    private String errorCode;
    private Integer elapsedMs;
    private Date createTime;

    public Long getStepId() { return stepId; }
    public void setStepId(Long stepId) { this.stepId = stepId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public String getStepType() { return stepType; }
    public void setStepType(String stepType) { this.stepType = stepType; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getArgsJson() { return argsJson; }
    public void setArgsJson(String argsJson) { this.argsJson = argsJson; }
    public String getObservationJson() { return observationJson; }
    public void setObservationJson(String observationJson) { this.observationJson = observationJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public Integer getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(Integer elapsedMs) { this.elapsedMs = elapsedMs; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
