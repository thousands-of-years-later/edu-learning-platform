# edu-learning-platform 学习平台

基于若依（RuoYi）4.8.3 二次开发的学习平台，包含课程学习、社区论坛、积分体系、积分商城和平台协议等模块，同时提供面向 App / 小程序 / H5 的 Token 鉴权 API。

## 技术栈

| 分类 | 选型 |
|---|---|
| 语言 | Java 17 |
| 框架 | Spring Boot 4.1.0、RuoYi 4.8.3 |
| 安全 | Apache Shiro 3.0.0（后台会话 + C 端 API Token） |
| 持久层 | MyBatis、MySQL 8.0 |
| 缓存/中间件 | Redis（Docker 编排中包含） |
| 构建 | Maven（多模块） |
| 部署 | Docker Compose |

## 模块说明

| 模块 | 说明 |
|---|---|
| `ruoyi-admin` | 启动模块，包含后台管理 Controller 与全局配置 |
| `ruoyi-framework` | 框架核心：Shiro、拦截器、全局异常、数据源等 |
| `ruoyi-common` | 公共工具、统一响应、WebSocket 事件中心 |
| `ruoyi-system` | 若依系统管理（用户、角色、菜单、字典等） |
| `ruoyi-quartz` | 定时任务 |
| `ruoyi-generator` | 代码生成 |
| `learning-course` | 课程、章节、课程视频 |
| `learning-video` | 短视频 |
| `learning-community` | 社区论坛：帖子、话题、分类、关注、违禁词 |
| `learning-interation` | 互动：评论、点赞、收藏、通知 |
| `learning-user` | C 端用户、学习记录、积分、签到、通知 |
| `learning-api` | C 端 API（`/api/v1/**`）与 Token 鉴权 |
| `shopping` | 积分商城：商品、分类、订单、地址、兑换码 |

## 主要功能

- **课程学习**：课程列表、章节、视频播放、学习进度、学习时长统计
- **社区论坛**：发帖 / 列表 / 详情 / 评论 / 点赞收藏、话题、违禁词拦截、帖子审核
- **实时推送**：WebSocket 推送新帖、新评论、点赞收藏变化
- **用户体系**：C 端登录注册、Token 鉴权、个人资料、关注
- **积分体系**：签到、积分账户、积分明细、积分奖励
- **积分商城**：商品兑换、订单、收货地址
- **平台协议**：用户协议、隐私政策等
- **后台管理**：若依后台，含内容审核、分类、违禁词、商城运营

## 环境要求

- JDK 17
- Maven 3.8+
- MySQL 8.0
- Redis（仅部署编排使用，本地不强制）

## 本地启动

1. 准备数据库

   创建数据库 `learning_platform`，按需执行 `sql/` 下的建表脚本。使用 Docker 本地开发时，`deploy/init/local-schema.sql` 会在首次启动时自动初始化。

2. 配置数据库连接

   默认读取 `ruoyi-admin/src/main/resources/application-druid.yml`，可用环境变量覆盖：

   | 变量 | 说明 | 默认 |
   |---|---|---|
   | `LEARNING_DB_HOST` | 数据库地址 | `43.142.9.214` |
   | `LEARNING_DB_USER` | 数据库账号 | `edu_dev_03` |
   | `LEARNING_DB_PASSWORD` | 数据库密码 | 见配置文件 |
   | `SERVER_PORT` | 服务端口 | `80` |

3. 构建并启动

   ```bash
   mvn -DskipTests package
   java -jar ruoyi-admin/target/ruoyi-admin.jar
   ```

   或直接运行 `com.ruoyi.RuoYiApplication`。

4. 访问

   后台：`http://localhost:8080`（本地端口被占用时可设 `SERVER_PORT=8080`）

## 鉴权约定

- **后台**：Shiro 会话登录，账号密码登录后使用 Cookie。
- **C 端 API**：`/api/v1/**`，请求头携带 Token：

  ```
  Authorization: Bearer <token>
  # 或
  token: <token>
  ```

  Token 存于 `learning_api_token` 表，通过 `/api/v1/login` 获取。

## WebSocket

论坛实时推送端点：

```
ws://<host>/ws/forum?token=<api_token>
```

客户端消息：

```json
{"action":"subscribe","topics":["forum:all","post:123"]}
{"action":"unsubscribe","topics":["post:123"]}
{"action":"ping"}
```

服务端事件：`post.created`、`comment.created`、`like.changed`、`favorite.changed`、`pong`、`error`。

主题：`forum:all`（全站新帖）、`post:{id}`（单帖评论/点赞）、`user:{id}`（仅本人可订阅）。

## 部署

部署相关脚本在 `deploy/`：

| 文件 | 用途 |
|---|---|
| `deploy/docker-compose.yml` | MySQL / Redis / 后端编排 |
| `deploy/start-local.ps1` | 本地 Docker 开发环境启动 |
| `deploy/deploy.sh` | 服务器构建部署（含健康检查与回滚） |
| `deploy/auto-deploy.sh` | 定时拉取并部署 |
| `deploy/README-deploy.md` | 完整部署说明 |

## 分支约定

| 分支 | 用途 |
|---|---|
| `category` | 主开发分支，日常功能合并到这里 |
| `deploy` | 发布基线，服务器部署以它为准 |

提交代码请推到 `category`，发布时再同步到 `deploy`。

## 目录结构

```
edu-learning-platform/
├── ruoyi-admin/           # 启动模块
├── ruoyi-framework/       # 框架核心
├── ruoyi-common/          # 公共工具 + WebSocket 事件中心
├── ruoyi-system/          # 系统管理
├── learning-course/       # 课程
├── learning-video/        # 短视频
├── learning-community/    # 社区论坛
├── learning-interation/   # 评论/点赞/收藏/通知
├── learning-user/         # 用户/学习记录/积分
├── learning-api/          # C 端 API 与 Token 鉴权
├── shopping/              # 积分商城
├── sql/                   # 各模块建表与初始化 SQL
├── deploy/                # Docker 与部署脚本
└── pom.xml
```
