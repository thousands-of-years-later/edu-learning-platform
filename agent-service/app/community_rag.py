"""社区帖子 RAG：已发布帖子入库，回答并引用 postId。"""
import datetime
import json
import re
from pathlib import Path
from typing import Any, Dict, List, Optional

import numpy as np
from sqlalchemy import text

from . import config, db, llm

SYSTEM = """你是学习平台的社区问答助手。只能根据提供的社区帖子回答，不要使用帖子之外的知识。
要求：
1. 回答正文不要标注来源（客户端会用引用卡片单独展示来源），不要写 [来源: ...]、“来源：”之类；
2. 可以综合多个帖子，但不要编造帖子没有的内容；
3. 如果帖子内容不足以回答，明确说“社区帖子中没有找到相关内容”；
4. 用简洁的中文回答。
"""

# 兜底：模型仍可能按习惯输出来源标注，返回前统一剥掉，来源只由 refs 卡片展示
_SOURCE_TAG = re.compile(r"[\[【]\s*来源\s*[:：][^\]】]*[\]】]")


def _load() -> Dict[str, Any]:
    path: Path = config.COMMUNITY_INDEX_FILE
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
    with config.COMMUNITY_INDEX_FILE.open("w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False)


def _clean(content: str) -> str:
    content = re.sub(r"<[^>]+>", " ", content or "")
    content = re.sub(r"\s+", " ", content)
    return content.strip()


def _post_chunks() -> List[Dict[str, Any]]:
    with db.engine().connect() as conn:
        rows = conn.execute(
            text(
                """
                select post_id, post_title, category_name, author_nickname,
                       content, like_count, comment_count, create_time
                  from v_agent_post_content
                 where status_name = '已发布'
                 order by post_id desc
                """
            )
        ).mappings().all()

    chunks: List[Dict[str, Any]] = []
    for row in rows:
        content = _clean(row["content"] or "")
        if not content:
            content = "（帖子暂无正文）"
        text_content = (
            f"帖子标题：{row['post_title']}\n"
            f"分类：{row['category_name'] or '未分类'}\n"
            f"作者：{row['author_nickname'] or '匿名'}\n"
            f"内容：{content[:4000]}"
        )
        chunks.append(
            {
                "id": f"post-{row['post_id']}",
                "text": text_content,
                "metadata": {
                    "postId": int(row["post_id"]),
                    "postTitle": row["post_title"],
                    "category": row["category_name"] or "未分类",
                    "author": row["author_nickname"] or "匿名",
                    "createTime": str(row["create_time"] or ""),
                    # 引用卡片用：正文前 80 字（已去过 HTML 标签、压过空白）
                    "excerpt": content[:80],
                },
            }
        )
    return chunks


async def ingest() -> Dict[str, Any]:
    chunks = _post_chunks()
    if not chunks:
        _save([])
        return {"chunks": 0, "message": "没有已发布帖子"}

    vectors = await llm.embed([chunk["text"] for chunk in chunks])
    for chunk, vector in zip(chunks, vectors):
        chunk["vector"] = vector
    _save(chunks)
    return {"chunks": len(chunks), "message": "社区帖子索引已重建"}


def _cosine(a: List[float], b: List[float]) -> float:
    va, vb = np.asarray(a, dtype=np.float32), np.asarray(b, dtype=np.float32)
    denom = float(np.linalg.norm(va) * np.linalg.norm(vb))
    return float(np.dot(va, vb) / denom) if denom else 0.0


async def search(question: str, top_k: int = 5) -> List[Dict[str, Any]]:
    chunks = _load().get("chunks") or []
    if not chunks:
        return []
    query_vector = (await llm.embed([question]))[0]
    scored = [{**chunk, "score": _cosine(query_vector, chunk["vector"])} for chunk in chunks]
    scored.sort(key=lambda item: item["score"], reverse=True)
    return scored[:top_k]


def _excerpt_of(chunk: Dict[str, Any]) -> str:
    """引用摘要：优先用索引里的 excerpt，老索引没有就从正文 chunk 里截。"""
    meta = chunk.get("metadata") or {}
    excerpt = (meta.get("excerpt") or "").strip()
    if excerpt:
        return excerpt
    text = chunk.get("text") or ""
    body = text.split("内容：", 1)[-1] if "内容：" in text else text
    return " ".join(body.split())[:80]


def _refs_of(chunks: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    refs: List[Dict[str, Any]] = []
    seen = set()
    for chunk in chunks:
        meta = chunk["metadata"]
        post_id = meta.get("postId")
        if post_id in seen:
            continue
        seen.add(post_id)
        refs.append(
            {
                "postId": post_id,
                "postTitle": meta.get("postTitle", ""),
                "category": meta.get("category", ""),
                "excerpt": _excerpt_of(chunk),
            }
        )
    return refs


def _messages(question: str, chunks: List[Dict[str, Any]],
              history: Optional[List[Dict[str, str]]] = None) -> List[Dict[str, str]]:
    context = "\n\n".join(
        f"[帖子《{c['metadata'].get('postTitle', '')}》#{c['metadata'].get('postId')}]\n{c['text']}"
        for c in chunks
    )
    messages: List[Dict[str, str]] = [{"role": "system", "content": SYSTEM}]
    total = 0
    for item in (history or [])[-8:]:
        role = (item.get("role") or "").strip()
        content = (item.get("content") or "").strip()
        if role not in ("user", "assistant") or not content:
            continue
        content = content[:1200]
        if total + len(content) > 6000:
            break
        total += len(content)
        messages.append({"role": role, "content": content})
    messages.append({"role": "user", "content": f"社区帖子资料：\n{context}\n\n用户问题：{question}"})
    return messages


async def ask(question: str, history: Optional[List[Dict[str, str]]] = None) -> Dict[str, Any]:
    question = (question or "").strip()
    if not question:
        raise ValueError("问题不能为空")
    chunks = await search(question, top_k=5)
    if not chunks:
        return {
            "answer": "社区帖子中没有找到相关内容。请先确认帖子索引已重建。",
            "refs": [],
            "retrieved": 0,
        }
    answer = await llm.chat(_messages(question, chunks, history), temperature=0.2, max_tokens=1000)
    return {"answer": _strip_source_tags(answer), "refs": _refs_of(chunks), "retrieved": len(chunks)}


def _strip_source_tags(answer: str) -> str:
    """去掉正文里的 [来源: 帖子…] / 【来源：…】 标注，来源统一由 refs 卡片展示。"""
    text = _SOURCE_TAG.sub("", answer or "")
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()
