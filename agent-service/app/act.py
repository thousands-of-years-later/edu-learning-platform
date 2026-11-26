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
MAX_REFS = 8
# 只重试非超时错误：超时再跑一次会把等待翻倍，交给模型换方案
RETRYABLE = {"TOOL_ERROR"}

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
3. 不要反问“要不要 / 是否需要 / 是否继续”。只要能从目标、上下文或已有素材推出合理默认值，就直接执行到底。
4. PPT 素材只有章节标题、大纲或片段时间时，先出框架版（cover/contents/section/highlights/keyPoints/closing），并在最终答复里注明“素材仅为章节标题/大纲，可补充后重做”；只有完全没有任何可用素材且无法合理默认时，才说明缺什么并停止。
5. 同一工具同一参数不要重复调用；工具返回失败时换方案或如实说明，不要假装成功。
6. generate_ppt 的 topic 只放标题（≤100 字），正文素材放 content；素材来自 summarize_video 的 summary 或用户给定大纲。
7. 只有用户明确说“记住 / 以后都 / 我的偏好是”才调用 save_memory，临时参数不写。
8. 所有子目标完成或无法继续时，直接输出最终答复，不要再调用工具。
9. 最终答复用中文，简洁说明做了什么、产物在哪，并列出使用的来源（课程/帖子）；不要堆砌工具名。
10. 最终答复不要以问句结尾，不要问“是否需要/要不要我继续/还需要我做什么”；需要补充材料时直接说明缺什么和默认处理方式。
"""


def _step(seq: int, step_type: str, **fields: Any) -> Dict[str, Any]:
    return {"type": "step", "seq": seq, "stepType": step_type, **fields}


def _parse_args(raw: str) -> Dict[str, Any]:
    try:
        value = json.loads(raw or "{}")
    except json.JSONDecodeError:
        return {}
    return value if isinstance(value, dict) else {}


def _dedup_refs(refs: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    seen = set()
    result: List[Dict[str, Any]] = []
    for ref in refs:
        if not isinstance(ref, dict):
            continue
        key = ref.get("postId") or ref.get("courseId") or ref.get("chapterTitle")
        if key is None or key in seen:
            continue
        seen.add(key)
        result.append(ref)
        if len(result) >= MAX_REFS:
            break
    return result


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
    tool_calls_total = 0
    refs: List[Dict[str, Any]] = []

    def done_event(answer: str, truncated: bool) -> Dict[str, Any]:
        return {
            "type": "final",
            "answer": answer,
            "rounds": rounds,
            "maxSteps": max_steps,
            "toolCallCount": tool_calls_total,
            "truncated": truncated,
            "refs": _dedup_refs(refs),
        }

    def stop_event(code: str, message: str) -> Dict[str, Any]:
        return {
            "type": "error",
            "errorCode": code,
            "message": message,
            "rounds": rounds,
            "maxSteps": max_steps,
            "toolCallCount": tool_calls_total,
            "truncated": True,
            "refs": _dedup_refs(refs),
        }

    while rounds < max_steps:
        if time.monotonic() - started > TOTAL_TIMEOUT_SECONDS:
            yield stop_event("ACT_TIMEOUT", f"任务超过 {TOTAL_TIMEOUT_SECONDS} 秒未完成")
            return

        rounds += 1
        try:
            message = await llm.chat_tools(messages, tools.TOOLS)
        except Exception as exc:
            yield stop_event("LLM_ERROR", str(exc)[:300])
            return

        content = (message.get("content") or "").strip()
        calls = message.get("tool_calls") or []

        if not calls:
            yield done_event(content or "任务已完成。", False)
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
            tool_calls_total += 1
            yield _step(seq, "tool", tool=name, args=args)

            tool_started = time.monotonic()
            attempts = 0
            while True:
                attempts += 1
                try:
                    result = await tools.execute(name, args, ctx)
                    observation = {"ok": True, "attempts": attempts, **result}
                    status = "ok"
                    failures = 0
                    break
                except tools.ToolError as exc:
                    # save_memory 不重试：首次可能已写入成功，重试会产生重复记忆
                    if attempts < 2 and exc.code in RETRYABLE and name != "save_memory":
                        continue
                    observation = {
                        "ok": False,
                        "errorCode": exc.code,
                        "errorMsg": exc.message,
                        "message": exc.message,
                        "hint": exc.hint,
                        "attempts": attempts,
                    }
                    status = "error"
                    failures += 1
                    break
                except Exception as exc:  # 未预期异常也要回填，避免循环中断
                    observation = {"ok": False, "errorCode": "TOOL_ERROR", "errorMsg": str(exc)[:200],
                                   "message": str(exc)[:200], "hint": "", "attempts": attempts}
                    status = "error"
                    failures += 1
                    break

            if observation.get("ok") and isinstance(observation.get("refs"), list):
                refs.extend(observation["refs"])

            elapsed = int((time.monotonic() - tool_started) * 1000)
            seq += 1
            yield _step(seq, "observation", tool=name, status=status,
                        elapsedMs=elapsed, attempts=attempts, observation=observation)

            messages.append({
                "role": "tool",
                "tool_call_id": call.get("id") or f"call_{seq}",
                "content": json.dumps(observation, ensure_ascii=False, default=str),
            })

        if failures >= 2:
            yield stop_event("TOOL_FAILED", "连续两次工具调用失败，已停止")
            return

    yield stop_event("STEP_LIMIT", f"已达到最大推理轮次 {max_steps}，任务未全部完成")


async def run(goal: str, user_id: int = 0, task_id: str = "", max_steps: int = 8) -> Dict[str, Any]:
    """非流式入口：收集全部事件，返回 answer + steps + 运行元数据。"""
    steps: List[Dict[str, Any]] = []
    answer = ""
    error = None
    meta: Dict[str, Any] = {}
    refs: List[Dict[str, Any]] = []
    async for event in stream_act(goal, user_id, task_id, max_steps):
        if event.get("type") == "step":
            steps.append(event)
        elif event.get("type") == "final":
            answer = event.get("answer", "")
            meta = event
            refs = event.get("refs", [])
        elif event.get("type") == "error":
            error = event
            meta = event
            refs = event.get("refs", [])
    return {
        "answer": answer,
        "steps": steps,
        "refs": refs,
        "rounds": meta.get("rounds", 0),
        "maxSteps": meta.get("maxSteps", max_steps),
        "toolCallCount": meta.get("toolCallCount", 0),
        "truncated": bool(meta.get("truncated", False)),
        "status": "error" if error else "success",
        "errorCode": (error or {}).get("errorCode"),
        "message": (error or {}).get("message", ""),
    }
