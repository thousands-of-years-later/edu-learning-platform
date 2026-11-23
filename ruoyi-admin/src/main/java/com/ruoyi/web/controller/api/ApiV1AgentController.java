package com.ruoyi.web.controller.api;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * 智能体入口（/api/v1/agent/**）
 *
 * 鉴权由 ApiTokenFilter 完成，用户身份不传给 Python；
 * 这里只做参数转发，不落会话/任务（会话与任务由 Java 业务表维护）。
 *
 * @author edu
 */
@RestController
@RequestMapping("/api/v1/agent")
public class ApiV1AgentController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(ApiV1AgentController.class);

    @Autowired
    private AgentClient agentClient;

    /** SQL 问答：{question} -> {answer, sql, rows, rowCount, error} */
    @PostMapping("/sql/ask")
    public AjaxResult sqlAsk(@RequestBody Map<String, Object> body)
    {
        return proxy("/internal/sql/ask", body);
    }

    /** 课程 RAG 问答：{question, courseId?} -> {answer, refs, retrieved} */
    @PostMapping("/chat/ask")
    public AjaxResult chatAsk(@RequestBody Map<String, Object> body)
    {
        return proxy("/internal/chat/ask", body);
    }

    /** 视频总结（一期 mock） */
    @PostMapping("/video/summary")
    public AjaxResult videoSummary(@RequestBody Map<String, Object> body)
    {
        return proxy("/internal/video/summary", body);
    }

    private AjaxResult proxy(String path, Map<String, Object> body)
    {
        try
        {
            Map<String, Object> result = agentClient.post(path, body == null ? Map.of() : body);
            AjaxResult ajax = AjaxResult.success();
            if (result != null)
            {
                ajax.putAll(result);
            }
            return ajax;
        }
        catch (RestClientResponseException e)
        {
            log.warn("Agent 服务返回错误: path={}, status={}", path, e.getStatusCode());
            return AjaxResult.error("智能体服务调用失败（" + e.getStatusCode().value() + "）");
        }
        catch (Exception e)
        {
            log.error("Agent 服务调用失败: path={}", path, e);
            return AjaxResult.error("智能体服务暂不可用");
        }
    }
}
