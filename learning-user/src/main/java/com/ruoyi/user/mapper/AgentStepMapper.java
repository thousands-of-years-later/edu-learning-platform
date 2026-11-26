package com.ruoyi.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.user.domain.AgentStep;

/** 智能体单步记录 */
public interface AgentStepMapper
{
    int insertStep(AgentStep step);

    List<AgentStep> selectStepsByTask(@Param("taskId") Long taskId);
}
