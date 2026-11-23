"""Agent 服务配置：只从环境变量/.env 读取，不把密钥写进代码。"""
import os
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parents[1]
load_dotenv(BASE_DIR / ".env")


def _env(name: str, default: str = "") -> str:
    value = os.getenv(name, default)
    return value.strip() if isinstance(value, str) else value


DEEPSEEK_API_KEY = _env("DEEPSEEK_API_KEY")
DEEPSEEK_BASE_URL = _env("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1").rstrip("/")
DEEPSEEK_MODEL = _env("DEEPSEEK_MODEL", "deepseek-chat")

DASHSCOPE_API_KEY = _env("DASHSCOPE_API_KEY")
DASHSCOPE_BASE_URL = _env(
    "DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"
).rstrip("/")
DASHSCOPE_EMBEDDING_MODEL = _env("DASHSCOPE_EMBEDDING_MODEL", "text-embedding-v3")

DB_READONLY_URL = _env("DB_READONLY_URL")
AGENT_INTERNAL_TOKEN = _env("AGENT_INTERNAL_TOKEN", "change-me")
AGENT_PORT = int(_env("AGENT_PORT", "8100"))

DATA_DIR = BASE_DIR / "data"
RAG_INDEX_FILE = DATA_DIR / "rag_courses.json"

# SQL 智能体只允许查询这些视图；Python 侧再按 v_agent_ 前缀做一次白名单
ALLOWED_VIEWS = {
    "v_agent_course",
    "v_agent_course_video",
    "v_agent_post",
    "v_agent_comment_stat",
    "v_agent_study_stat",
    "v_agent_category",
    "v_agent_author",
    "v_agent_user_stat",
    "v_agent_course_progress",
}

MAX_SQL_ROWS = 200
LLM_TIMEOUT_SECONDS = 30
LLM_RETRIES = 2


def require(name: str, value: str) -> str:
    if not value:
        raise RuntimeError(f"缺少环境变量 {name}，请检查 agent-service/.env")
    return value
