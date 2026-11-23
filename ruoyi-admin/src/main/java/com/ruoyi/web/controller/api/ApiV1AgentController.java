package com.ruoyi.web.controller.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.user.domain.AgentMessage;
import com.ruoyi.user.domain.AgentSession;
import com.ruoyi.user.domain.AgentTask;
import com.ruoyi.user.service.IAgentService;

/**
 * 智能体入口（/api/v1/agent/**）
 *
 * 鉴权由 ApiTokenFilter 完成；会话/消息/任务落在 Java，Python 只做计算。
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

    @Autowired
    private IAgentService agentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    /** 新建会话：{title?} */
    @PostMapping("/sessions")
    public AjaxResult createSession(@RequestBody(required = false) Map<String, Object> body)
    {
        String title = body == null ? null : Convert.toStr(body.get("title"));
        AgentSession session = agentService.createSession(ApiContext.getUserId(), title);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("session", session);
        return ajax;
    }

    /** 我的会话列表（分页） */
    @GetMapping("/sessions")
    public AjaxResult sessions(@RequestParam(defaultValue = "1") Integer pageNum,
                               @RequestParam(defaultValue = "20") Integer pageSize)
    {
        if (pageNum == null || pageNum < 1)
        {
            return AjaxResult.error("页码不合法");
        }
        if (pageSize == null || pageSize < 1 || pageSize > 50)
        {
            return AjaxResult.error("每页条数不合法（1~50）");
        }
        PageHelper.startPage(pageNum, pageSize);
        List<AgentSession> list = agentService.listSessions(ApiContext.getUserId());
        PageInfo<AgentSession> pageInfo = new PageInfo<>(list);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("total", pageInfo.getTotal());
        ajax.put("rows", list);
        return ajax;
    }

    /** 某个会话的消息列表 */
    @GetMapping("/sessions/{sessionId}/messages")
    public AjaxResult messages(@PathVariable Long sessionId)
    {
        List<AgentMessage> messages = agentService.listMessages(ApiContext.getUserId(), sessionId);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("rows", messages);
        return ajax;
    }

    /**
     * 课程问答（带会话落库）
     *
     * 请求：{sessionId?, question, courseId?}
     * 返回：{sessionId, messageId, answer, refs, retrieved}
     */
    @PostMapping("/chat")
    public AjaxResult chat(@RequestBody Map<String, Object> body)
    {
        String question = body == null ? null : Convert.toStr(body.get("question"));
        if (StringUtils.isEmpty(question))
        {
            return AjaxResult.error("问题不能为空");
        }
        Long sessionId = body == null ? null : Convert.toLong(body.get("sessionId"));
        Long courseId = body == null ? null : Convert.toLong(body.get("courseId"));

        AgentSession session;
        if (sessionId == null)
        {
            session = agentService.createSession(ApiContext.getUserId(), question);
        }
        else
        {
            session = agentService.requireSession(ApiContext.getUserId(), sessionId);
        }

        agentService.addMessage(ApiContext.getUserId(), session.getSessionId(),
                "user", question, "text", null, 0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", question);
        if (courseId != null)
        {
            payload.put("courseId", courseId);
        }

        try
        {
            Map<String, Object> result = agentClient.post("/internal/chat/ask", payload);
            String answer = result == null ? "" : Convert.toStr(result.get("answer"));
            String refsJson = result == null ? null : objectMapper.writeValueAsString(result.get("refs"));
            AgentMessage assistant = agentService.addMessage(ApiContext.getUserId(), session.getSessionId(),
                    "assistant", answer, "text", refsJson, 0);

            AjaxResult ajax = AjaxResult.success();
            ajax.put("sessionId", session.getSessionId());
            ajax.put("messageId", assistant.getMessageId());
            if (result != null)
            {
                ajax.putAll(result);
            }
            return ajax;
        }
        catch (RestClientResponseException e)
        {
            log.warn("Agent chat 返回错误: status={}", e.getStatusCode());
            return AjaxResult.error("智能体服务调用失败（" + e.getStatusCode().value() + "）");
        }
        catch (Exception e)
        {
            log.error("Agent chat 调用失败", e);
            return AjaxResult.error("智能体服务暂不可用");
        }
    }

    /** 视频总结（一期同步 mock，任务状态落库） */
    @PostMapping("/video/summary")
    public AjaxResult videoSummary(@RequestBody Map<String, Object> body)
    {
        Long videoId = body == null ? null : Convert.toLong(body.get("videoId"));
        if (videoId == null)
        {
            return AjaxResult.error("视频ID不能为空");
        }

        String inputJson;
        try
        {
            inputJson = objectMapper.writeValueAsString(body);
        }
        catch (Exception e)
        {
            return AjaxResult.error("参数格式不合法");
        }

        AgentTask task = agentService.createTask(ApiContext.getUserId(), null,
                "video_summary", inputJson, null);
        agentService.markRunning(task.getTaskId());

        try
        {
            Map<String, Object> result = agentClient.post("/internal/video/summary",
                    Map.of("videoId", videoId));
            String outputJson = objectMapper.writeValueAsString(result);
            agentService.markSuccess(task.getTaskId(), outputJson);

            AjaxResult ajax = AjaxResult.success();
            ajax.put("taskId", task.getTaskId());
            if (result != null)
            {
                ajax.putAll(result);
            }
            return ajax;
        }
        catch (Exception e)
        {
            log.error("视频总结任务失败: taskId={}", task.getTaskId(), e);
            agentService.markFailed(task.getTaskId(), e.getMessage());
            return AjaxResult.error("视频总结失败，请稍后重试");
        }
    }

    /** 任务状态 */
    @GetMapping("/tasks/{taskId}")
    public AjaxResult task(@PathVariable Long taskId)
    {
        AgentTask task = agentService.requireTask(ApiContext.getUserId(), taskId);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("task", task);
        return ajax;
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
