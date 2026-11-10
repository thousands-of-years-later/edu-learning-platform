# 自动化部署说明

## 架构

```
本地仓库 --git push--> 服务器裸仓库 /www/git/edu-learning-platform.git
                              |
                     cron 每 10 分钟（0:00-8:50）
                              |
                     deploy/auto-deploy.sh  (git fetch -> 有变化才部署)
                              |
                     deploy/deploy.sh       (构建 -> 打版本标签 -> 启动 -> 健康检查 -> 失败回滚)
                              |
                     deploy/maintenance.sh  (部署成功后清理日志/缓存/旧镜像)
```

服务器不直接访问 GitHub。实测服务器到 github.com 的 git 连接 3/3 超时，codeload 60 秒仅完成 2.7MB，
因此改用「本地推送 + 服务器本地裸仓库」方式，全程不依赖外网。

## 常用命令

| 场景 | 命令 |
|---|---|
| 推送并部署（本地） | `git push server deploy` |
| 立即检查并部署（服务器） | `bash /www/wwwroot/edu-smart-learning/deploy/auto-deploy.sh` |
| 强制重新部署 | 服务器：`cd /www/wwwroot/edu-smart-learning && bash deploy/deploy.sh` |
| 应用增量 SQL | `bash deploy/deploy.sh --sql 文件名.sql` |
| 手动清理 | `bash deploy/maintenance.sh` 或加 `--dry-run` 预览 |
| 查看部署日志 | `tail -f /var/log/edu-deploy.log` |
| 查看清理日志 | `tail -f /var/log/edu-maintenance.log` |

## 版本与回滚

- 每次构建自动打标签 `edu-learning-platform/backend:YYYYMMDD-HHMMSS`
- 保留最近 3 个版本，更早的在部署成功后由 `maintenance.sh` 删除
- 健康检查（`http://127.0.0.1:8080/api/v1/login` 返回 200/400/401）失败时，脚本自动回滚到上一个标签并重启
- 手工回滚：`docker tag edu-learning-platform/backend:<旧标签> edu-learning-platform/backend:latest`
  然后 `docker compose -p deploy -f deploy/docker-compose.yml up -d --no-build --force-recreate backend`

## 清理策略

| 对象 | 策略 |
|---|---|
| 应用日志 | 保留 30 天，每周日 03:00 清理 |
| 部署备份包 | 保留 30 天 |
| 容器日志 | 单文件 50MB、最多 3 个（compose 里 `logging` 配置） |
| 构建缓存 | 保留 72 小时 |
| 悬空镜像 | 删除 24 小时以上的 |
| backend 镜像 | 只保留最近 3 个版本 |

清理只在部署成功后执行，部署失败时保留现场便于排查。

## 代理与网络加速

服务器装了 mihomo（Clash Meta）常驻服务，只为本机的 git 和 docker 使用。

| 项 | 值 |
|---|---|
| 服务 | `systemctl status mihomo` / `restart mihomo` |
| 配置 | `/opt/mihomo/config.yaml`（权限 600，含节点凭据，不进仓库） |
| 代理端口 | `127.0.0.1:7890`（mixed，仅本机监听，防火墙未放行） |
| 控制面 | `127.0.0.1:9090`，需 `secret`（在配置文件里） |
| 作用范围 | git 仅对 github.com；docker daemon 全局 |

实测对比：

| 场景 | 直连 | 走代理 |
|---|---|---|
| 打开 github.com | 12 秒超时 | 0.2–0.6 秒 |
| `git ls-remote` | 40 秒超时 | 1 秒 |

常用操作：

```bash
bash /opt/mihomo/update-sub.sh      # 更新订阅节点（失败自动保留旧配置，不会断服务）
journalctl -u mihomo -n 30 --no-pager   # 看内核日志
```

docker 的代理在 `/etc/docker/daemon.json`，改动前的备份在 `/root/daemon.json.bak.*`；
已开 `live-restore`，但首次重启 daemon 仍会重启容器，所以改 docker 配置要挑时间。

注意事项：
- 订阅到期 2026-12-07，到期前更新订阅链接或续费
- 没有设置全局 `http_proxy`，容器内和 Maven 构建不会被带偏
- 代理挂掉不影响部署：部署走服务器本地裸仓库，不走外网

## 注意事项

- `deploy/.env` 不进仓库，服务器上保留原文件，`git reset --hard` 不会删它
- compose 固定使用 `-p deploy`，保证数据卷名（`deploy_mysql-data` 等）不变
- `deploy/docker-compose.local.yml` 仅本地开发使用，服务器不加载
- 首次切换前的完整备份：`/root/edu-smart-learning-pre-git-20261110-1457.tgz`
