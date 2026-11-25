package com.ruoyi.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.user.domain.AgentMemory;

/** 智能体长期记忆 */
public interface AgentMemoryMapper
{
    int insertMemory(AgentMemory memory);

    List<AgentMemory> selectMemories(@Param("userId") Long userId);

    /** 按内容查已生效的记忆，用于“记住”按钮幂等：同一句不重复插入 */
    AgentMemory selectByContent(@Param("userId") Long userId, @Param("content") String content);

    int updateMemory(@Param("userId") Long userId, @Param("memoryId") Long memoryId,
                     @Param("content") String content);

    int deleteMemory(@Param("userId") Long userId, @Param("memoryId") Long memoryId);
}
