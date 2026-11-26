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

# 引用相关度阈值（DashScope text-embedding-v3 实测分布）：
#   强相关 0.69–0.81（如 MySQL→索引失效、Redis→缓存穿透）
#   次相关 0.44–0.54（同分类但主题不同，如「计算机网络」→微服务演进）
#   无关   0.39–0.48
# 低于 MIN_SCORE 不视为来源；比最高分低超过 RELATIVE_MARGIN 的也丢掉，
# 避免「一个主题命中后把同批 top-k 全部当来源」。
MIN_SCORE = 0.52
RELATIVE_MARGIN = 0.10
MAX_REFS = 3

# 模型会把「循环结构」改写成「循环结构相关的讨论、经验与踩坑」，
# 这些泛化词会把不相关帖子也拉过分数线，所以先用关键词覆盖做一道硬门槛：
# 去掉通用词后，query 的实词（中文 2-gram / 英文整词）必须出现在帖子标题、分类或摘要里。
_GENERIC_WORDS = (
    "相关", "讨论", "经验", "踩坑", "分享", "内容", "问题", "怎么", "如何", "哪些",
    "什么", "介绍", "总结", "一下", "主要", "这门", "课程", "帖子", "社区", "学习",
    "知识", "方面", "大家", "遇到", "请", "帮忙", "查", "找", "看看", "说说",
    "一般", "具体", "的", "了", "我", "有", "是", "在", "和", "与", "或", "及",
    "吗", "呢", "啊", "讲", "学",
)
_TOKEN_SPLIT = re.compile(r"[\s,，。、；;:：!?！？/\\()（）\[\]【】\"'“”‘’]+")
_ENGLISH_TOKEN = re.compile(r"^[a-z0-9]+$")


def _keywords(text: str) -> set:
    text = (text or "").lower()
    for word in _GENERIC_WORDS:
        text = text.replace(word, " ")
    grams = set()
    for token in _TOKEN_SPLIT.split(text):
        token = token.strip()
        if len(token) < 2:
            continue
        if _ENGLISH_TOKEN.match(token):
            grams.add(token)
            continue
        for i in range(len(token) - 1):
            grams.add(token[i:i + 2])
    return grams


def _keyword_hit(chunk: Dict[str, Any], keywords: set) -> bool:
    meta = chunk.get("metadata") or {}
    haystack = " ".join(
        str(meta.get(key) or "") for key in ("postTitle", "category", "excerpt")
    ).lower()
    return any(word in haystack for word in keywords)


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


async def search(question: str, top_k: int = 5,
                 min_score: float = MIN_SCORE) -> List[Dict[str, Any]]:
    """向量召回 + 相关度过滤。

    纯 top-k 在没有命中时也会硬凑 5 条（问「计算机网络」会带回 uni-app、Kafka），
    所以这里加两道闸：绝对分下限 + 与最高分的相对差。
    """
    chunks = _load().get("chunks") or []
    if not chunks:
        return []
    query_vector = (await llm.embed([question]))[0]
    scored = [{**chunk, "score": _cosine(query_vector, chunk["vector"])} for chunk in chunks]
    scored.sort(key=lambda item: item["score"], reverse=True)
    best = scored[0]["score"]
    cutoff = max(min_score, best - RELATIVE_MARGIN)
    keywords = _keywords(question)
    return [
        chunk for chunk in scored[:top_k]
        if chunk["score"] >= cutoff and (not keywords or _keyword_hit(chunk, keywords))
    ]


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
            "answer": f"社区帖子中没有找到与「{question}」相关的内容。",
            "refs": [],
            "retrieved": 0,
        }
    answer = await llm.chat(_messages(question, chunks, history), temperature=0.2, max_tokens=1000)
    return {
        "answer": _strip_source_tags(answer),
        "refs": _refs_of(chunks[:MAX_REFS]),
        "retrieved": len(chunks),
    }


def _strip_source_tags(answer: str) -> str:
    """去掉正文里的 [来源: 帖子…] / 【来源：…】 标注，来源统一由 refs 卡片展示。"""
    text = _SOURCE_TAG.sub("", answer or "")
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()
