"""视频总结 / 字幕：Paraformer ASR + 分段摘要；无 URL/ASR 失败时回退到元数据摘要。

字幕（SRT/VTT）复用同一次 ASR 的句级时间戳，生成后上传 OSS 并返回可下载地址。
"""
import logging
import os
import re
import tempfile
from typing import Any, Dict, List, Optional
from urllib.parse import quote

from sqlalchemy import text

from . import asr, config, db, llm, oss_client

logger = logging.getLogger(__name__)

SUMMARY_SYSTEM = """你是学习平台的视频总结助手。请根据视频转写文本输出中文总结。
结构：
1. 一句话总结
2. 分段大纲（按内容顺序）
3. 关键知识点（3~6 条）
4. 复习思考题（2~3 条）
只依据转写文本，不要编造；文本是英文时可以翻成中文再总结。
"""

SUBTITLE_PREFIX = "edu/subtitle"
MIN_TRANSCRIPT_CHARS = 20


def _load_video(video_id: int) -> Any:
    with db.engine().connect() as conn:
        row = conn.execute(
            text(
                """
                select video_id, course_id, course_name, chapter_title,
                       video_title, duration_min, video_url
                  from v_agent_course_video
                 where video_id = :video_id
                """
            ),
            {"video_id": video_id},
        ).mappings().first()
    if not row:
        raise ValueError("视频不存在或已删除")
    return row


def _base(row: Any) -> Dict[str, Any]:
    return {
        "videoId": int(row["video_id"]),
        "courseId": int(row["course_id"]),
        "videoTitle": row["video_title"],
        "durationMinutes": float(row["duration_min"] or 0),
    }


def _asr_url(row: Any) -> str:
    """校验并编码视频地址：相对路径下载不到，中文/空格需百分号编码。"""
    video_url = (row["video_url"] or "").strip()
    if not video_url:
        raise ValueError("视频缺少可访问地址")
    if not video_url.lower().startswith(("http://", "https://")):
        raise ValueError("视频地址不是公网可访问地址，无法进行语音识别")
    return quote(video_url, safe=":/?&=%#")


async def summary(video_id: int) -> Dict[str, Any]:
    row = _load_video(video_id)
    base = _base(row)

    try:
        video_url = _asr_url(row)
    except ValueError as exc:
        return {**base, **_fallback(row, str(exc))}

    try:
        transcript = await asr.transcribe(video_url)
        transcript_text = transcript["text"].strip()
        if len(transcript_text) < MIN_TRANSCRIPT_CHARS:
            return {**base, **_fallback(row, "ASR 转写内容过短")}
        if _is_english_test_audio(row["video_title"], transcript_text):
            fallback = _fallback(row, "ASR 转写内容是英文测试素材，与课程标题不一致")
            fallback.update({"asr": True, "transcriptChars": len(transcript_text)})
            return {**base, **fallback}

        summary_text = await _summarize(transcript_text)
        result = {
            **base,
            "summary": summary_text,
            "transcriptChars": len(transcript_text),
            "asr": True,
            "fallback": False,
        }
        # 同一份 ASR 结果顺带产出字幕；字幕失败不影响总结
        try:
            result.update(_publish_subtitle(video_id, transcript["sentences"]))
        except Exception as exc:
            logger.warning("字幕生成失败 videoId=%s: %s", video_id, exc)
        return result
    except Exception as exc:
        logger.warning("视频总结 ASR 失败 videoId=%s: %s", video_id, exc, exc_info=True)
        return {
            **base,
            **_fallback(row, _clean_reason(str(exc))),
            "asrErrorCode": getattr(exc, "code", "ASR_ERROR"),
        }


async def subtitle(video_id: int, force: bool = False) -> Dict[str, Any]:
    """只生成字幕（不调用大模型总结）。已生成过则直接返回 OSS 地址。"""
    row = _load_video(video_id)
    base = _base(row)

    if not force:
        cached = _cached_subtitle(video_id)
        if cached:
            return {**base, **cached, "cached": True, "asr": True}

    try:
        video_url = _asr_url(row)
    except ValueError as exc:
        raise ValueError(str(exc))

    transcript = await asr.transcribe(video_url)
    sentences = transcript["sentences"]
    if not (transcript["text"] or "").strip() or not sentences:
        raise ValueError("没有识别出有效语音内容")
    return {**base, **_publish_subtitle(video_id, sentences), "transcriptChars": len(transcript["text"].strip()), "cached": False, "asr": True}


def _publish_subtitle(video_id: int, sentences: List[Dict[str, Any]]) -> Dict[str, Any]:
    srt = build_srt(sentences)
    vtt = build_vtt(sentences)
    srt_url = _upload_text(srt, f"{SUBTITLE_PREFIX}/video-{video_id}.srt")
    vtt_url = _upload_text(vtt, f"{SUBTITLE_PREFIX}/video-{video_id}.vtt")
    return {
        "subtitleUrl": srt_url,
        "subtitleVttUrl": vtt_url,
        "subtitleFormat": "srt",
        "subtitleSegments": len([s for s in sentences if (s.get("text") or "").strip()]),
    }


def _upload_text(content: str, key: str) -> str:
    fd, path = tempfile.mkstemp(suffix=os.path.splitext(key)[1])
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            handle.write(content)
        return oss_client.upload_file(path, key)
    finally:
        try:
            os.remove(path)
        except OSError:
            pass


def _cached_subtitle(video_id: int) -> Optional[Dict[str, Any]]:
    key = f"{SUBTITLE_PREFIX}/video-{video_id}.srt"
    if not oss_client.exists(key):
        return None
    segments = None
    try:
        segments = oss_client.read_text(key).count("-->")
    except Exception as exc:
        logger.warning("读取已生成字幕失败 videoId=%s: %s", video_id, exc)
    host = config.OSS_ENDPOINT.replace("https://", "").replace("http://", "").rstrip("/")
    base = f"https://{config.OSS_BUCKET}.{host}/{key}"
    return {
        "subtitleUrl": base,
        "subtitleVttUrl": base[:-4] + ".vtt",
        "subtitleFormat": "srt",
        "subtitleSegments": segments,
    }


def _ms(value: Any) -> int:
    try:
        return max(0, int(value or 0))
    except (TypeError, ValueError):
        return 0


def _stamp(value: Any, sep: str = ",") -> str:
    ms = _ms(value)
    hours, rest = divmod(ms, 3600000)
    minutes, rest = divmod(rest, 60000)
    seconds, millis = divmod(rest, 1000)
    return f"{hours:02d}:{minutes:02d}:{seconds:02d}{sep}{millis:03d}"


def build_srt(sentences: List[Dict[str, Any]]) -> str:
    blocks = []
    for sentence in sentences:
        content = (sentence.get("text") or "").strip()
        if not content:
            continue
        begin, end = _ms(sentence.get("begin")), _ms(sentence.get("end"))
        if end <= begin:
            end = begin + 1000
        blocks.append(f"{len(blocks) + 1}\n{_stamp(begin)} --> {_stamp(end)}\n{content}")
    return "\n\n".join(blocks) + ("\n" if blocks else "")


def build_vtt(sentences: List[Dict[str, Any]]) -> str:
    blocks = ["WEBVTT"]
    for sentence in sentences:
        content = (sentence.get("text") or "").strip()
        if not content:
            continue
        begin, end = _ms(sentence.get("begin")), _ms(sentence.get("end"))
        if end <= begin:
            end = begin + 1000
        blocks.append(f"{_stamp(begin, '.')} --> {_stamp(end, '.')}\n{content}")
    return "\n\n".join(blocks) + "\n"


def _clean_reason(reason: str) -> str:
    """降级文案只给一句原因：去掉 JSON、request_id 等排查信息。"""
    text = re.sub(r"\{.*\}", "", str(reason or ""), flags=re.S)
    text = re.sub(r"[\"']?request_id[\"']?\s*[:=]\s*[\"']?[\w-]+[\"']?", "", text)
    text = re.sub(r"[\"']?code[\"']?\s*[:=]\s*[\"']?[\w.-]+[\"']?", "", text)
    text = re.sub(r"\s+", " ", text).strip(" ：:，,")
    if len(text) > 80:
        text = text[:80] + "…"
    return text or "语音识别服务暂时不可用"


def _fallback(row: Any, reason: str) -> Dict[str, Any]:
    return {
        "summary": (
            f"《{row['video_title']}》\n"
            f"所属课程：{row['course_name']}\n"
            f"章节：{row['chapter_title'] or '未分章'}\n"
            f"时长：{row['duration_min'] or 0} 分钟\n\n"
            f"暂时无法生成基于语音内容的总结（{reason}）。"
        ),
        "asr": False,
        "fallback": True,
    }


def _is_english_test_audio(title: str, transcript: str) -> bool:
    """课程标题是中文、转写却几乎全是英文时，视为测试素材，避免输出无关总结。"""
    has_chinese_title = any("\u4e00" <= ch <= "\u9fff" for ch in (title or ""))
    letters = [ch for ch in transcript if ch.isalpha()]
    if not has_chinese_title or not letters:
        return False
    ascii_letters = sum(1 for ch in letters if ch.isascii())
    return ascii_letters / len(letters) > 0.7


async def _summarize(text: str) -> str:
    # 长文本分段摘要；这里按 4000 字切，避免上下文过长
    chunks: List[str] = [text[i:i + 4000] for i in range(0, len(text), 4000)]
    if len(chunks) == 1:
        return await _summarize_one(chunks[0])

    partials = []
    for index, chunk in enumerate(chunks, start=1):
        partial = await llm.chat(
            [
                {"role": "system", "content": SUMMARY_SYSTEM},
                {"role": "user", "content": f"这是第 {index}/{len(chunks)} 段转写，请先提炼要点：\n{chunk}"},
            ],
            temperature=0.2,
            max_tokens=800,
        )
        partials.append(f"第 {index} 段：\n{partial}")

    return await llm.chat(
        [
            {"role": "system", "content": SUMMARY_SYSTEM},
            {"role": "user", "content": "以下是各段摘要，请合并成最终视频总结：\n\n" + "\n\n".join(partials)},
        ],
        temperature=0.2,
        max_tokens=1200,
    )


async def _summarize_one(text: str) -> str:
    return await llm.chat(
        [
            {"role": "system", "content": SUMMARY_SYSTEM},
            {"role": "user", "content": f"视频转写文本：\n{text}"},
        ],
        temperature=0.2,
        max_tokens=1200,
    )
