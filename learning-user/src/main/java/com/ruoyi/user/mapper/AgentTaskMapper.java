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

    /** 运行中更新进度（只改 RUNNING 的任务） */
    int updateProgress(@Param("taskId") Long taskId, @Param("progress") Integer progress);

    /** 用户取消：PENDING/RUNNING → CANCELED */
    int markCanceled(@Param("taskId") Long taskId);

    /** 幂等键查任务（唯一索引） */
    AgentTask selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /** 失败但保留部分结果（steps/refs/rounds/truncated 写进 output_json） */
    int markFailedWithOutput(@Param("taskId") Long taskId,
                             @Param("errorMsg") String errorMsg,
                             @Param("outputJson") String outputJson);
}
