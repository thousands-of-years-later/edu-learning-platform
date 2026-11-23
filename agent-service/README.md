# edu-learning agent-service

独立 Python 智能体服务，FastAPI + DeepSeek + DashScope embedding，给若依 Java 后端做内网调用。

## 已实现

| 能力 | 状态 |
|---|---|
| `POST /internal/sql/ask` | 自然语言 -> SQL -> 只读视图执行 -> 中文回答 |
| `POST /internal/chat/ask` | 课程 RAG（课程简介 + 章节/小节标题），带来源 |
| `POST /internal/video/summary` | mock，返回视频元数据说明 |
| `POST /internal/rag/ingest` | 重建课程向量索引 |
| `GET /internal/health` | 健康检查 |

所有 `/internal/**` 请求必须带 `x-internal-token`，值等于 `.env` 里的 `AGENT_INTERNAL_TOKEN`。

## 启动

```powershell
cd H:\edu-learning-platform\agent-service
python -m pip install -r requirements.txt
python -m app.ingest
uvicorn app.main:app --host 127.0.0.1 --port 8100
```

## 测试

```powershell
$h = @{ "x-internal-token" = "<AGENT_INTERNAL_TOKEN>"; "Content-Type" = "application/json" }

curl.exe -s http://127.0.0.1:8100/internal/health

curl.exe -s -H "x-internal-token: <AGENT_INTERNAL_TOKEN>" -H "Content-Type: application/json" `
  -d '{"question":"平台一共有多少门课程？"}' `
  http://127.0.0.1:8100/internal/sql/ask

curl.exe -s -H "x-internal-token: <AGENT_INTERNAL_TOKEN>" -H "Content-Type: application/json" `
  -d '{"question":"Java 课程主要讲什么？"}' `
  http://127.0.0.1:8100/internal/chat/ask

curl.exe -s -H "x-internal-token: <AGENT_INTERNAL_TOKEN>" -H "Content-Type: application/json" `
  -d '{"videoId":201}' `
  http://127.0.0.1:8100/internal/video/summary
```

## SQL 安全

- 只允许 `v_agent_*` 视图，`sqlglot` 解析 AST 后校验单条 SELECT；
- 强制补 `LIMIT 200`，会话级 `READ ONLY`，执行超时 3 秒；
- 结果再做一层手机号/邮箱脱敏；
- 生产必须使用 `edu_ro`，不要用 `edu_dev_03`。

## RAG 说明

当前知识库来自 `v_agent_course` 的课程简介和 `v_agent_course_video` 的小节标题。
课程正文/讲义/字幕入库后，回答质量会明显提升；视频 ASR 接入前先使用 mock。

## 待办

- [ ] 换成 `edu_ro` 只读账号；
- [ ] 视频 ASR 接入；
- [ ] PPT 生成；
- [ ] 课程讲义/字幕入库；
- [ ] Java `ApiV1AgentController` 对接与 WS 流式推送。
