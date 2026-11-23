package com.ruoyi.web.controller.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.ws.WsEventPublisher;
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

    @Autowired
    private WsEventPublisher wsEventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

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

    /**
     * 课程问答（WebSocket 流式）
     *
     * 请求：{sessionId?, question, courseId?}
     * 立即返回：{requestId, sessionId}
     * 后续通过 WS 推送：agent.start / agent.delta / agent.done / agent.error
     */
    @PostMapping("/chat/stream")
    public AjaxResult chatStream(@RequestBody Map<String, Object> body)
    {
        String question = body == null ? null : Convert.toStr(body.get("question"));
        if (StringUtils.isEmpty(question))
        {
            return AjaxResult.error("问题不能为空");
        }
        if (StringUtils.isEmpty(agentClient.getInternalToken()))
        {
            return AjaxResult.error("智能体内部令牌未配置");
        }

        Long userId = ApiContext.getUserId();
        Long sessionId = body == null ? null : Convert.toLong(body.get("sessionId"));
        Long courseId = body == null ? null : Convert.toLong(body.get("courseId"));

        AgentSession session = sessionId == null
                ? agentService.createSession(userId, question)
                : agentService.requireSession(userId, sessionId);

        agentService.addMessage(userId, session.getSessionId(), "user", question, "text", null, 0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", question);
        if (courseId != null)
        {
            payload.put("courseId", courseId);
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture.runAsync(() -> streamChat(userId, session.getSessionId(), payload, requestId));

        AjaxResult ajax = AjaxResult.success();
        ajax.put("requestId", requestId);
        ajax.put("sessionId", session.getSessionId());
        return ajax;
    }

    private void streamChat(Long userId, Long sessionId, Map<String, Object> payload, String requestId)
    {
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("requestId", requestId);
        start.put("sessionId", sessionId);
        wsEventPublisher.publish("agent.start", start, "user:" + userId);

        StringBuilder answer = new StringBuilder();
        Object refsObject = null;
        boolean finished = false;

        try
        {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(agentClient.getBaseUrl() + "/internal/chat/stream"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("x-internal-token", agentClient.getInternalToken())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() != 200)
            {
                throw new IllegalStateException("Agent 流式接口返回 " + response.statusCode());
            }

            int seq = 0;
            try (Stream<String> lines = response.body())
            {
                Iterator<String> iterator = lines.iterator();
                while (iterator.hasNext())
                {
                    String line = iterator.next().trim();
                    if (line.isEmpty())
                    {
                        continue;
                    }
                    Map<String, Object> event;
                    try
                    {
                        event = objectMapper.readValue(line, new TypeReference<Map<String, Object>>() {});
                    }
                    catch (Exception parseError)
                    {
                        log.warn("忽略无法解析的 Agent 流式片段: {}", line);
                        continue;
                    }

                    Object error = event.get("error");
                    if (error != null)
                    {
                        throw new IllegalStateException(String.valueOf(error));
                    }

                    Object delta = event.get("delta");
                    if (delta != null)
                    {
                        answer.append(delta);
                        seq++;
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("requestId", requestId);
                        data.put("seq", seq);
                        data.put("content", String.valueOf(delta));
                        wsEventPublisher.publish("agent.delta", data, "user:" + userId);
                    }

                    if (Boolean.TRUE.equals(event.get("done")))
                    {
                        refsObject = event.get("refs");
                        finished = true;
                        break;
                    }
                }
            }

            if (!finished)
            {
                throw new IllegalStateException("Agent 流式响应提前结束");
            }

            List<Map<String, Object>> refs = refsObject == null
                    ? List.of()
                    : objectMapper.convertValue(refsObject, new TypeReference<List<Map<String, Object>>>() {});
            String refsJson = objectMapper.writeValueAsString(refs);
            AgentMessage assistant = agentService.addMessage(userId, sessionId, "assistant",
                    answer.toString(), "text", refsJson, 0);

            Map<String, Object> done = new LinkedHashMap<>();
            done.put("requestId", requestId);
            done.put("sessionId", sessionId);
            done.put("messageId", assistant.getMessageId());
            done.put("answer", answer.toString());
            done.put("refs", refs);
            wsEventPublisher.publish("agent.done", done, "user:" + userId);
        }
        catch (Exception e)
        {
            log.error("Agent 流式问答失败: requestId={}", requestId, e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("requestId", requestId);
            error.put("code", "STREAM_FAILED");
            error.put("message", "智能体服务暂不可用");
            wsEventPublisher.publish("agent.error", error, "user:" + userId);
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
