"""DashScope Paraformer 录音文件识别（paraformer-v2）。"""
import asyncio
from typing import Any, Dict

import httpx

from . import config, oss_client


class AsrError(RuntimeError):
    """语音识别失败。message 只放可展示的中文原因，code 放错误码供日志/前端区分。"""

    def __init__(self, message: str, code: str = "ASR_ERROR"):
        super().__init__(message)
        self.code = code


# DashScope 错误码 → 用户能看懂的一句话，不要把原始 JSON 抛到界面
_ERROR_HINTS = {
    "FILE_DOWNLOAD_FAILED": "视频文件无法下载，可能已失效或不是公网可访问地址",
    "FILE_ACCESS_FAILED": "视频文件无法访问",
    "INVALID_FILE": "视频文件格式不支持",
    "FILE_DURATION_TOO_LONG": "视频时长超出识别上限",
    "FILE_SIZE_TOO_LARGE": "视频文件过大",
    "UNSUPPORTED_SAMPLE_RATE": "音频采样率不支持",
}


def _failure(output: Dict[str, Any], data: Dict[str, Any], default: str) -> AsrError:
    """从 DashScope 响应里取 code/message，转成简短中文，不带 request_id 等排查信息。"""
    code = str((output or {}).get("code") or (data or {}).get("code") or "ASR_ERROR")
    message = str((output or {}).get("message") or (data or {}).get("message") or "").strip()
    hint = _ERROR_HINTS.get(code) or message[:80] or default
    return AsrError(hint, code)


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
            raise AsrError("语音识别服务调用失败", f"HTTP_{response.status_code}")
        task_id = response.json().get("output", {}).get("task_id")
        if not task_id:
            raise AsrError("语音识别服务没有返回任务编号", "NO_TASK_ID")

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
                raise _failure(output, data, "语音识别任务失败")
            await asyncio.sleep(2)
        if result is None:
            raise AsrError("语音识别超时，请稍后重试", "TIMEOUT")

        transcription_url = _transcription_url(result)
        if not transcription_url:
            raise AsrError("语音识别没有返回转写结果", "NO_TRANSCRIPTION_URL")
        transcript_response = await client.get(transcription_url, timeout=60)
        if transcript_response.status_code >= 400:
            raise AsrError("转写结果下载失败", f"HTTP_{transcript_response.status_code}")
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
        raise AsrError("没有识别出有效语音内容", "EMPTY_TRANSCRIPT")
    return {"text": full_text, "sentences": sentences, "fileUrl": file_url}


def _transcription_url(output: Dict[str, Any]) -> str:
    for result in output.get("results") or []:
        inner = result.get("output") or result
        url = inner.get("transcription_url")
        if url:
            return url
    return ""
