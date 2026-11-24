package com.ruoyi.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.user.domain.AgentMessage;

/** 智能体消息 */
public interface AgentMessageMapper
{
    int insertMessage(AgentMessage message);

    List<AgentMessage> selectMessages(@Param("userId") Long userId, @Param("sessionId") Long sessionId);

    /** 游标分页：取 message_id 小于 beforeMessageId 的最近 pageSize 条（倒序） */
    List<AgentMessage> selectMessagesBefore(@Param("userId") Long userId,
                                            @Param("sessionId") Long sessionId,
                                            @Param("beforeMessageId") Long beforeMessageId,
                                            @Param("pageSize") Integer pageSize);

    /** 会话删除时一并软删消息 */
    int deleteBySession(@Param("userId") Long userId, @Param("sessionId") Long sessionId);
}
