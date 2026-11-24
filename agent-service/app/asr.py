"""DashScope Paraformer 录音文件识别（paraformer-v2）。"""
import asyncio
import json
from typing import Any, Dict

import httpx

from . import config, oss_client


class AsrError(RuntimeError):
    pass


async def transcribe(video_url: str) -> Dict[str, Any]:
    config.require("DASHSCOPE_API_KEY", config.DASHSCOPE_API_KEY)
    file_url = oss_client.sign_url(video_url, expires=3600)

    headers = {
        "Authorization": f"Bearer {config.DASHSCOPE_API_KEY}",
        "Content-Type": "application/json",
        "X-DashScope-Async": "enable",
    }
    payload = {
        "model": config.ASR_MODEL,
        "input": {"file_urls": [file_url]},
        "parameters": {
            "channel_id": [0],
            "language_hints": ["zh", "en"],
            "timestamp_alignment_enabled": True,
        },
    }

    async with httpx.AsyncClient(timeout=60) as client:
        response = await client.post(
            f"{config.ASR_BASE_URL}/services/audio/asr/transcription",
            headers=headers,
            json=payload,
        )
        if response.status_code >= 400:
            raise AsrError(f"ASR 提交失败 {response.status_code}: {response.text[:300]}")
        task_id = response.json().get("output", {}).get("task_id")
        if not task_id:
            raise AsrError(f"ASR 没有返回 task_id: {response.text[:300]}")

        deadline = asyncio.get_running_loop().time() + config.ASR_TIMEOUT_SECONDS
        result = None
        while asyncio.get_running_loop().time() < deadline:
            poll = await client.post(
                f"{config.ASR_BASE_URL}/tasks/{task_id}",
                headers={"Authorization": headers["Authorization"]},
            )
            data = poll.json()
            output = data.get("output") or {}
            status = output.get("task_status")
            if status == "SUCCEEDED":
                result = output
                break
            if status in ("FAILED", "CANCELED"):
                raise AsrError(f"ASR 任务失败: {json.dumps(data, ensure_ascii=False)[:500]}")
            await asyncio.sleep(2)
        if result is None:
            raise AsrError("ASR 任务超时")

        transcription_url = _transcription_url(result)
        if not transcription_url:
            raise AsrError(f"ASR 没有返回转写地址: {json.dumps(result, ensure_ascii=False)[:500]}")
        transcript_response = await client.get(transcription_url, timeout=60)
        if transcript_response.status_code >= 400:
            raise AsrError(f"下载转写结果失败 {transcript_response.status_code}")
        transcript = transcript_response.json()

    texts = []
    sentences = []
    for channel in transcript.get("transcripts") or []:
        text = (channel.get("text") or "").strip()
        if text:
            texts.append(text)
        for sentence in channel.get("sentences") or []:
            sentences.append(
                {
                    "begin": sentence.get("begin_time"),
                    "end": sentence.get("end_time"),
                    "text": sentence.get("text", ""),
                }
            )

    full_text = "\n".join(texts).strip()
    if not full_text:
        raise AsrError("ASR 转写结果为空")
    return {"text": full_text, "sentences": sentences, "fileUrl": file_url}


def _transcription_url(output: Dict[str, Any]) -> str:
    for result in output.get("results") or []:
        inner = result.get("output") or result
        url = inner.get("transcription_url")
        if url:
            return url
    return ""
