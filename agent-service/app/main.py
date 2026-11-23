"""Agent 服务入口：只对内网提供 /internal/** 接口。"""
import asyncio
import logging
from typing import Optional

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import text

from . import config, db, rag, sql_agent, video_agent

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger("agent-service")

app = FastAPI(title="edu-learning-agent", version="0.1.0")


def verify_token(x_internal_token: Optional[str] = Header(default=None)) -> None:
    if not config.AGENT_INTERNAL_TOKEN or x_internal_token != config.AGENT_INTERNAL_TOKEN:
        raise HTTPException(status_code=401, detail="invalid internal token")


class SqlAskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=1000)


class ChatAskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=1000)
    courseId: Optional[int] = None


class VideoSummaryRequest(BaseModel):
    videoId: int


@app.get("/internal/health")
async def health():
    db_status = "unknown"
    try:
        with db.engine().connect() as conn:
            conn.execute(text("select 1"))
        db_status = "up"
    except Exception as exc:
        db_status = f"down: {exc}"
    return {"ok": True, "db": db_status, "service": "agent-service"}


@app.post("/internal/sql/ask", dependencies=[Depends(verify_token)])
async def sql_ask(body: SqlAskRequest):
    try:
        return await sql_agent.ask(body.question)
    except Exception as exc:
        logger.exception("sql ask failed")
        raise HTTPException(status_code=500, detail=str(exc))


@app.post("/internal/chat/ask", dependencies=[Depends(verify_token)])
async def chat_ask(body: ChatAskRequest):
    try:
        return await rag.ask(body.courseId, body.question)
    except Exception as exc:
        logger.exception("chat ask failed")
        raise HTTPException(status_code=500, detail=str(exc))


@app.post("/internal/video/summary", dependencies=[Depends(verify_token)])
async def video_summary(body: VideoSummaryRequest):
    try:
        return await video_agent.summary(body.videoId)
    except Exception as exc:
        logger.exception("video summary failed")
        raise HTTPException(status_code=500, detail=str(exc))


@app.post("/internal/rag/ingest", dependencies=[Depends(verify_token)])
async def rag_ingest():
    try:
        return await rag.ingest()
    except Exception as exc:
        logger.exception("rag ingest failed")
        raise HTTPException(status_code=500, detail=str(exc))


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="127.0.0.1", port=config.AGENT_PORT, reload=False)
