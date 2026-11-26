package com.ruoyi.web.controller.api;

import org.springframework.stereotype.Component;

import com.ruoyi.user.domain.AgentStep;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * act 任务的运行期取消标志。
 *
 * <p>还没接 Redis：单实例部署足够；多实例需要换成共享存储或让 Python 侧自己轮询 DB 状态。</p>
 */
@Component
public class ActTaskRegistry
{
    /** 工具名 → 前端进度文案 */
    private static final Map<String, String> TOOL_STEP_TEXT = Map.of(
            "search_course", "正在检索课程资料",
            "ask_community", "正在检索社区帖子",
            "query_learning_stats", "正在查询学习统计",
            "summarize_video", "正在总结视频并生成字幕",
            "generate_ppt", "正在生成 PPT",
            "save_memory", "正在写入长期记忆");

    private final Map<Long, Boolean> cancelFlags = new ConcurrentHashMap<>();

    /** 任务开始执行时注册，清掉上一轮的残留标记 */
    public void markRunning(Long taskId)
    {
        cancelFlags.put(taskId, Boolean.FALSE);
    }

    /** 用户点“停止” */
    public void requestCancel(Long taskId)
    {
        cancelFlags.put(taskId, Boolean.TRUE);
    }

    public boolean isCanceled(Long taskId)
    {
        return Boolean.TRUE.equals(cancelFlags.get(taskId));
    }

    /** 任务结束（成功/失败/取消）后释放 */
    public void clear(Long taskId)
    {
        cancelFlags.remove(taskId);
    }

    /**
     * 进度文案：按任务状态和最后一条步骤推断，供轮询接口直接展示。
     */
    public static String currentStep(String status, List<AgentStep> steps)
    {
        if ("SUCCESS".equals(status))
        {
            return "已完成";
        }
        if ("FAILED".equals(status))
        {
            return "执行失败";
        }
        if ("CANCELED".equals(status))
        {
            return "已取消";
        }
        if (steps == null || steps.isEmpty())
        {
            return "正在理解目标";
        }
        AgentStep last = steps.get(steps.size() - 1);
        if ("tool".equals(last.getStepType()))
        {
            return TOOL_STEP_TEXT.getOrDefault(last.getToolName(), "正在调用工具");
        }
        if ("observation".equals(last.getStepType()))
        {
            return "正在整理工具结果";
        }
        return "正在规划下一步";
    }
}
