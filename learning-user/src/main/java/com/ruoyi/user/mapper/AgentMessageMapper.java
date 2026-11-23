package com.ruoyi.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.user.domain.AgentMessage;

/** 智能体消息 */
public interface AgentMessageMapper
{
    int insertMessage(AgentMessage message);

    List<AgentMessage> selectMessages(@Param("userId") Long userId, @Param("sessionId") Long sessionId);
}
