package com.ruoyi.web.controller.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.user.domain.AgentMemory;
import com.ruoyi.user.service.IAgentService;

/**
 * 智能体内部回调接口（只给 agent 容器用）。
 *
 * agent 只有只读库权限，save_memory 工具必须回到 Java 落库；
 * 鉴权用 x-internal-token，与 agent 服务的 AGENT_INTERNAL_TOKEN 一致。
 */
@RestController
@RequestMapping("/internal/agent")
public class InternalAgentController
{
    @Value("${agent.internal-token:}")
    private String internalToken;

    @Autowired
    private IAgentService agentService;

    @PostMapping("/memory")
    public Map<String, Object> saveMemory(
            @RequestHeader(value = "x-internal-token", required = false) String token,
            @RequestBody Map<String, Object> body)
    {
        if (StringUtils.isEmpty(internalToken) || !internalToken.equals(token))
        {
            throw new ServiceException("无效的内部调用");
        }

        Long userId = Convert.toLong(body == null ? null : body.get("userId"));
        String content = Convert.toStr(body == null ? null : body.get("content"));
        if (userId == null || StringUtils.isEmpty(content))
        {
            throw new ServiceException("userId 与 content 不能为空");
        }

        String source = body.get("source") == null ? "agent" : Convert.toStr(body.get("source"));
        AgentMemory memory = agentService.addMemory(userId, content, source);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("memoryId", memory.getMemoryId());
        result.put("content", memory.getContent());
        return result;
    }
}
