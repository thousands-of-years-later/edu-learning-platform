"""视频总结（一期 mock）：只查元数据，不接 ASR。"""
from typing import Any, Dict

from sqlalchemy import text

from . import db


async def summary(video_id: int) -> Dict[str, Any]:
    with db.engine().connect() as conn:
        row = conn.execute(
            text(
                """
                select video_id, course_id, course_name, chapter_title,
                       video_title, duration_min
                  from v_agent_course_video
                 where video_id = :video_id
                """
            ),
            {"video_id": video_id},
        ).mappings().first()

    if not row:
        raise ValueError("视频不存在或已删除")

    title = row["video_title"]
    duration = row["duration_min"] or 0
    summary_text = (
        f"【Mock 视频总结】《{title}》\n"
        f"所属课程：{row['course_name']}\n"
        f"章节：{row['chapter_title'] or '未分章'}\n"
        f"时长：{duration} 分钟\n\n"
        "ASR 转写接入后，这里会输出：一句话总结、分段时间轴、关键知识点和复习思考题。"
    )
    return {
        "videoId": int(row["video_id"]),
        "courseId": int(row["course_id"]),
        "videoTitle": title,
        "summary": summary_text,
        "mock": True,
    }
