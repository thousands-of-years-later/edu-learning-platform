"""只读数据库访问 + SQL 白名单/解析校验。"""
import datetime
import decimal
import re
from typing import Any, Dict, List

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine
from sqlglot import exp, parse

from . import config

_engine: Engine | None = None


def engine() -> Engine:
    global _engine
    if _engine is None:
        config.require("DB_READONLY_URL", config.DB_READONLY_URL)
        _engine = create_engine(
            config.DB_READONLY_URL,
            pool_pre_ping=True,
            pool_size=3,
            max_overflow=2,
            connect_args={"connect_timeout": 5},
        )
    return _engine


def _jsonable(value: Any) -> Any:
    if isinstance(value, decimal.Decimal):
        return float(value)
    if isinstance(value, (datetime.datetime, datetime.date, datetime.time)):
        return value.isoformat(sep=" ") if isinstance(value, datetime.datetime) else value.isoformat()
    if isinstance(value, bytes):
        return value.decode("utf-8", errors="replace")
    return value


def _mask(key: str, value: Any) -> Any:
    if value is None:
        return None
    lower = key.lower()
    if any(token in lower for token in ("phone", "mobile", "email", "id_card", "password")):
        text_value = str(value)
        if "@" in text_value:
            name, _, domain = text_value.partition("@")
            return (name[:2] + "***@" + domain) if name else "***@" + domain
        return text_value[:3] + "****" + text_value[-2:] if len(text_value) > 5 else "****"
    return value


def get_schema() -> Dict[str, List[Dict[str, str]]]:
    """读取 information_schema，只保留 v_agent_* 视图的列。"""
    sql = text(
        """
        select table_name as table_name,
               column_name as column_name,
               data_type as data_type
          from information_schema.columns
         where table_schema = database()
           and table_name like 'v_agent_%'
         order by table_name, ordinal_position
        """
    )
    schema: Dict[str, List[Dict[str, str]]] = {}
    with engine().connect() as conn:
        for row in conn.execute(sql).mappings():
            name = row["table_name"]
            if name not in config.ALLOWED_VIEWS:
                continue
            schema.setdefault(name, []).append(
                {"column": row["column_name"], "type": row["data_type"]}
            )
    return schema


def schema_prompt() -> str:
    schema = get_schema()
    if not schema:
        raise RuntimeError("没有找到 v_agent_* 视图，请先执行视图 SQL")
    lines = ["可用视图（只能查询这些表）："]
    for table, columns in schema.items():
        cols = ", ".join(f"{c['column']}({c['type']})" for c in columns)
        lines.append(f"- {table}: {cols}")
    lines.append("\n禁止查询 sys_user、learning_api_token、sys_config、sys_dept 等系统表。")
    return "\n".join(lines)


def validate_sql(sql: str) -> str:
    """只允许单条 SELECT，表名必须在 v_agent_* 白名单内，并强制 LIMIT。"""
    statements = parse(sql, read="mysql")
    if len(statements) != 1:
        raise ValueError("只允许一条 SQL")
    expression = statements[0]
    if not isinstance(expression, exp.Select):
        raise ValueError("只允许 SELECT 查询")

    # 明确拒绝 SELECT ... INTO OUTFILE / 锁表
    into_cls = getattr(exp, "Into", None)
    if into_cls is not None and expression.find(into_cls):
        raise ValueError("不允许 SELECT ... INTO")
    lock_cls = getattr(exp, "Lock", None)
    if lock_cls is not None and expression.find(lock_cls):
        raise ValueError("不允许加锁查询")

    for table in expression.find_all(exp.Table):
        name = (table.name or "").lower()
        if name not in config.ALLOWED_VIEWS:
            raise ValueError(f"表 {name} 不在允许的视图白名单内")

    limit = expression.args.get("limit")
    if limit is None:
        expression.set("limit", exp.Limit(expression=exp.Literal.number(config.MAX_SQL_ROWS)))
    else:
        limit_value = limit.expression
        if not isinstance(limit_value, exp.Literal) or not limit_value.is_int:
            raise ValueError("LIMIT 必须是整数")
        if int(limit_value.this) > config.MAX_SQL_ROWS:
            expression.set("limit", exp.Limit(expression=exp.Literal.number(config.MAX_SQL_ROWS)))

    # 双保险：不允许出现写操作关键字（AST 已限制 SELECT，这里防解析遗漏）
    if re.search(r"\b(insert|update|delete|drop|alter|truncate|create|grant|revoke)\b", sql, re.I):
        raise ValueError("SQL 包含禁止的写操作关键字")

    return expression.sql(dialect="mysql")


def run_select(sql: str) -> List[Dict[str, Any]]:
    safe_sql = validate_sql(sql)
    config.require("DB_READONLY_URL", config.DB_READONLY_URL)
    with engine().connect() as conn:
        # 会话级只读；即使校验有漏洞，也不能写库
        try:
            conn.execute(text("SET SESSION TRANSACTION READ ONLY"))
            conn.execute(text("SET SESSION MAX_EXECUTION_TIME=3000"))
        except Exception:
            pass
        result = conn.execute(text(safe_sql))
        rows = result.mappings().fetchmany(config.MAX_SQL_ROWS)
        conn.rollback()
    return [
        {key: _mask(key, _jsonable(value)) for key, value in dict(row).items()}
        for row in rows
    ]
