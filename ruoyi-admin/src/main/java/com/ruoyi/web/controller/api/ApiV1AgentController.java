package com.ruoyi.web.controller.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import com.ruoyi.common.config.OssConfig;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
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

    /** 单次对话最多带 3 张图，控制延迟与费用 */
    private static final int MAX_CHAT_IMAGES = 3;

    /** 只传图不带文字时，用于落库展示与会话标题 */
    private static final String DEFAULT_IMAGE_QUESTION = "请描述这张图片的内容";

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

    /** 某个会话的消息列表（游标分页，默认最近 30 条） */
    @GetMapping("/sessions/{sessionId}/messages")
    public AjaxResult messages(@PathVariable Long sessionId,
                               @RequestParam(required = false) Long beforeMessageId,
                               @RequestParam(defaultValue = "30") Integer pageSize)
    {
        List<AgentMessage> messages = agentService.listMessagesPage(
                ApiContext.getUserId(), sessionId, beforeMessageId, pageSize);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("rows", messages);
        return ajax;
    }

    /** 删除会话（软删，消息一起软删，重复删除幂等） */
    @DeleteMapping("/sessions/{sessionId}")
    public AjaxResult deleteSession(@PathVariable Long sessionId)
    {
        agentService.deleteSession(ApiContext.getUserId(), sessionId);
        return AjaxResult.success();
    }

    /** 重命名会话 */
    @PutMapping("/sessions/{sessionId}")
    public AjaxResult renameSession(@PathVariable Long sessionId,
                                    @RequestBody(required = false) Map<String, Object> body)
    {
        String title = body == null ? null : Convert.toStr(body.get("title"));
        AgentSession session = agentService.renameSession(ApiContext.getUserId(), sessionId, title);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("session", session);
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

        List<String> imageUrls;
        try
        {
            imageUrls = parseImageUrls(body);
        }
        catch (ServiceException e)
        {
            return AjaxResult.error(e.getMessage());
        }
        if (StringUtils.isEmpty(question) && imageUrls.isEmpty())
        {
            return AjaxResult.error("问题不能为空");
        }
        String displayContent = StringUtils.isEmpty(question) ? DEFAULT_IMAGE_QUESTION : question;
        Long sessionId = body == null ? null : Convert.toLong(body.get("sessionId"));
        Long courseId = body == null ? null : Convert.toLong(body.get("courseId"));

        AgentSession session;
        if (sessionId == null)
        {
            session = agentService.createSession(ApiContext.getUserId(), displayContent);
        }
        else
        {
            session = agentService.requireSession(ApiContext.getUserId(), sessionId);
        }

        List<Map<String, String>> history = buildHistory(
                agentService.listMessages(ApiContext.getUserId(), session.getSessionId()));
        agentService.addMessage(ApiContext.getUserId(), session.getSessionId(),
                "user", displayContent, imageUrls.isEmpty() ? "text" : "image_text",
                imagesRefsJson(imageUrls), 0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", question == null ? "" : question);
        if (courseId != null)
        {
            payload.put("courseId", courseId);
        }
        if (!imageUrls.isEmpty())
        {
            payload.put("imageUrls", imageUrls);
        }
        if (!history.isEmpty())
        {
            payload.put("history", history);
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

        List<String> imageUrls;
        try
        {
            imageUrls = parseImageUrls(body);
        }
        catch (ServiceException e)
        {
            return AjaxResult.error(e.getMessage());
        }
        if (StringUtils.isEmpty(question) && imageUrls.isEmpty())
        {
            return AjaxResult.error("问题不能为空");
        }
        if (StringUtils.isEmpty(agentClient.getInternalToken()))
        {
            return AjaxResult.error("智能体内部令牌未配置");
        }

        String displayContent = StringUtils.isEmpty(question) ? DEFAULT_IMAGE_QUESTION : question;
        Long userId = ApiContext.getUserId();
        Long sessionId = body == null ? null : Convert.toLong(body.get("sessionId"));
        Long courseId = body == null ? null : Convert.toLong(body.get("courseId"));

        AgentSession session = sessionId == null
                ? agentService.createSession(userId, displayContent)
                : agentService.requireSession(userId, sessionId);

        List<Map<String, String>> history = buildHistory(
                agentService.listMessages(userId, session.getSessionId()));
        agentService.addMessage(userId, session.getSessionId(), "user", displayContent,
                imageUrls.isEmpty() ? "text" : "image_text", imagesRefsJson(imageUrls), 0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", question == null ? "" : question);
        if (courseId != null)
        {
            payload.put("courseId", courseId);
        }
        if (!imageUrls.isEmpty())
        {
            payload.put("imageUrls", imageUrls);
        }
        if (!history.isEmpty())
        {
            payload.put("history", history);
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

    /** 视频总结（异步任务：Paraformer ASR + 分段总结） */
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

        Long userId = ApiContext.getUserId();
        AgentTask task = agentService.createTask(userId, null, "video_summary", inputJson, null);
        CompletableFuture.runAsync(() -> runVideoTask(userId, task.getTaskId(), videoId));

        AjaxResult ajax = AjaxResult.success();
        ajax.put("taskId", task.getTaskId());
        ajax.put("status", "PENDING");
        return ajax;
    }

    private void runVideoTask(Long userId, Long taskId, Long videoId)
    {
        agentService.markRunning(taskId);
        try
        {
            Map<String, Object> result = agentClient.post("/internal/video/summary",
                    Map.of("videoId", videoId));
            agentService.markSuccess(taskId, objectMapper.writeValueAsString(result));
        }
        catch (Exception e)
        {
            log.error("视频总结任务失败: taskId={}", taskId, e);
            agentService.markFailed(taskId, e.getMessage());
        }
    }

    /** PPT 生成（异步任务） */
    @PostMapping("/ppt/generate")
    public AjaxResult pptGenerate(@RequestBody Map<String, Object> body)
    {
        String topic = body == null ? null : Convert.toStr(body.get("topic"));
        if (StringUtils.isEmpty(topic))
        {
            return AjaxResult.error("PPT 主题不能为空");
        }
        Integer pages = body == null ? null : Convert.toInt(body.get("pages"));
        int pageCount = pages == null || pages < 1 ? 6 : Math.min(pages, 20);

        String inputJson;
        try
        {
            inputJson = objectMapper.writeValueAsString(body);
        }
        catch (Exception e)
        {
            return AjaxResult.error("参数格式不合法");
        }

        Long userId = ApiContext.getUserId();
        AgentTask task = agentService.createTask(userId, null, "ppt", inputJson, null);
        CompletableFuture.runAsync(() -> runPptTask(userId, task.getTaskId(), topic, pageCount));

        AjaxResult ajax = AjaxResult.success();
        ajax.put("taskId", task.getTaskId());
        ajax.put("status", "PENDING");
        return ajax;
    }

    private void runPptTask(Long userId, Long taskId, String topic, int pages)
    {
        agentService.markRunning(taskId);
        try
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("topic", topic);
            payload.put("pages", pages);
            payload.put("userId", userId);
            payload.put("taskId", String.valueOf(taskId));
            Map<String, Object> result = agentClient.post("/internal/ppt/generate", payload);
            agentService.markSuccess(taskId, objectMapper.writeValueAsString(result));
        }
        catch (Exception e)
        {
            log.error("PPT 生成任务失败: taskId={}", taskId, e);
            agentService.markFailed(taskId, e.getMessage());
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

    /**
     * 解析并校验图片地址：只允许本平台 OSS 上 agent/chat/ 前缀的对象。
     *
     * 不能让前端传任意 URL，否则服务端/模型会替调用方去拉内网或第三方地址。
     */
    private List<String> parseImageUrls(Map<String, Object> body)
    {
        Object raw = body == null ? null : body.get("imageUrls");
        if (raw == null)
        {
            return List.of();
        }
        if (!(raw instanceof List<?> list))
        {
            throw new ServiceException("imageUrls 必须是数组");
        }
        if (list.size() > MAX_CHAT_IMAGES)
        {
            throw new ServiceException("最多支持 " + MAX_CHAT_IMAGES + " 张图片");
        }
        List<String> urls = new ArrayList<>();
        for (Object item : list)
        {
            String url = Convert.toStr(item);
            if (StringUtils.isEmpty(url) || !isChatImageUrl(url))
            {
                throw new ServiceException("图片地址不合法，只允许本平台上传的聊天图片");
            }
            urls.add(url);
        }
        return urls;
    }

    private boolean isChatImageUrl(String url)
    {
        try
        {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
            {
                return false;
            }
            String prefixHost = URI.create(OssConfig.resolveUrlPrefix()).getHost();
            // 上传接口返回的是 bucket 直连 https 地址（模型侧只认 https），
            // 所以直连域名也要放行，不能只认 urlPrefix 的 host
            String directHost = OssConfig.getBucketName() + "." + OssConfig.getEndpoint();
            boolean hostAllowed = (StringUtils.isNotEmpty(prefixHost) && prefixHost.equalsIgnoreCase(uri.getHost()))
                    || directHost.equalsIgnoreCase(uri.getHost());
            if (!hostAllowed)
            {
                return false;
            }
            String path = uri.getPath();
            if (path == null)
            {
                return false;
            }
            // urlPrefix 可能带路径（生产是 http://host/oss-preview 这种后端代理），
            // 先把前缀自带的 path 剥掉，剩下的才是真正的对象 key
            String prefixPath = URI.create(OssConfig.resolveUrlPrefix()).getPath();
            if (StringUtils.isNotEmpty(prefixPath) && path.startsWith(prefixPath))
            {
                path = path.substring(prefixPath.length());
            }
            return path.startsWith("/agent/chat/");
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private String imagesRefsJson(List<String> imageUrls)
    {
        if (imageUrls == null || imageUrls.isEmpty())
        {
            return null;
        }
        Map<String, Object> refs = new LinkedHashMap<>();
        List<Map<String, Object>> images = new ArrayList<>();
        for (String url : imageUrls)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("url", url);
            images.add(item);
        }
        refs.put("images", images);
        try
        {
            return objectMapper.writeValueAsString(refs);
        }
        catch (Exception e)
        {
            log.warn("序列化图片引用失败", e);
            return null;
        }
    }

    private List<Map<String, String>> buildHistory(List<AgentMessage> messages)
    {
        List<Map<String, String>> history = new ArrayList<>();
        if (messages == null || messages.isEmpty())
        {
            return history;
        }
        int start = Math.max(0, messages.size() - 8);
        int totalChars = 0;
        for (int i = start; i < messages.size(); i++)
        {
            AgentMessage message = messages.get(i);
            String role = message.getRole();
            String content = message.getContent();
            if (content == null || content.isBlank()
                    || (!"user".equals(role) && !"assistant".equals(role)))
            {
                continue;
            }
            if (content.length() > 1200)
            {
                content = content.substring(0, 1200);
            }
            if (totalChars + content.length() > 6000)
            {
                break;
            }
            totalChars += content.length();
            Map<String, String> item = new LinkedHashMap<>();
            item.put("role", role);
            item.put("content", content);
            history.add(item);
        }
        return history;
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
