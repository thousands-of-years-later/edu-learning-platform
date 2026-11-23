"""DeepSeek chat + DashScope embedding 的轻量客户端。"""
import asyncio
import json
from typing import Any, Dict, List, Optional

import httpx

from . import config


class LlmError(RuntimeError):
    pass


async def _post_with_retry(url: str, headers: Dict[str, str], payload: Dict[str, Any]) -> Dict[str, Any]:
    last_error: Optional[Exception] = None
    for attempt in range(config.LLM_RETRIES + 1):
        try:
            async with httpx.AsyncClient(timeout=config.LLM_TIMEOUT_SECONDS) as client:
                response = await client.post(url, headers=headers, json=payload)
            if response.status_code >= 400:
                raise LlmError(f"HTTP {response.status_code}: {response.text[:300]}")
            return response.json()
        except Exception as exc:  # 网络错误/429/5xx 都重试
            last_error = exc
            if attempt < config.LLM_RETRIES:
                await asyncio.sleep(1.0 * (attempt + 1))
    raise LlmError(f"模型调用失败: {last_error}")


async def chat(
    messages: List[Dict[str, str]],
    temperature: float = 0.2,
    max_tokens: int = 1200,
) -> str:
    config.require("DEEPSEEK_API_KEY", config.DEEPSEEK_API_KEY)
    data = await _post_with_retry(
        f"{config.DEEPSEEK_BASE_URL}/chat/completions",
        {
            "Authorization": f"Bearer {config.DEEPSEEK_API_KEY}",
            "Content-Type": "application/json",
        },
        {
            "model": config.DEEPSEEK_MODEL,
            "messages": messages,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "stream": False,
        },
    )
    try:
        return data["choices"][0]["message"]["content"] or ""
    except (KeyError, IndexError) as exc:
        raise LlmError(f"模型返回格式异常: {data}") from exc


async def chat_stream(
    messages: List[Dict[str, str]],
    temperature: float = 0.2,
    max_tokens: int = 1200,
):
    """流式调用 DeepSeek，逐段 yield 文本增量。"""
    config.require("DEEPSEEK_API_KEY", config.DEEPSEEK_API_KEY)
    headers = {
        "Authorization": f"Bearer {config.DEEPSEEK_API_KEY}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": config.DEEPSEEK_MODEL,
        "messages": messages,
        "temperature": temperature,
        "max_tokens": max_tokens,
        "stream": True,
    }
    timeout = httpx.Timeout(connect=10.0, read=120.0, write=30.0, pool=10.0)
    async with httpx.AsyncClient(timeout=timeout) as client:
        async with client.stream("POST", f"{config.DEEPSEEK_BASE_URL}/chat/completions",
                                 headers=headers, json=payload) as response:
            if response.status_code >= 400:
                body = await response.aread()
                raise LlmError(f"HTTP {response.status_code}: {body[:300]!r}")
            async for line in response.aiter_lines():
                if not line or not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if data == "[DONE]":
                    break
                try:
                    obj = json.loads(data)
                    delta = obj["choices"][0].get("delta", {}).get("content")
                except (json.JSONDecodeError, KeyError, IndexError):
                    continue
                if delta:
                    yield delta



async def embed(texts: List[str]) -> List[List[float]]:
    """DashScope OpenAI 兼容 embeddings，按 10 条一批。"""
    config.require("DASHSCOPE_API_KEY", config.DASHSCOPE_API_KEY)
    if not texts:
        return []

    vectors: List[List[float]] = []
    for start in range(0, len(texts), 10):
        batch = texts[start:start + 10]
        data = await _post_with_retry(
            f"{config.DASHSCOPE_BASE_URL}/embeddings",
            {
                "Authorization": f"Bearer {config.DASHSCOPE_API_KEY}",
                "Content-Type": "application/json",
            },
            {
                "model": config.DASHSCOPE_EMBEDDING_MODEL,
                "input": batch,
                "encoding_format": "float",
            },
        )
        rows = data.get("data") or []
        if len(rows) != len(batch):
            raise LlmError(f"embedding 返回条数不匹配: {len(rows)} != {len(batch)}")
        rows.sort(key=lambda item: item.get("index", 0))
        vectors.extend([row["embedding"] for row in rows])
    return vectors


def extract_json(text: str) -> Dict[str, Any]:
    """从模型输出中提取 JSON（允许 ```json 包裹）。"""
    raw = (text or "").strip()
    if raw.startswith("```"):
        raw = raw.split("\n", 1)[1] if "\n" in raw else raw
        if raw.endswith("```"):
            raw = raw[:-3]
    start = raw.find("{")
    end = raw.rfind("}")
    if start < 0 or end < start:
        raise LlmError(f"模型没有返回 JSON: {text[:200]}")
    try:
        return json.loads(raw[start:end + 1])
    except json.JSONDecodeError as exc:
        raise LlmError(f"JSON 解析失败: {exc}") from exc
