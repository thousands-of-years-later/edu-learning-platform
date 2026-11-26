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
DEEPSEEK_MODEL = _env("DEEPSEEK_MODEL", "deepseek-flash")

DASHSCOPE_API_KEY = _env("DASHSCOPE_API_KEY")
DASHSCOPE_BASE_URL = _env(
    "DASHSCOPE_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1"
).rstrip("/")
DASHSCOPE_EMBEDDING_MODEL = _env("DASHSCOPE_EMBEDDING_MODEL", "text-embedding-v3")

DB_READONLY_URL = _env("DB_READONLY_URL")
AGENT_INTERNAL_TOKEN = _env("AGENT_INTERNAL_TOKEN", "change-me")
AGENT_PORT = int(_env("AGENT_PORT", "8100"))
# 内部回调 Java 后端（save_memory 需要写库，agent 只有只读库权限）
BACKEND_INTERNAL_URL = _env("BACKEND_INTERNAL_URL", "http://backend:80").rstrip("/")

# 阿里云 OSS：PPT 上传、视频签名 URL
OSS_ACCESS_KEY_ID = _env("OSS_ACCESS_KEY_ID")
OSS_ACCESS_KEY_SECRET = _env("OSS_ACCESS_KEY_SECRET")
OSS_BUCKET = _env("OSS_BUCKET", "zhinengguanggaopingtai")
OSS_ENDPOINT = _env("OSS_ENDPOINT", "oss-cn-shanghai.aliyuncs.com")
OSS_PPT_PREFIX = _env("OSS_PPT_PREFIX", "agent/ppt")

# DashScope Paraformer 录音文件识别
ASR_BASE_URL = _env("ASR_BASE_URL", "https://dashscope.aliyuncs.com/api/v1").rstrip("/")
ASR_MODEL = _env("ASR_MODEL", "paraformer-v2")
ASR_TIMEOUT_SECONDS = int(_env("ASR_TIMEOUT_SECONDS", "180"))

# PPT
DATA_DIR = BASE_DIR / "data"
PPT_TEMPLATE = BASE_DIR / "templates" / "business_template.pptx"
PPT_OUTPUT_DIR = DATA_DIR / "ppt"

RAG_INDEX_FILE = DATA_DIR / "rag_courses.json"
COMMUNITY_INDEX_FILE = DATA_DIR / "rag_posts.json"

# SQL 智能体只允许查询这些视图；Python 侧再按 v_agent_ 前缀做一次白名单
ALLOWED_VIEWS = {
    "v_agent_course",
    "v_agent_course_video",
    "v_agent_course_chapter",
    "v_agent_post",
    "v_agent_post_content",
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
