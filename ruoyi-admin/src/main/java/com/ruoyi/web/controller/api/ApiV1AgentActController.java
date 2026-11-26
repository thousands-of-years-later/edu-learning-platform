package com.ruoyi.web.controller.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
 * 过程按步落 learning_agent_step，每落一步刷新 progress；
 * 前端轮询 GET /agent/act/{taskId} 拿步骤、currentStep、refs。
 * 长任务可用 POST /agent/tasks/{taskId}/cancel 停止。
 */
@RestController
@RequestMapping({"/api/v1/agent/act", "/agent/act"})
public class ApiV1AgentActController
{
    private static final Logger log = LoggerFactory.getLogger(ApiV1AgentActController.class);

    private static final int MAX_GOAL_LENGTH = 500;
    private static final int MAX_STEPS_HARD = 10;
    private static final int DEFAULT_STEPS = 8;
    /** 默认幂等键的时间桶：5 分钟内重复点发送复用同一任务 */
    private static final long IDEMPOTENT_WINDOW_MS = 5 * 60 * 1000L;

    @Autowired
    private IAgentService agentService;

    @Autowired
    private AgentStepMapper stepMapper;

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private ActTaskRegistry taskRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 提交执行目标（异步任务）
     *
     * body: {goal, sessionId?, maxSteps?, idempotencyKey?}
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

        final Long userId = ApiContext.getUserId();
        String idempotencyKey = Convert.toStr(body.get("idempotencyKey"));
        final boolean explicitKey = StringUtils.isNotEmpty(idempotencyKey);
        if (!explicitKey)
        {
            idempotencyKey = defaultIdempotencyKey(userId, goalText);
        }

        // 幂等命中：直接返回已有任务，不再建会话、不再跑一遍
        AgentTask existing = agentService.findByIdempotencyKey(idempotencyKey);
        if (existing != null && userId.equals(existing.getUserId()) && !isRetryableTerminal(existing, explicitKey))
        {
            AjaxResult ajax = AjaxResult.success();
            ajax.put("taskId", existing.getTaskId());
            ajax.put("status", existing.getStatus());
            ajax.put("sessionId", existing.getSessionId());
            ajax.put("idempotent", true);
            return ajax;
        }
        if (existing != null)
        {
            // 失败/取消过的默认键：换一个键重新执行，不让用户永远卡在旧任务
            idempotencyKey = idempotencyKey + "-r" + System.currentTimeMillis();
        }

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

        AgentTask task = agentService.createTask(userId, session.getSessionId(), "act", inputJson, idempotencyKey);
        agentService.addMessage(userId, session.getSessionId(), "assistant",
                "已提交执行任务，可在步骤面板查看进展", "task", null, 0, task.getTaskId(), null);

        final String finalKeyProtocolGoal = goalText;
        CompletableFuture.runAsync(() -> runActTask(userId, task.getTaskId(), finalKeyProtocolGoal, maxSteps));

        AjaxResult ajax = AjaxResult.success();
        ajax.put("taskId", task.getTaskId());
        ajax.put("status", "PENDING");
        ajax.put("sessionId", session.getSessionId());
        ajax.put("idempotent", false);
        return ajax;
    }

    /**
     * 查询执行状态与步骤（前端轮询，RUNNING 期间也能拿到已完成步骤）
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
        List<Object> refs = List.of();
        Map<String, Object> meta = new LinkedHashMap<>();
        if (StringUtils.isNotEmpty(task.getOutputJson()))
        {
            try
            {
                JsonNode output = objectMapper.readTree(task.getOutputJson());
                answer = output.path("answer").asText(null);
                if (output.has("refs") && output.get("refs").isArray())
                {
                    refs = objectMapper.convertValue(output.get("refs"), List.class);
                }
                for (String key : new String[]{"rounds", "maxSteps", "toolCallCount", "truncated",
                        "stepCount", "degraded", "degradeReason", "errorCode"})
                {
                    if (output.has(key))
                    {
                        meta.put(key, objectMapper.convertValue(output.get(key), Object.class));
                    }
                }
            }
            catch (Exception ignored)
            {
                // 旧任务或异常输出，不阻断步骤展示
            }
        }
        ajax.put("answer", answer);
        ajax.put("refs", refs);
        ajax.put("currentStep", ActTaskRegistry.currentStep(task.getStatus(), steps));
        ajax.put("progress", task.getProgress());
        ajax.putAll(meta);
        return ajax;
    }

    private void runActTask(Long userId, Long taskId, String goal, int maxSteps)
    {
        taskRegistry.markRunning(taskId);
        agentService.markRunning(taskId);

        String answer = "";
        List<Object> refs = List.of();
        int rounds = 0;
        int toolCallCount = 0;
        boolean truncated = false;
        String errorCode = null;
        String errorMessage = null;
        int stepCount = 0;
        boolean canceled = false;

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
                    // 用户点过停止：不再消费后续事件，直接退出
                    if (taskRegistry.isCanceled(taskId))
                    {
                        canceled = true;
                        break;
                    }
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
                        // 每步推进进度：5 → 95，完成时由 markSuccess 置 100
                        agentService.updateProgress(taskId, Math.min(95, 5 + stepCount * 8));
                    }
                    else if ("final".equals(type))
                    {
                        answer = node.path("answer").asText("");
                        rounds = node.path("rounds").asInt(0);
                        toolCallCount = node.path("toolCallCount").asInt(0);
                        truncated = node.path("truncated").asBoolean(false);
                        if (node.has("refs") && node.get("refs").isArray())
                        {
                            refs = objectMapper.convertValue(node.get("refs"), List.class);
                        }
                    }
                    else if ("error".equals(type))
                    {
                        errorCode = node.path("errorCode").asText("ACT_ERROR");
                        errorMessage = node.path("message").asText("任务执行失败");
                        rounds = node.path("rounds").asInt(rounds);
                        toolCallCount = node.path("toolCallCount").asInt(toolCallCount);
                        truncated = node.path("truncated").asBoolean(true);
                        if (node.has("refs") && node.get("refs").isArray())
                        {
                            refs = objectMapper.convertValue(node.get("refs"), List.class);
                        }
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

        try
        {
            if (canceled)
            {
                agentService.markCanceled(taskId);
                return;
            }
            if (errorCode != null)
            {
                String errorMsg = "[" + errorCode + "] "
                        + (errorMessage == null ? "" : errorMessage);
                // 失败也写 outputJson：前端能判断答案是否被截断、拿到已产生的来源
                Map<String, Object> output = new LinkedHashMap<>();
                output.put("answer", answer == null ? "" : answer);
                output.put("refs", refs);
                output.put("rounds", rounds);
                output.put("maxSteps", maxSteps);
                output.put("toolCallCount", toolCallCount);
                output.put("truncated", true);
                output.put("stepCount", stepCount);
                output.put("status", "FAILED");
                output.put("errorCode", errorCode);
                output.put("errorMessage", errorMessage == null ? "" : errorMessage);
                output.put("degraded", true);
                output.put("degradeReason", degradeReason(errorCode));
                agentService.markFailedWithOutput(taskId, errorMsg,
                        objectMapper.writeValueAsString(output));
                return;
            }
            // final 刚读完时用户又点了停止：以取消为准，不覆盖成 SUCCESS
            if (taskRegistry.isCanceled(taskId))
            {
                agentService.markCanceled(taskId);
                return;
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("answer", answer);
            output.put("refs", refs);
            output.put("rounds", rounds);
            output.put("maxSteps", maxSteps);
            output.put("toolCallCount", toolCallCount);
            output.put("truncated", truncated);
            output.put("stepCount", stepCount);
            output.put("status", errorCode == null ? "SUCCESS" : "FAILED");
            agentService.markSuccess(taskId, objectMapper.writeValueAsString(output));
        }
        catch (Exception e)
        {
            log.error("act 任务结果落库失败 taskId={}", taskId, e);
            agentService.markFailed(taskId, "结果序列化失败");
        }
        finally
        {
            taskRegistry.clear(taskId);
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

    /** FAILED/CANCELED 的默认键允许换键重跑；显式键保持严格幂等 */
    private boolean isRetryableTerminal(AgentTask task, boolean explicitKey)
    {
        if (explicitKey)
        {
            return false;
        }
        return "FAILED".equals(task.getStatus()) || "CANCELED".equals(task.getStatus());
    }

    private String defaultIdempotencyKey(Long userId, String goal)
    {
        long bucket = System.currentTimeMillis() / IDEMPOTENT_WINDOW_MS;
        return "act-" + sha1(userId + "|" + goal + "|" + bucket).substring(0, 24);
    }

    private String sha1(String raw)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes)
            {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            throw new IllegalStateException("幂等键生成失败", e);
        }
    }

    /** 失败/截断的降级说明，直接给前端展示 */
    private String degradeReason(String errorCode)
    {
        switch (errorCode)
        {
            case "STEP_LIMIT":
                return "达到推理轮次上限，结果不完整";
            case "TOOL_FAILED":
                return "连续工具失败，已停止并保留已完成步骤";
            case "ACT_TIMEOUT":
                return "任务总超时，结果不完整";
            case "LLM_ERROR":
                return "模型调用失败，任务中止";
            default:
                return "任务执行异常，已保留已完成步骤";
        }
    }

    private AjaxResult badRequest(String message)
    {
        AjaxResult ajax = AjaxResult.error(message);
        ajax.put("code", 400);
        return ajax;
    }
}
