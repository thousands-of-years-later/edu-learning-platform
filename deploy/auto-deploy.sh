#!/usr/bin/env bash
# ============================================================
# 服务器定时自动部署（由 crontab 调用，不建议手动执行）
# 逻辑：拉取远端 deploy 分支 -> 有变化才部署 -> 失败保留现场并记录
# 日志：/var/log/edu-deploy.log
# 手动触发：bash deploy/auto-deploy.sh
# ============================================================
set -uo pipefail

REPO_DIR="${REPO_DIR:-/www/wwwroot/edu-smart-learning}"
BRANCH="${BRANCH:-deploy}"
LOG="${LOG:-/var/log/edu-deploy.log}"
LOCK="/tmp/edu-deploy.lock"
TS="$(date '+%F %T')"

mkdir -p "$(dirname "$LOG")"
exec 9>"$LOCK"
if ! flock -n 9; then
  echo "[$TS] [skip] 已有部署任务在运行" >>"$LOG"
  exit 0
fi

cd "$REPO_DIR" || { echo "[$TS] [错误] 目录不存在: $REPO_DIR" >>"$LOG"; exit 1; }

# 服务器上若有人手工改过被跟踪文件，先记录差异再以仓库为准
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "[$TS] [警告] 工作区有本地修改，已备份到 /tmp/edu-local-changes-$(date +%s).patch" >>"$LOG"
  git diff >"/tmp/edu-local-changes-$(date +%s).patch" 2>/dev/null || true
fi

OLD="$(git rev-parse HEAD 2>/dev/null || echo none)"

echo "[$TS] [拉取] origin/$BRANCH" >>"$LOG"
if ! git fetch --prune origin "$BRANCH" >>"$LOG" 2>&1; then
  echo "[$TS] [失败] git fetch 失败（网络或权限问题）" >>"$LOG"
  exit 1
fi

NEW="$(git rev-parse FETCH_HEAD)"
if [ "$OLD" = "$NEW" ]; then
  exit 0
fi

echo "[$TS] [变更] ${OLD:0:8} -> ${NEW:0:8}" >>"$LOG"
if ! git reset --hard FETCH_HEAD >>"$LOG" 2>&1; then
  echo "[$TS] [失败] 切换到最新代码失败" >>"$LOG"
  exit 1
fi

echo "[$TS] [部署] 执行 deploy/deploy.sh" >>"$LOG"
if bash deploy/deploy.sh >>"$LOG" 2>&1; then
  echo "[$TS] [完成] 部署成功 ${NEW:0:8}" >>"$LOG"
else
  echo "[$TS] [失败] 部署失败，已保留现场（镜像与容器未清理），请查看上方日志" >>"$LOG"
  exit 1
fi

# 成功后才清理，失败时保留现场便于排查
bash deploy/maintenance.sh >>"$LOG" 2>&1 || echo "[$TS] [警告] 清理脚本执行异常（不影响部署结果）" >>"$LOG"
