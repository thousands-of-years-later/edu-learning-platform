package com.ruoyi.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.user.domain.AgentTask;

/** 智能体异步任务 */
public interface AgentTaskMapper
{
    int insertTask(AgentTask task);

    AgentTask selectTask(@Param("userId") Long userId, @Param("taskId") Long taskId);

    /** 历史消息联查任务用：一次取多个任务，避免 N+1 */
    List<AgentTask> selectTasksByIds(@Param("userId") Long userId, @Param("taskIds") List<Long> taskIds);

    int markRunning(@Param("taskId") Long taskId);

    int markSuccess(@Param("taskId") Long taskId, @Param("outputJson") String outputJson);

    int markFailed(@Param("taskId") Long taskId, @Param("errorMsg") String errorMsg);
}
