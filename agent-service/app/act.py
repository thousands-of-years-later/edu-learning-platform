"""`/agent/act` 的 function calling 循环。

模型输出 tool_call → 执行 → observation 回填 → 再让模型决定继续或收尾。
事件通过 NDJSON 逐行吐出，Java 侧边读边落库，前端轮询步骤。

护栏：6 个工具白名单、参数业务校验、8 步上限（硬 10）、单工具超时、
单 act 最多 1 次视频总结 / 1 次 PPT、连续 2 次工具失败终止、总 300 秒。
"""
import asyncio
import json
import time
from typing import Any, AsyncIterator, Dict, List

from . import llm, tools

MAX_STEPS_HARD = 10
TOTAL_TIMEOUT_SECONDS = 300

SYSTEM_PROMPT = """你是学习平台的执行型智能体，通过调用工具完成用户目标。

可用工具：
- search_course：检索课程原文回答知识点问题
- ask_community：检索社区帖子回答经验类问题
- query_learning_stats：查询当前用户的学习统计
- summarize_video：总结单个视频并生成字幕
- generate_ppt：根据主题与素材生成 PPT 文件
- save_memory：写入用户明确要求长期记住的偏好

规则：
1. 先判断目标需要哪些工具，按依赖顺序调用；一次可以调用多个互不依赖的工具。
2. 参数必须来自用户输入或上一步工具的返回，不得编造 videoId、课程 ID。
3. 同一工具同一参数不要重复调用；工具返回失败时换方案或如实说明，不要假装成功。
4. generate_ppt 的 topic 只放标题（≤100 字），正文素材放 content；素材来自 summarize_video 的 summary。
5. 只有用户明确说“记住 / 以后都 / 我的偏好是”才调用 save_memory，临时参数不写。
6. 所有子目标完成或无法继续时，直接输出最终答复，不要再调用工具。
7. 最终答复用中文，简洁说明做了什么、产物在哪；不要复述内部参数和工具名堆砌。
"""


def _step(seq: int, step_type: str, **fields: Any) -> Dict[str, Any]:
    return {"type": "step", "seq": seq, "stepType": step_type, **fields}


def _parse_args(raw: str) -> Dict[str, Any]:
    try:
        value = json.loads(raw or "{}")
    except json.JSONDecodeError:
        return {}
    return value if isinstance(value, dict) else {}


async def stream_act(
    goal: str,
    user_id: int = 0,
    task_id: str = "",
    max_steps: int = 8,
) -> AsyncIterator[Dict[str, Any]]:
    goal = (goal or "").strip()
    if not goal:
        yield {"type": "error", "errorCode": "PARAM_INVALID", "message": "目标不能为空"}
        return

    max_steps = max(1, min(int(max_steps or 8), MAX_STEPS_HARD))
    messages: List[Dict[str, Any]] = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": goal},
    ]
    ctx = tools.ToolContext(user_id=user_id, task_id=task_id)
    started = time.monotonic()
    seq = 0
    rounds = 0
    failures = 0

    while rounds < max_steps:
        if time.monotonic() - started > TOTAL_TIMEOUT_SECONDS:
            yield {"type": "error", "errorCode": "ACT_TIMEOUT",
                   "message": f"任务超过 {TOTAL_TIMEOUT_SECONDS} 秒未完成"}
            return

        rounds += 1
        try:
            message = await llm.chat_tools(messages, tools.TOOLS)
        except Exception as exc:
            yield {"type": "error", "errorCode": "LLM_ERROR", "message": str(exc)[:300]}
            return

        content = (message.get("content") or "").strip()
        calls = message.get("tool_calls") or []

        if not calls:
            yield {"type": "final", "answer": content or "任务已完成。"}
            return

        seq += 1
        first_name = ((calls[0].get("function") or {}).get("name") or "工具")
        yield _step(seq, "thought", content=content or f"准备调用 {first_name}")

        messages.append({
            "role": "assistant",
            "content": content or None,
            "tool_calls": calls,
        })

        for call in calls:
            function = call.get("function") or {}
            name = function.get("name") or ""
            args = _parse_args(function.get("arguments") or "{}")

            seq += 1
            yield _step(seq, "tool", tool=name, args=args)

            tool_started = time.monotonic()
            try:
                result = await tools.execute(name, args, ctx)
                observation = {"ok": True, **result}
                status = "ok"
                failures = 0
            except tools.ToolError as exc:
                observation = {
                    "ok": False,
                    "errorCode": exc.code,
                    "message": exc.message,
                    "hint": exc.hint,
                }
                status = "error"
                failures += 1
            except Exception as exc:  # 未预期异常也要回填，避免循环中断
                observation = {"ok": False, "errorCode": "TOOL_ERROR",
                               "message": str(exc)[:200], "hint": ""}
                status = "error"
                failures += 1

            elapsed = int((time.monotonic() - tool_started) * 1000)
            seq += 1
            yield _step(seq, "observation", tool=name, status=status,
                        elapsedMs=elapsed, observation=observation)

            messages.append({
                "role": "tool",
                "tool_call_id": call.get("id") or f"call_{seq}",
                "content": json.dumps(observation, ensure_ascii=False, default=str),
            })

        if failures >= 2:
            yield {"type": "error", "errorCode": "TOOL_FAILED",
                   "message": "连续两次工具调用失败，已停止"}
            return

    yield {"type": "error", "errorCode": "STEP_LIMIT",
           "message": f"已达到最大步数 {max_steps}，任务未全部完成"}


async def run(goal: str, user_id: int = 0, task_id: str = "", max_steps: int = 8) -> Dict[str, Any]:
    """非流式入口：收集全部事件，返回 answer + steps。"""
    steps: List[Dict[str, Any]] = []
    answer = ""
    error = None
    async for event in stream_act(goal, user_id, task_id, max_steps):
        if event.get("type") == "step":
            steps.append(event)
        elif event.get("type") == "final":
            answer = event.get("answer", "")
        elif event.get("type") == "error":
            error = event
    return {
        "answer": answer,
        "steps": steps,
        "status": "error" if error else "success",
        "errorCode": (error or {}).get("errorCode"),
        "message": (error or {}).get("message", ""),
    }
