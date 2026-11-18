#!/usr/bin/env bash
# ============================================================
# 定期清理（日志 / 构建缓存 / 旧镜像）
# 用法：bash deploy/maintenance.sh           实际执行
#       bash deploy/maintenance.sh --dry-run 只打印不删除
# 策略：日志保留 30 天；构建缓存保留 72 小时；backend 镜像保留最近 3 个版本
# ============================================================
set -uo pipefail

DRY=""
[ "${1:-}" = "--dry-run" ] && DRY="echo [dry-run]"

ROOT="${ROOT:-/www/wwwroot/edu-smart-learning}"
KEEP_DAYS=30
KEEP_IMAGES=3
LOG="/var/log/edu-maintenance.log"
TS="$(date '+%F %T')"
IMAGE_REPO="edu-learning-platform/backend"

log() { echo "[$TS] $*" | tee -a "$LOG"; }
run() { if [ -n "$DRY" ]; then echo "[dry-run] $*"; else eval "$@"; fi; }

log "===== 开始清理 ====="

# ---------- 1. 应用日志：保留 30 天 ----------
for d in "$ROOT/logs" "$ROOT/deploy/logs"; do
  [ -d "$d" ] || continue
  while IFS= read -r f; do
    log "删除日志: $f"
    run "rm -f \"$f\""
  done < <(find "$d" -type f \( -name "*.log" -o -name "*.log.*" -o -name "*.gz" \) -mtime +$KEEP_DAYS 2>/dev/null)
done

# ---------- 2. 部署备份包：保留 30 天 ----------
while IFS= read -r f; do
  log "删除备份: $f"
  run "rm -f \"$f\""
done < <(find /root /tmp -maxdepth 1 -type f \( -name "edu-*.tgz" -o -name "edu-*.tar.gz" \) -mtime +$KEEP_DAYS 2>/dev/null)

# ---------- 3. 容器日志超限截断（不删除文件，避免句柄失效） ----------
while IFS= read -r f; do
  size=$(du -m "$f" 2>/dev/null | cut -f1)
  [ "${size:-0}" -gt 100 ] || continue
  log "截断容器日志(${size}MB): $f"
  run "truncate -s 0 \"$f\""
done < <(find /var/lib/docker/containers -name "*-json.log" -size +100M 2>/dev/null)

# ---------- 4. 构建缓存：保留 72 小时 ----------
if [ -z "$DRY" ]; then
  docker builder prune -f --filter "until=72h" | tail -n 2 | tee -a "$LOG"
else
  echo "[dry-run] docker builder prune -f --filter until=72h"
fi

# ---------- 5. 悬空镜像：24 小时以上 ----------
if [ -z "$DRY" ]; then
  docker image prune -f --filter "until=24h" | tail -n 2 | tee -a "$LOG"
else
  echo "[dry-run] docker image prune -f --filter until=24h"
fi

# ---------- 6. backend 镜像：只保留最近 3 个时间戳版本 ----------
if [ -n "$DRY" ]; then
  echo "[dry-run] 保留最近 $KEEP_IMAGES 个 $IMAGE_REPO 版本"
else
  tags=$(docker images "$IMAGE_REPO" --format '{{.Tag}}' | grep -E '^[0-9]{8}-[0-9]{6}$' | sort -r)
  count=$(echo "$tags" | grep -c . || true)
  if [ "${count:-0}" -gt "$KEEP_IMAGES" ]; then
    echo "$tags" | tail -n +$((KEEP_IMAGES + 1)) | while IFS= read -r t; do
      log "删除旧镜像: $IMAGE_REPO:$t"
      docker rmi "$IMAGE_REPO:$t" >>"$LOG" 2>&1 || true
    done
  else
    log "镜像版本数 ${count:-0}，无需清理"
  fi
fi

# ---------- 7. 汇总 ----------
log "磁盘占用：$(df -h / | tail -1 | awk '{print $3"/"$2" ("$5")"}')"
docker system df | tee -a "$LOG"
log "===== 清理结束 ====="
