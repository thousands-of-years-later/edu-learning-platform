"""课程 RAG：课程简介 + 章节/小节标题入库，DashScope 向量检索。"""
import datetime
import json
from pathlib import Path
from typing import Any, Dict, List, Optional

import numpy as np
from sqlalchemy import text

from . import config, db, llm

SYSTEM = """你是学习平台的课程答疑助手。只能根据提供的课程资料回答，不要使用资料之外的知识。
要求：
1. 结合对话历史理解“它/这门课/上面说的”等指代；
2. 回答末尾用 [来源: 课程名-章节名] 标注出处；
3. 资料里没有的内容，明确说“课程资料中没有相关内容”，不要编造；
4. 用简洁的中文回答，适合学生阅读。
"""


def _load() -> Dict[str, Any]:
    path: Path = config.RAG_INDEX_FILE
    if not path.exists():
        return {"chunks": [], "created_at": None}
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def _save(chunks: List[Dict[str, Any]]) -> None:
    config.DATA_DIR.mkdir(parents=True, exist_ok=True)
    payload = {
        "created_at": datetime.datetime.now().isoformat(timespec="seconds"),
        "chunks": chunks,
    }
    with config.RAG_INDEX_FILE.open("w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False)


def _course_chunks() -> List[Dict[str, Any]]:
    chunks: List[Dict[str, Any]] = []
    with db.engine().connect() as conn:
        courses = conn.execute(
            text(
                """
                select course_id, course_name, category_name, intro,
                       rating_avg, view_count, like_count
                  from v_agent_course
                 order by course_id
                """
            )
        ).mappings().all()

        for row in courses:
            intro = (row["intro"] or "").strip() or "暂无简介"
            content = (
                f"课程：{row['course_name']}\n"
                f"分类：{row['category_name'] or '未分类'}\n"
                f"简介：{intro}\n"
                f"评分：{row['rating_avg'] or 0}\n"
                f"学习人数：{row['view_count'] or 0}"
            )
            chunks.append(
                {
                    "id": f"course-{row['course_id']}",
                    "text": content,
                    "metadata": {
                        "course_id": int(row["course_id"]),
                        "course": row["course_name"],
                        "chapter": "课程简介",
                        "type": "course",
                    },
                }
            )

        videos = conn.execute(
            text(
                """
                select video_id, course_id, course_name, chapter_title,
                       video_title, duration_min
                  from v_agent_course_video
                 order by course_id, order_num, video_id
                """
            )
        ).mappings().all()

        for row in videos:
            content = (
                f"课程：{row['course_name']}\n"
                f"章节：{row['chapter_title'] or '未分章'}\n"
                f"小节：{row['video_title']}\n"
                f"时长：{row['duration_min'] or 0} 分钟"
            )
            chunks.append(
                {
                    "id": f"video-{row['video_id']}",
                    "text": content,
                    "metadata": {
                        "course_id": int(row["course_id"]),
                        "course": row["course_name"],
                        "chapter": row["chapter_title"] or "未分章",
                        "video": row["video_title"],
                        "type": "video",
                    },
                }
            )

        chapters = conn.execute(
            text(
                """
                select chapter_id, course_id, course_name, chapter_title, chapter_content
                  from v_agent_course_chapter
                 order by course_id, order_num, chapter_id
                """
            )
        ).mappings().all()

        for row in chapters:
            content = (row["chapter_content"] or "").strip()
            if not content:
                continue
            chunks.append(
                {
                    "id": f"chapter-{row['chapter_id']}",
                    "text": (
                        f"课程：{row['course_name']}\n"
                        f"章节：{row['chapter_title']}\n"
                        f"内容：{content}"
                    ),
                    "metadata": {
                        "course_id": int(row["course_id"]),
                        "course": row["course_name"],
                        "chapter": row["chapter_title"],
                        "type": "chapter",
                    },
                }
            )
    return chunks


async def ingest() -> Dict[str, Any]:
    """重建课程向量索引（课程简介 + 小节标题）。"""
    chunks = _course_chunks()
    if not chunks:
        _save([])
        return {"chunks": 0, "message": "没有可入库的课程"}

    vectors = await llm.embed([chunk["text"] for chunk in chunks])
    for chunk, vector in zip(chunks, vectors):
        chunk["vector"] = vector
    _save(chunks)
    return {"chunks": len(chunks), "message": "课程索引已重建"}


def _cosine(a: List[float], b: List[float]) -> float:
    va, vb = np.asarray(a, dtype=np.float32), np.asarray(b, dtype=np.float32)
    denom = float(np.linalg.norm(va) * np.linalg.norm(vb))
    return float(np.dot(va, vb) / denom) if denom else 0.0


async def search(question: str, course_id: Optional[int] = None, top_k: int = 5) -> List[Dict[str, Any]]:
    index = _load()
    chunks = index.get("chunks") or []
    if not chunks:
        return []
    query_vector = (await llm.embed([question]))[0]

    scored: List[Dict[str, Any]] = []
    for chunk in chunks:
        meta = chunk.get("metadata") or {}
        if course_id is not None and int(meta.get("course_id", -1)) != int(course_id):
            continue
        scored.append({**chunk, "score": _cosine(query_vector, chunk["vector"])})

    scored.sort(key=lambda item: item["score"], reverse=True)
    return scored[:top_k]


async def ask(course_id: Optional[int], question: str, history: Optional[List[Dict[str, str]]] = None) -> Dict[str, Any]:
    question = (question or "").strip()
    if not question:
        raise ValueError("问题不能为空")

    chunks = await search(question, course_id=course_id, top_k=5)
    if not chunks:
        return {
            "answer": "课程资料中没有相关内容。请先确认课程知识库已入库。",
            "refs": [],
            "retrieved": 0,
        }

    messages = _build_messages(question, chunks, history)
    answer = await llm.chat(messages, temperature=0.2, max_tokens=1000)
    return {"answer": answer.strip(), "refs": _refs_of(chunks), "retrieved": len(chunks)}


def _build_messages(question: str, chunks: List[Dict[str, Any]],
                    history: Optional[List[Dict[str, str]]] = None) -> List[Dict[str, str]]:
    context = "\n\n".join(
        f"[来源: {c['metadata'].get('course', '')}-{c['metadata'].get('chapter', '')}]\n{c['text']}"
        for c in chunks
    )
    messages: List[Dict[str, str]] = [{"role": "system", "content": SYSTEM}]

    # 只带最近 8 条，单条截断，避免历史把上下文撑爆
    total_chars = 0
    for item in (history or [])[-8:]:
        role = (item.get("role") or "").strip()
        content = (item.get("content") or "").strip()
        if role not in ("user", "assistant") or not content:
            continue
        content = content[:1200]
        total_chars += len(content)
        if total_chars > 6000:
            break
        messages.append({"role": role, "content": content})

    messages.append({
        "role": "user",
        "content": f"课程资料：\n{context}\n\n用户问题：{question}",
    })
    return messages


def _refs_of(chunks: List[Dict[str, Any]]) -> List[Dict[str, str]]:
    refs: List[Dict[str, str]] = []
    seen = set()
    for chunk in chunks:
        meta = chunk["metadata"]
        key = (meta.get("course"), meta.get("chapter"))
        if key not in seen:
            seen.add(key)
            refs.append({"course": meta.get("course", ""), "chapter": meta.get("chapter", "")})
    return refs


async def stream_answer(course_id: Optional[int], question: str,
                        history: Optional[List[Dict[str, str]]] = None):
    """逐段产出 {"delta": ...}，最后产出 {"done": True, "refs": [...]}。"""
    question = (question or "").strip()
    if not question:
        yield {"error": "问题不能为空"}
        return

    chunks = await search(question, course_id=course_id, top_k=5)
    if not chunks:
        text = "课程资料中没有相关内容。请先确认课程知识库已入库。"
        yield {"delta": text}
        yield {"done": True, "refs": [], "retrieved": 0}
        return

    async for delta in llm.chat_stream(_build_messages(question, chunks, history), temperature=0.2, max_tokens=1000):
        yield {"delta": delta}
    yield {"done": True, "refs": _refs_of(chunks), "retrieved": len(chunks)}

