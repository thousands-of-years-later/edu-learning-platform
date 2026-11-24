# edu-learning agent-service

独立 Python 智能体服务，FastAPI + DeepSeek + DashScope embedding，给若依 Java 后端做内网调用。

## 已实现

| 能力 | 状态 |
|---|---|
| `POST /internal/sql/ask` | 自然语言 -> SQL -> 只读视图执行 -> 中文回答 |
| `POST /internal/chat/ask` | 课程 RAG（课程简介 + 章节正文 + 小节标题），带来源 |
| `POST /internal/chat/stream` | 课程 RAG 流式输出（NDJSON） |
| `POST /internal/community/ask` | 社区帖子 RAG，回答引用 `postId` |
| `POST /internal/community/ingest` | 重建社区帖子索引 |
| `POST /internal/video/summary` | Paraformer ASR + 分段总结；无地址/ASR 失败时回退元数据 |
| `POST /internal/ppt/generate` | 大纲 JSON -> python-pptx 套模板 -> OSS 签名 URL |
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

课程知识库来自 `v_agent_course` 课程简介、`v_agent_course_chapter` 章节正文和
`v_agent_course_video` 小节标题；社区知识库来自 `v_agent_post_content` 的已发布帖子正文。
课程正文/讲义/字幕越完整，回答质量越高。

重建索引：

```powershell
python -m app.ingest
# 或调用 POST /internal/community/ingest
```

视频总结使用 Paraformer `paraformer-v2`：视频 URL 由 OSS 签名后提交 ASR，
转写文本分段总结；现有 6 个视频是英文测试素材，标题是中文时会回退到元数据摘要。

## PPT 说明

模板：`templates/business_template.pptx`（已移除模板最后一页版权页）。
生成时只保留封面、目录、章节页、亮点页、要点页、结尾页共 6 页，
上传路径 `agent/ppt/{userId}/{taskId}.pptx`，返回 1 小时签名 URL。

## 待办

- [ ] 换成 `edu_ro` 只读账号；
- [ ] 用真实课程视频替换英文测试素材；
- [ ] 课程讲义/字幕批量入库；
- [ ] Java 会话/任务/流式已对接，后续补多轮上下文与审计限流。
