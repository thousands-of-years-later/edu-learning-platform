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
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.api.ApiContext;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.user.domain.AgentSession;
import com.ruoyi.user.domain.AgentStep;
import com.ruoyi.user.domain.AgentTask;
import com.ruoyi.user.mapper.AgentStepMapper;
import com.ruoyi.user.service.IAgentService;

/**
 * 智能体执行入口 /agent/act
 *
 * 一次请求跑一个 function calling 循环：模型选工具 → 执行 → observation 回填 → 收尾。
 * 过程按步落 learning_agent_step，前端轮询 GET /agent/act/{taskId} 拿步骤。
 */
@RestController
@RequestMapping({"/api/v1/agent/act", "/agent/act"})
public class ApiV1AgentActController
{
    private static final Logger log = LoggerFactory.getLogger(ApiV1AgentActController.class);

    private static final int MAX_GOAL_LENGTH = 500;
    private static final int MAX_STEPS_HARD = 10;
    private static final int DEFAULT_STEPS = 8;

    @Autowired
    private IAgentService agentService;

    @Autowired
    private AgentStepMapper stepMapper;

    @Autowired
    private AgentClient agentClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 提交执行目标（异步任务）
     *
     * body: {goal, sessionId?, maxSteps?}
     */
    @PostMapping
    public AjaxResult act(@RequestBody Map<String, Object> body)
    {
        String goal = body == null ? null : Convert.toStr(body.get("goal"));
        if (StringUtils.isEmpty(goal))
        {
            return badRequest("目标不能为空");
        }
        final String goalText = goal.trim();
        if (goalText.length() > MAX_GOAL_LENGTH)
        {
            return badRequest("目标最长 " + MAX_GOAL_LENGTH + " 字（当前 " + goalText.length() + " 字）");
        }

        int requestedSteps = body.get("maxSteps") == null
                ? DEFAULT_STEPS : Convert.toInt(body.get("maxSteps"), DEFAULT_STEPS);
        final int maxSteps = Math.max(1, Math.min(requestedSteps, MAX_STEPS_HARD));

        Long userId = ApiContext.getUserId();
        Long sessionId = body.get("sessionId") == null ? null : Convert.toLong(body.get("sessionId"));
        String title = StringUtils.substring(goalText, 0, 30);

        AgentSession session;
        try
        {
            session = sessionId == null
                    ? agentService.createSession(userId, title, "act")
                    : agentService.requireSession(userId, sessionId);
        }
        catch (ServiceException e)
        {
            return AjaxResult.error(e.getMessage());
        }

        agentService.addMessage(userId, session.getSessionId(), "user", goalText, "text", null, 0);
        // 首条用户消息会把会话标题覆盖成消息内容，新会话再把标题改回来
        if (sessionId == null)
        {
            agentService.renameSession(userId, session.getSessionId(), title);
        }

        String inputJson;
        try
        {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("goal", goalText);
            input.put("maxSteps", maxSteps);
            inputJson = objectMapper.writeValueAsString(input);
        }
        catch (Exception e)
        {
            return AjaxResult.error("参数格式不合法");
        }

        AgentTask task = agentService.createTask(userId, session.getSessionId(), "act", inputJson, null);
        agentService.addMessage(userId, session.getSessionId(), "assistant",
                "已提交执行任务，可在步骤面板查看进展", "task", null, 0, task.getTaskId(), null);

        CompletableFuture.runAsync(() -> runActTask(userId, task.getTaskId(), goalText, maxSteps));

        AjaxResult ajax = AjaxResult.success();
        ajax.put("taskId", task.getTaskId());
        ajax.put("status", "PENDING");
        ajax.put("sessionId", session.getSessionId());
        return ajax;
    }

    /**
     * 查询执行状态与步骤（前端轮询）
     */
    @GetMapping("/{taskId}")
    public AjaxResult detail(@PathVariable Long taskId)
    {
        AgentTask task = agentService.requireTask(ApiContext.getUserId(), taskId);
        List<AgentStep> steps = stepMapper.selectStepsByTask(taskId);

        AjaxResult ajax = AjaxResult.success();
        ajax.put("task", task);
        ajax.put("steps", steps);

        String answer = null;
        if (StringUtils.isNotEmpty(task.getOutputJson()))
        {
            try
            {
                JsonNode output = objectMapper.readTree(task.getOutputJson());
                answer = output.path("answer").asText(null);
            }
            catch (Exception ignored)
            {
                // 旧任务或异常输出，不阻断步骤展示
            }
        }
        ajax.put("answer", answer);
        return ajax;
    }

    private void runActTask(Long userId, Long taskId, String goal, int maxSteps)
    {
        agentService.markRunning(taskId);

        String answer = "";
        String errorCode = null;
        String errorMessage = null;
        int stepCount = 0;

        try
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("goal", goal);
            payload.put("userId", userId);
            payload.put("taskId", "act-" + taskId);
            payload.put("maxSteps", maxSteps);

            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(agentClient.getBaseUrl() + "/internal/agent/act/stream"))
                    .timeout(Duration.ofSeconds(320))
                    .header("x-internal-token", agentClient.getInternalToken())
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<Stream<String>> response = client.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400)
            {
                throw new IllegalStateException("智能体服务返回 " + response.statusCode());
            }

            try (Stream<String> lines = response.body())
            {
                Iterator<String> iterator = lines.iterator();
                while (iterator.hasNext())
                {
                    String line = iterator.next();
                    if (StringUtils.isEmpty(line))
                    {
                        continue;
                    }
                    JsonNode node = objectMapper.readTree(line);
                    String type = node.path("type").asText("");
                    if ("step".equals(type))
                    {
                        insertStep(userId, taskId, node);
                        stepCount++;
                    }
                    else if ("final".equals(type))
                    {
                        answer = node.path("answer").asText("");
                    }
                    else if ("error".equals(type))
                    {
                        errorCode = node.path("errorCode").asText("ACT_ERROR");
                        errorMessage = node.path("message").asText("任务执行失败");
                    }
                }
            }
        }
        catch (Exception e)
        {
            log.error("act 任务执行失败 taskId={}", taskId, e);
            errorCode = "ACT_ERROR";
            errorMessage = e.getMessage();
        }

        if (errorCode != null)
        {
            agentService.markFailed(taskId, "[" + errorCode + "] "
                    + (errorMessage == null ? "" : errorMessage));
            return;
        }

        try
        {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("answer", answer);
            output.put("stepCount", stepCount);
            agentService.markSuccess(taskId, objectMapper.writeValueAsString(output));
        }
        catch (Exception e)
        {
            log.error("act 任务结果落库失败 taskId={}", taskId, e);
            agentService.markFailed(taskId, "结果序列化失败");
        }
    }

    private void insertStep(Long userId, Long taskId, JsonNode node) throws Exception
    {
        AgentStep step = new AgentStep();
        step.setTaskId(taskId);
        step.setUserId(userId);
        step.setSeq(node.path("seq").asInt());
        step.setStepType(node.path("stepType").asText(""));
        step.setToolName(node.hasNonNull("tool") ? node.get("tool").asText() : null);
        step.setArgsJson(node.has("args") ? objectMapper.writeValueAsString(node.get("args")) : null);
        step.setStatus(node.hasNonNull("status") ? node.get("status").asText() : null);
        step.setElapsedMs(node.hasNonNull("elapsedMs") ? node.get("elapsedMs").asInt() : null);

        if (node.has("observation"))
        {
            JsonNode observation = node.get("observation");
            step.setObservationJson(objectMapper.writeValueAsString(observation));
            step.setErrorCode(observation.hasNonNull("errorCode")
                    ? observation.get("errorCode").asText() : null);
        }
        stepMapper.insertStep(step);
    }

    private AjaxResult badRequest(String message)
    {
        AjaxResult ajax = AjaxResult.error(message);
        ajax.put("code", 400);
        return ajax;
    }
}
