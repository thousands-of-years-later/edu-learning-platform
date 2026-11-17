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

# 通知结果单独记日志（原先用 >/dev/null 丢弃，失败时无从排查）
NOTIFY_LOG="${NOTIFY_LOG:-/var/log/edu-deploy-notify.log}"
mkdir -p "$(dirname "$NOTIFY_LOG")" 2>/dev/null || true
touch "$NOTIFY_LOG" 2>/dev/null || NOTIFY_LOG="/tmp/edu-deploy-notify.log"

notify() {
  local title="$1" body="$2"
  [ -f deploy/notify.sh ] || return 0
  printf '[%s] %s -> ' "$(date '+%F %T')" "$title" >>"$NOTIFY_LOG" 2>/dev/null || true
  if bash deploy/notify.sh "$title" "$body" >>"$NOTIFY_LOG" 2>&1; then
    echo "[ok]" >>"$NOTIFY_LOG" 2>/dev/null || true
  else
    echo "[失败] 退出码 $?" >>"$NOTIFY_LOG" 2>/dev/null || true
  fi
}

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

# 拉取策略：优先 GitHub 直连（走本机 mihomo 代理），失败回退服务器本地裸仓库
# 回退可避免代理节点失效时定时部署被卡住；两个源都失败才中止
FETCH_SRC="origin"
echo "[$TS] [拉取] origin/$BRANCH（GitHub 直连）" >>"$LOG"
if ! git fetch --prune origin "$BRANCH" >>"$LOG" 2>&1; then
  FALLBACK_REMOTE="${FALLBACK_REMOTE:-mirror}"
  if git remote get-url "$FALLBACK_REMOTE" >/dev/null 2>&1; then
    echo "[$TS] [回退] GitHub 拉取失败，改用本地裸仓库 $FALLBACK_REMOTE/$BRANCH" >>"$LOG"
    if ! git fetch --prune "$FALLBACK_REMOTE" "$BRANCH" >>"$LOG" 2>&1; then
      echo "[$TS] [失败] GitHub 与本地裸仓库均拉取失败，未做任何变更" >>"$LOG"
      notify "部署失败" "GitHub 与本地裸仓库均拉取失败，代码未变更"
      exit 1
    fi
    FETCH_SRC="$FALLBACK_REMOTE"
  else
    echo "[$TS] [失败] GitHub 拉取失败，且未配置回退远端 $FALLBACK_REMOTE" >>"$LOG"
    notify "部署失败" "GitHub 拉取失败，代码未变更"
    exit 1
  fi
fi

NEW="$(git rev-parse FETCH_HEAD)"
if [ "$OLD" = "$NEW" ]; then
  exit 0
fi

echo "[$TS] [变更] ${OLD:0:8} -> ${NEW:0:8}（来源 $FETCH_SRC）" >>"$LOG"

# 只改了文档就不重建镜像，只更新代码（避免为了一个 md 跑 5 分钟构建）
CHANGED="$(git diff --name-only "$OLD" "$NEW" 2>/dev/null || true)"
FILE_COUNT="$(printf '%s\n' "$CHANGED" | sed '/^$/d' | wc -l)"
NEED_DEPLOY=1
if [ -n "$CHANGED" ] && ! printf '%s\n' "$CHANGED" | grep -qvE '^(docs/)|\.md$|^deploy/README'; then
  NEED_DEPLOY=0
fi

if ! git reset --hard FETCH_HEAD >>"$LOG" 2>&1; then
  echo "[$TS] [失败] 切换到最新代码失败" >>"$LOG"
  notify "部署失败" "提交 ${NEW:0:8} 切换代码失败，已保留现场"
  exit 1
fi

if [ "$NEED_DEPLOY" = "0" ]; then
  echo "[$TS] [跳过] 本次 ${FILE_COUNT} 个文件均为文档，代码已更新但不需要重建镜像" >>"$LOG"
  exit 0
fi

echo "[$TS] [部署] 执行 deploy/deploy.sh（变更文件 ${FILE_COUNT} 个）" >>"$LOG"
DEPLOY_START=$(date +%s)
# 成功/失败的通知统一由 deploy.sh 发（带上版本号与耗时），这里只通过环境变量补充变更文件数
if NOTIFY_FROM_AUTO=1 NOTIFY_EXTRA="变更文件 ${FILE_COUNT} 个" bash deploy/deploy.sh >>"$LOG" 2>&1; then
  COST=$(( $(date +%s) - DEPLOY_START ))
  echo "[$TS] [完成] 部署成功 ${NEW:0:8}，耗时 ${COST} 秒" >>"$LOG"
else
  echo "[$TS] [失败] 部署失败，已保留现场（镜像与容器未清理），请查看上方日志" >>"$LOG"
  exit 1
fi

# 成功后才清理，失败时保留现场便于排查
bash deploy/maintenance.sh >>"$LOG" 2>&1 || echo "[$TS] [警告] 清理脚本执行异常（不影响部署结果）" >>"$LOG"
