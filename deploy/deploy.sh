#!/usr/bin/env bash
# ============================================================
# 智能广告平台（edu-smart-learning）一键部署脚本
# 用法：在项目根目录执行    bash deploy/deploy.sh
#   可选：bash deploy/deploy.sh --sql 文件1.sql 文件2.sql   # 先应用增量 SQL
#
# 说明：
#   1. 固定 compose 项目名 deploy，保证数据卷 deploy_mysql-data 等不变
#   2. 每次构建成功后打时间戳镜像（edu-learning-platform/backend:20261110-153000）
#   3. 健康检查失败自动回滚到上一个时间戳镜像
#   4. 清理不在本脚本做，由 maintenance.sh 在部署成功后执行
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PROJECT="deploy"
COMPOSE_FILE="deploy/docker-compose.yml"
ENV_FILE="deploy/.env"
IMAGE_REPO="edu-learning-platform/backend"
BACKEND_LOG_LINES=20
HEALTH_RETRY=20
HEALTH_SLEEP=3

DEPLOY_START=$(date +%s)
# 通知结果记日志，失败可查；未配置 notify.conf 时 notify.sh 会静默返回 0
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

SHORT_SHA() {
  git rev-parse --short HEAD 2>/dev/null || echo unknown
}

# ---------- 0. 前置校验 ----------
[ -f "$ENV_FILE" ]    || { echo "[错误] 缺少 $ENV_FILE（请确认 OSS/MySQL 配置存在）"; notify "部署失败" "缺少 $ENV_FILE，部署未开始\n提交 $(SHORT_SHA)"; exit 1; }
[ -f "$COMPOSE_FILE" ] || { echo "[错误] 缺少 $COMPOSE_FILE"; notify "部署失败" "缺少 $COMPOSE_FILE，部署未开始\n提交 $(SHORT_SHA)"; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "[错误] docker 不可用"; notify "部署失败" "docker 不可用，部署未开始"; exit 1; }

health_check() {
  local code
  for _ in $(seq 1 "$HEALTH_RETRY"); do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8080/api/v1/login 2>/dev/null || true)"
    case "$code" in
      200|400|401) return 0 ;;
    esac
    sleep "$HEALTH_SLEEP"
  done
  return 1
}

# ---------- 1. 可选：应用数据库变更（只执行指定文件，禁止全量） ----------
if [ "${1:-}" = "--sql" ]; then
  shift
  [ $# -gt 0 ] || {
    echo "[错误] --sql 必须跟要执行的 SQL 文件名（相对 sql/ 目录），例如："
    echo "  bash deploy/deploy.sh --sql learning_study.sql"
    echo "  注意：不会执行 sql/ 下的全部脚本（含回滚 / 清理脚本，避免误操作）"
    exit 1
  }
  echo "[1/4] 应用数据库变更脚本"
  MYSQL_ROOT_PASSWORD="$(grep -E '^MYSQL_ROOT_PASSWORD=' "$ENV_FILE" | tail -n1 | cut -d= -f2-)"
  for name in "$@"; do
    f="sql/$name"
    [ -f "$f" ] || { echo "  [跳过] $name 不存在于 sql/ 目录"; continue; }
    echo "  -> $f"
    docker exec -i edu-mysql sh -c \
      'mysql --default-character-set=utf8mb4 -uroot -p"'"$MYSQL_ROOT_PASSWORD"'" learning_platform' < "$f" \
      && echo "      [OK]" || { echo "      [失败] 请检查脚本"; notify "部署失败" "数据库脚本 $name 执行失败，部署已中止\n提交 $(SHORT_SHA)"; exit 1; }
  done
else
  echo "[1/4] 跳过数据库变更（如需应用请加 --sql 并指定文件名）"
fi

# ---------- 2. 构建并打版本标签 ----------
echo "[2/4] 构建后端镜像"
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" build backend \
  || { echo "[错误] 镜像构建失败"; notify "部署失败" "镜像构建失败，未影响线上容器\n提交 $(SHORT_SHA)"; exit 1; }

TS_TAG="$(date '+%Y%m%d-%H%M%S')"
docker tag "$IMAGE_REPO:latest" "$IMAGE_REPO:$TS_TAG"
echo "  已打版本标签: $IMAGE_REPO:$TS_TAG"

echo "[3/4] 启动服务（使用刚构建的镜像，不再重复构建）"
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up -d --no-build \
  || { echo "[错误] 容器启动失败"; notify "部署失败" "容器启动失败\n版本 ${TS_TAG}\n提交 $(SHORT_SHA)\n详见 /var/log/edu-deploy.log"; exit 1; }

# ---------- 3. 健康检查 + 失败回滚 ----------
echo "[4/4] 等待后端就绪（最多 $((HEALTH_RETRY * HEALTH_SLEEP)) 秒）"
if health_check; then
  echo "  [OK] 后端 HTTP 响应正常，当前版本 $TS_TAG"
  notify "部署成功" "版本 ${TS_TAG} 已上线\n提交 $(SHORT_SHA)\n耗时 $(( $(date +%s) - DEPLOY_START )) 秒\n${NOTIFY_EXTRA:-}"
else
  echo "  [失败] 新版本未就绪，最近日志："
  docker logs --tail "$BACKEND_LOG_LINES" edu-backend 2>&1 || true

  PREV_TAG="$(docker images "$IMAGE_REPO" --format '{{.Tag}}' \
    | grep -E '^[0-9]{8}-[0-9]{6}$' | grep -v "^$TS_TAG$" | sort -r | head -n 1)"
  if [ -n "$PREV_TAG" ]; then
    echo "  [回滚] 切换到上一个版本 $PREV_TAG"
    docker tag "$IMAGE_REPO:$PREV_TAG" "$IMAGE_REPO:latest"
    docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up -d --no-build --force-recreate backend
    if health_check; then
      echo "  [回滚成功] 已恢复到 $PREV_TAG，请排查 $TS_TAG 的构建日志"
      notify "部署失败" "版本 ${TS_TAG} 启动失败，已回滚到 ${PREV_TAG}\n提交 $(SHORT_SHA)\n详见 /var/log/edu-deploy.log"
      exit 1
    fi
    echo "  [回滚失败] 旧版本 $PREV_TAG 也无法启动，需人工介入"
    notify "部署失败" "版本 ${TS_TAG} 启动失败，回滚到 ${PREV_TAG} 也失败，需人工介入\n提交 $(SHORT_SHA)"
    exit 1
  fi
  echo "  [无可用回滚版本] 首版部署失败，请检查构建与配置"
  notify "部署失败" "首版部署失败，无可回滚版本\n版本 ${TS_TAG}\n提交 $(SHORT_SHA)"
  exit 1
fi

# ---------- 4. 结果汇总 ----------
echo ""
echo "部署完成，当前容器状态："
docker ps --filter "name=edu-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
echo "可用回滚版本："
docker images "$IMAGE_REPO" --format '{{.Tag}}\t{{.CreatedAt}}' | grep -E '^[0-9]{8}-[0-9]{6}' | head -n 3
