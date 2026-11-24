package com.ruoyi.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.user.domain.AgentSession;

/** 智能体会话 */
public interface AgentSessionMapper
{
    int insertSession(AgentSession session);

    AgentSession selectSession(@Param("userId") Long userId, @Param("sessionId") Long sessionId);

    List<AgentSession> selectSessions(@Param("userId") Long userId);

    int touchSession(@Param("sessionId") Long sessionId, @Param("title") String title);

    int deleteSession(@Param("userId") Long userId, @Param("sessionId") Long sessionId);

    int updateTitle(@Param("userId") Long userId, @Param("sessionId") Long sessionId,
                    @Param("title") String title);
}
