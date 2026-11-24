"""视频总结：Paraformer ASR + 分段摘要；无 URL/ASR 失败时回退到元数据摘要。"""
from typing import Any, Dict, List

from sqlalchemy import text

from . import asr, config, db, llm

SUMMARY_SYSTEM = """你是学习平台的视频总结助手。请根据视频转写文本输出中文总结。
结构：
1. 一句话总结
2. 分段大纲（按内容顺序）
3. 关键知识点（3~6 条）
4. 复习思考题（2~3 条）
只依据转写文本，不要编造；文本是英文时可以翻成中文再总结。
"""


async def summary(video_id: int) -> Dict[str, Any]:
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

    base = {
        "videoId": int(row["video_id"]),
        "courseId": int(row["course_id"]),
        "videoTitle": row["video_title"],
        "durationMinutes": float(row["duration_min"] or 0),
    }

    video_url = row["video_url"]
    if not video_url:
        return {**base, **_fallback(row, "视频缺少可访问地址")}

    try:
        transcript = await asr.transcribe(video_url)
        transcript_text = transcript["text"].strip()
        if len(transcript_text) < 20:
            return {**base, **_fallback(row, "ASR 转写内容过短")}
        if _is_english_test_audio(row["video_title"], transcript_text):
            fallback = _fallback(row, "ASR 转写内容是英文测试素材，与课程标题不一致")
            fallback.update({"asr": True, "transcriptChars": len(transcript_text)})
            return {**base, **fallback}
        summary_text = await _summarize(transcript_text)
        return {
            **base,
            "summary": summary_text,
            "transcriptChars": len(transcript_text),
            "asr": True,
            "fallback": False,
        }
    except Exception as exc:
        return {**base, **_fallback(row, f"ASR 失败：{exc}")}


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
