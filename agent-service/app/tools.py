"""智能体工具注册表与执行器。

只暴露 6 个工具，schema 同时用于 function calling 与服务端校验。
工具本身没有副作用（除 save_memory 会回调 Java 写库、PPT 会生成文件），
副作用类工具在 act 循环里另有次数上限。
"""
import asyncio
import json
import time
from typing import Any, Dict, List, Optional

import httpx

from . import community_rag, config, ppt_agent, rag, sql_agent, video_agent


class ToolError(Exception):
    """工具执行失败：code 供模型决策，hint 是给模型的下一步建议。"""

    def __init__(self, code: str, message: str, hint: str = ""):
        super().__init__(message)
        self.code = code
        self.message = message
        self.hint = hint


TOOLS: List[Dict[str, Any]] = [
    {
        "type": "function",
        "function": {
            "name": "search_course",
            "description": (
                "检索平台课程原文并据此回答。用于课程知识点、概念解释、代码写法、章节内容相关问题。"
                "不用于学习统计、社区帖子或文件生成。"
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string", "minLength": 1, "maxLength": 300,
                              "description": "学员问题原文，保留技术关键词"},
                    "courseId": {"type": "integer", "minimum": 1,
                                 "description": "限定课程 ID；用户没点名课程时不要传"},
                    "topK": {"type": "integer", "minimum": 1, "maximum": 10, "default": 5},
                },
                "required": ["query"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "ask_community",
            "description": (
                "检索社区已发布帖子并汇总学员经验。用于经验、踩坑、做法讨论类问题。"
                "不作为课程官方答案，不用于统计或文件生成。"
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "question": {"type": "string", "minLength": 1, "maxLength": 300},
                    "topK": {"type": "integer", "minimum": 1, "maximum": 10, "default": 5},
                },
                "required": ["question"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "query_learning_stats",
            "description": (
                "查询当前用户的学习统计数据。用于学习进度、完成率、签到、评论/笔记数量等平台数据问题。"
                "不用于课程知识或社区经验问答。"
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "question": {"type": "string", "minLength": 1, "maxLength": 200,
                                 "description": "统计问题原文，含时间范围"},
                },
                "required": ["question"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "summarize_video",
            "description": (
                "对指定视频生成内容总结与字幕。用于单个视频/课时的内容概括。"
                "不用于整门课程问答或文件生成。单个视频只调用一次。"
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "videoId": {"type": "integer", "minimum": 1,
                                "description": "视频 ID，必须来自用户输入或上下文，不得猜测"},
                },
                "required": ["videoId"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "generate_ppt",
            "description": (
                "根据主题与素材生成 PPT 文件。用于明确的课件、汇报、演示文稿需求。"
                "topic 只做标题，长素材放 content。"
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "topic": {"type": "string", "minLength": 1, "maxLength": 100,
                              "description": "PPT 标题，最多 100 字"},
                    "content": {"type": "string", "maxLength": 8000,
                                "description": "正文素材，最多 8000 字；可为空"},
                    "videoId": {"type": "integer", "minimum": 1,
                                "description": "素材来源视频 ID，可选"},
                },
                "required": ["topic"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "save_memory",
            "description": (
                "把用户明确要求长期记住的偏好或事实写入长期记忆。"
                "仅在用户说出“记住/以后都/我的偏好是”时调用；临时参数与模型推断不得写入。"
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "content": {"type": "string", "minLength": 1, "maxLength": 500,
                                "description": "一条独立、可复用的事实或偏好，陈述句"},
                },
                "required": ["content"],
                "additionalProperties": False,
            },
        },
    },
]

TIMEOUTS = {
    "search_course": 15,
    "ask_community": 25,
    "query_learning_stats": 20,
    "summarize_video": 120,
    "generate_ppt": 120,
    "save_memory": 10,
}

MAX_CALLS = {"summarize_video": 1, "generate_ppt": 1}

# 敏感信息不写记忆：手机号 / 身份证 / 邮箱 / 密钥
_SENSITIVE = ("手机号", "身份证", "银行卡", "密码", "密钥", "住址", "手机号码")


class ToolContext:
    """一次 act 的执行上下文（次数与成本计数）。"""

    def __init__(self, user_id: int = 0, task_id: str = ""):
        self.user_id = user_id
        self.task_id = task_id
        self.calls: Dict[str, int] = {}


def _param(args: Dict[str, Any], key: str) -> Any:
    if not isinstance(args, dict):
        raise ToolError("PARAM_INVALID", "工具参数必须是对象")
    value = args.get(key)
    if value is None or (isinstance(value, str) and not value.strip()):
        raise ToolError("PARAM_INVALID", f"缺少参数 {key}")
    return value


def _positive_int(args: Dict[str, Any], key: str) -> int:
    value = _param(args, key)
    try:
        number = int(value)
    except (TypeError, ValueError):
        raise ToolError("PARAM_INVALID", f"{key} 必须是整数")
    if number <= 0:
        raise ToolError("PARAM_INVALID", f"{key} 必须大于 0")
    return number


async def _search_course(args: Dict[str, Any], ctx: ToolContext) -> Dict[str, Any]:
    query = str(_param(args, "query")).strip()
    if len(query) > 300:
        raise ToolError("PARAM_INVALID", "query 最长 300 字")
    course_id = args.get("courseId")
    if course_id is not None:
        course_id = _positive_int(args, "courseId")
    result = await rag.ask(course_id, query, [], [], [])
    return {
        "answer": result.get("answer", ""),
        "matched": int(result.get("retrieved", 0)) > 0,
        "chunkCount": int(result.get("retrieved", 0)),
        "refs": result.get("refs", []),
    }


async def _ask_community(args: Dict[str, Any], ctx: ToolContext) -> Dict[str, Any]:
    question = str(_param(args, "question")).strip()
    if len(question) > 300:
        raise ToolError("PARAM_INVALID", "question 最长 300 字")
    result = await community_rag.ask(question, [])
    return {
        "answer": result.get("answer", ""),
        "matched": int(result.get("retrieved", 0)) > 0,
        "refs": result.get("refs", []),
    }


async def _query_learning_stats(args: Dict[str, Any], ctx: ToolContext) -> Dict[str, Any]:
    question = str(_param(args, "question")).strip()
    if len(question) > 200:
        raise ToolError("PARAM_INVALID", "question 最长 200 字")
    result = await sql_agent.ask(question)
    rows = result.get("rows") or []
    return {
        "answer": result.get("answer", ""),
        "matched": not result.get("error", False),
        "rowCount": int(result.get("rowCount", len(rows))),
        "rows": rows[:10],
        # sql 只进审计（Java 落 step 表），前端步骤展示应隐藏
        "sql": result.get("sql", ""),
    }


async def _summarize_video(args: Dict[str, Any], ctx: ToolContext) -> Dict[str, Any]:
    video_id = _positive_int(args, "videoId")
    result = await video_agent.summary(video_id)
    summary = str(result.get("summary") or "")
    return {
        "videoId": video_id,
        "videoTitle": result.get("videoTitle", ""),
        "summary": summary[:2500],
        "summaryChars": len(summary),
        "asr": bool(result.get("asr", False)),
        "asrErrorCode": result.get("asrErrorCode"),
        "subtitleUrl": result.get("subtitleUrl"),
        "transcriptChars": result.get("transcriptChars", 0),
    }


async def _generate_ppt(args: Dict[str, Any], ctx: ToolContext) -> Dict[str, Any]:
    topic = str(_param(args, "topic")).strip()
    if len(topic) > 100:
        raise ToolError("PARAM_INVALID", f"PPT 主题最长 100 字（当前 {len(topic)} 字）",
                        "缩短 topic，正文素材放到 content")
    content = str(args.get("content") or "").strip()
    if len(content) > 8000:
        raise ToolError("PARAM_INVALID", f"PPT 素材最长 8000 字（当前 {len(content)} 字）",
                        "精简 content 后重试")
    video_id = args.get("videoId")
    if video_id is not None:
        video_id = _positive_int(args, "videoId")
    index = ctx.calls.get("generate_ppt", 0) + 1
    task_key = f"{ctx.task_id or 'act'}-ppt-{index}"
    result = await ppt_agent.generate(topic, 6, ctx.user_id, task_key, content, video_id)
    return {
        "url": result.get("url"),
        "fileName": result.get("fileName"),
        "pageCount": result.get("pageCount"),
        "slides": result.get("slides", []),
    }


async def _save_memory(args: Dict[str, Any], ctx: ToolContext) -> Dict[str, Any]:
    content = str(_param(args, "content")).strip()
    if len(content) > 500:
        raise ToolError("PARAM_INVALID", f"记忆最长 500 字（当前 {len(content)} 字）")
    if any(word in content for word in _SENSITIVE):
        raise ToolError("PARAM_INVALID", "记忆内容包含敏感信息，不能保存",
                        "只保存学习偏好，不要保存手机号/身份证/密钥等")
    url = f"{config.BACKEND_INTERNAL_URL}/internal/agent/memory"
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            response = await client.post(
                url,
                headers={"x-internal-token": config.AGENT_INTERNAL_TOKEN},
                json={"userId": ctx.user_id, "content": content, "source": "agent"},
            )
    except Exception as exc:
        raise ToolError("TOOL_ERROR", f"记忆服务不可用: {exc}", "稍后再试或直接告诉用户稍后手动保存")
    if response.status_code >= 400:
        detail = response.text[:200]
        raise ToolError("TOOL_ERROR", f"记忆保存失败（{response.status_code}）: {detail}")
    data = response.json()
    return {"memoryId": data.get("memoryId"), "content": data.get("content", content)}


_HANDLERS = {
    "search_course": _search_course,
    "ask_community": _ask_community,
    "query_learning_stats": _query_learning_stats,
    "summarize_video": _summarize_video,
    "generate_ppt": _generate_ppt,
    "save_memory": _save_memory,
}


async def execute(name: str, args: Dict[str, Any], ctx: ToolContext) -> Dict[str, Any]:
    """执行工具；失败抛 ToolError，调用方负责把错误作为 observation 回填。"""
    handler = _HANDLERS.get(name)
    if handler is None:
        raise ToolError("TOOL_NOT_ALLOWED", f"不允许调用工具 {name}")

    limit = MAX_CALLS.get(name)
    if limit is not None and ctx.calls.get(name, 0) >= limit:
        raise ToolError("COST_LIMIT", f"{name} 单次任务最多调用 {limit} 次")

    started = time.monotonic()
    try:
        result = await asyncio.wait_for(handler(args or {}, ctx), timeout=TIMEOUTS[name])
    except ToolError:
        raise
    except asyncio.TimeoutError:
        raise ToolError("TOOL_TIMEOUT", f"{name} 执行超时（{TIMEOUTS[name]}s）",
                        "换用其他工具或直接说明该步骤未完成")
    except ValueError as exc:
        raise ToolError("PARAM_INVALID", str(exc)[:200])
    except Exception as exc:
        # 依赖服务超时/断连/返回异常等非参数错误：归 TOOL_ERROR，交给 act 层重试一次
        raise ToolError("TOOL_ERROR", f"{name} 执行失败：{str(exc)[:160]}",
                        "依赖服务异常，已自动重试一次；仍失败请换方案或如实说明")
    finally:
        elapsed = int((time.monotonic() - started) * 1000)
        if elapsed > 200:
            # 超时/慢调用留痕，便于排查
            print(f"[tool] {name} {elapsed}ms", flush=True)

    ctx.calls[name] = ctx.calls.get(name, 0) + 1
    return result
