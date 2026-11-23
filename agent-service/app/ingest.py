"""重建课程 RAG 索引：python -m app.ingest"""
import asyncio

from .rag import ingest


if __name__ == "__main__":
    result = asyncio.run(ingest())
    print(result)
