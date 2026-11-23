"""自然语言 -> SQL -> 只读执行 -> 自然语言汇总。"""
import json
import re
from typing import Any, Dict

from . import config, db, llm

SYSTEM = """你是学习平台的数据分析助手。请把用户问题转换为一条 MySQL SELECT 查询。

规则：
1. 只能查询给定的 v_agent_* 视图，禁止查询任何业务表、系统表。
2. 只能生成一条 SELECT，禁止 INSERT/UPDATE/DELETE/DDL/存储过程。
3. 不要编造字段；字段名必须来自给定视图。
4. 结果必须加 LIMIT，最多 200 行。
5. 只输出 SQL，放在 ```sql 代码块中，不要解释。
"""

SUMMARY_SYSTEM = """你是学习平台的数据分析助手。根据 SQL 查询结果，用简洁的中文回答用户问题。
要求：
1. 只依据查询结果，不要编造不存在的数据；
2. 结果为空时直接说“没有查询到符合条件的数据”；
3. 不要输出 SQL，除非用户要求；
4. 数字带上单位（如人、门、条、小时）。
"""


def extract_sql(text: str) -> str:
    match = re.search(r"```(?:sql)?\s*(.*?)```", text, re.S | re.I)
    raw = match.group(1) if match else text
    match = re.search(r"(?is)\bselect\b.*", raw)
    if not match:
        raise ValueError("模型没有生成 SELECT 语句")
    return match.group(0).strip().rstrip(";").strip()


async def generate_sql(question: str) -> str:
    schema = db.schema_prompt()
    prompt = f"数据库视图结构：\n{schema}\n\n用户问题：{question}\n\n请输出 SQL。"
    text = await llm.chat(
        [
            {"role": "system", "content": SYSTEM},
            {"role": "user", "content": prompt},
        ],
        temperature=0.0,
        max_tokens=800,
    )
    return extract_sql(text)


async def ask(question: str) -> Dict[str, Any]:
    question = (question or "").strip()
    if not question:
        raise ValueError("问题不能为空")

    sql = ""
    try:
        sql = await generate_sql(question)
        rows = db.run_select(sql)
    except Exception as exc:
        return {
            "answer": f"无法完成查询：{exc}",
            "sql": sql,
            "rows": [],
            "rowCount": 0,
            "error": True,
        }

    if not rows:
        return {
            "answer": "没有查询到符合条件的数据。",
            "sql": sql,
            "rows": [],
            "rowCount": 0,
            "error": False,
        }

    # 只把前 50 行给模型汇总，避免上下文过长；完整结果仍返回前端
    preview = rows[:50]
    prompt = (
        f"用户问题：{question}\n\n"
        f"执行 SQL：{sql}\n\n"
        f"查询结果（最多 50 行）：\n{json.dumps(preview, ensure_ascii=False, default=str)}\n\n"
        "请用中文回答用户问题。"
    )
    answer = await llm.chat(
        [
            {"role": "system", "content": SUMMARY_SYSTEM},
            {"role": "user", "content": prompt},
        ],
        temperature=0.2,
        max_tokens=600,
    )
    return {
        "answer": answer.strip(),
        "sql": sql,
        "rows": rows,
        "rowCount": len(rows),
        "error": False,
    }
