#!/usr/bin/env bash
# 部署结果通知：未配置时静默退出，不影响部署流程
# 配置文件：/etc/edu-deploy/notify.conf
#   WEBHOOK_URL=https://open.feishu.cn/open-apis/bot/v2/hook/xxxx
#   NOTIFY_TYPE=feishu        # feishu | wecom | dingtalk，默认 feishu
#   NOTIFY_ON=all             # all | fail，默认 all
set -uo pipefail

CONF="/etc/edu-deploy/notify.conf"
[ -f "$CONF" ] || exit 0
# shellcheck disable=SC1090
. "$CONF"

[ -n "${WEBHOOK_URL:-}" ] || exit 0

TITLE="${1:-部署通知}"
BODY="${2:-}"
TYPE="${NOTIFY_TYPE:-feishu}"
ON="${NOTIFY_ON:-all}"
IS_FAIL=0
case "$TITLE" in *失败*) IS_FAIL=1 ;; esac

if [ "$ON" = "fail" ] && [ "$IS_FAIL" = "0" ]; then
  exit 0
fi

TITLE="$TITLE" BODY="$BODY" TYPE="$TYPE" WEBHOOK_URL="$WEBHOOK_URL" python3 - <<'PY'
import json, os, sys, urllib.request

title = os.environ.get("TITLE", "")
body = os.environ.get("BODY", "")
kind = os.environ.get("TYPE", "feishu")
url = os.environ["WEBHOOK_URL"]

text = f"{title}\n{body}" if body else title

if kind == "feishu":
    payload = {"msg_type": "text", "content": {"text": text}}
else:
    payload = {"msgtype": "text", "text": {"content": text}}

req = urllib.request.Request(
    url,
    data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
    headers={"Content-Type": "application/json"},
    method="POST",
)
try:
    with urllib.request.urlopen(req, timeout=10) as r:
        print(r.read().decode("utf-8", "ignore")[:200])
except Exception as e:
    print(f"通知发送失败: {type(e).__name__}", file=sys.stderr)
PY
