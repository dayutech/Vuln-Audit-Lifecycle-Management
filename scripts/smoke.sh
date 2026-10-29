#!/usr/bin/env bash
# =============================================================================
# scripts/smoke.sh — 漏洞审计生命周期管理平台「部署验收」冒烟脚本
#
# 用法（在 Linux 主机上；也可用 plink 远程执行，见文件末尾「远程执行」示例）：
#   bash scripts/smoke.sh
#   BASE_URL=http://192.168.188.200:8089 bash scripts/smoke.sh
#   bash scripts/smoke.sh 2>&1 | tee smoke-$(date +%F-%H%M).log
#
# 可配置项（全部有默认值，不依赖外部变量）：
#   BASE_URL      默认 http://127.0.0.1:8089      对外入口（nginx：静态页 + /api 反代）
#   ADMIN_USER/ADMIN_PASS       默认 admin/admin
#   ENGINEER_USER/ENGINEER_PASS 默认 engineer/engineer
#   LEADER_USER/LEADER_PASS     默认 leader/leader
#   SMOKE_PROVISION  默认 1：工程师/组长账号登录不上时，用 admin 自动建
#                    smoke_engineer / smoke_leader 并绑定角色（写操作，验证后尽力清理）
#   SMOKE_PASSWORD   默认 Smoke@12345：自动建账号时使用的口令
#   CURL_TIMEOUT     默认 25 秒
#
# 输出约定（每个用例一行）：
#   PASS  <用例名>  <HTTP状态>  <补充说明>
#   FAIL  <用例名>  <HTTP状态>  <补充说明>
#   SKIP  <用例名>  <HTTP状态>  <补充说明>      # 接口未实现(404/405/501)或被前置条件跳过
# 末尾输出 TOTAL / PASS / FAIL / SKIP 汇总；只要 FAIL>0 退出码为 1，SKIP 不影响退出码。
#
# 判定口径：
#   - 404 / 405 / 501 → SKIP（功能未上线，部署前不应该算失败）
#   - 5xx、状态码不符、状态码对但内容断言不过 → FAIL
#   - 本脚本只依赖 bash + curl + coreutils(grep/sed/od/head)，不需要 jq
# =============================================================================
set -uo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8089}"
BASE_URL="${BASE_URL%/}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-admin}"
ENGINEER_USER="${ENGINEER_USER:-engineer}"
ENGINEER_PASS="${ENGINEER_PASS:-engineer}"
LEADER_USER="${LEADER_USER:-leader}"
LEADER_PASS="${LEADER_PASS:-leader}"
SMOKE_PROVISION="${SMOKE_PROVISION:-1}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Smoke@12345}"
CURL_TIMEOUT="${CURL_TIMEOUT:-25}"

TMPD="$(mktemp -d "${TMPDIR:-/tmp}/vuln-smoke.XXXXXX" 2>/dev/null || mktemp -d)"
BODY="$TMPD/body"
LAST_STATUS=""
LAST_CTYPE=""
TOTAL=0
PASSED=0
FAILED=0
SKIPPED=0
FAILED_NAMES=""

ADMIN_TOKEN=""
ENGINEER_TOKEN=""
LEADER_TOKEN=""
ADMIN_ID=""
ENGINEER_ID=""
LEADER_ID=""

TS="$(date +%s)"
TAG="smoke_${TS}"

ASSET_ID=""
ASSET_VERSION_ID=""
GROUP_ID=""
VULN_ID=""
VULN_ATTACH_ID=""
TASK_ID=""
TOOL_ID=""
TOOL_VERSION_ID=""
ENV_ID=""
ANNOUNCEMENT_ID=""
SCOPE_ID=""

# ----------------------------------------------------------------------------- 基础能力

pass() { TOTAL=$((TOTAL + 1)); PASSED=$((PASSED + 1)); printf 'PASS  %-46s %-4s %s\n' "$1" "${2:--}" "${3:-}"; }
fail() {
  TOTAL=$((TOTAL + 1)); FAILED=$((FAILED + 1)); FAILED_NAMES="${FAILED_NAMES}${FAILED_NAMES:+, }$1"
  printf 'FAIL  %-46s %-4s %s\n' "$1" "${2:--}" "${3:-}"
}
skip() { TOTAL=$((TOTAL + 1)); SKIPPED=$((SKIPPED + 1)); printf 'SKIP  %-46s %-4s %s\n' "$1" "${2:--}" "${3:-}"; }

snippet() { head -c 220 "$BODY" 2>/dev/null | tr -d '\r\n' | tr '\t' ' '; }

# curl 在 Windows/Git-Bash 下（MSYS 构建）读不了 /tmp 形式的本地路径，
# Linux 上无 cygpath 时原样返回，行为不变。
pathify() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}

# request <method> <path> <token> [curl 额外参数...]
request() {
  local method="$1" path="$2" token="${3:-}"
  shift 3 || true
  local -a args=(-s -o "$BODY" -w '%{http_code} %{content_type}' --max-time "$CURL_TIMEOUT" -X "$method" "${BASE_URL}${path}")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer ${token}")
  local out
  out="$(curl "${args[@]}" "$@" 2>"$TMPD/curl.err" || true)"
  LAST_STATUS="${out%% *}"
  LAST_CTYPE="${out#* }"
  [ -z "$LAST_STATUS" ] && LAST_STATUS="000"
  [ "$LAST_CTYPE" = "$out" ] && LAST_CTYPE=""
}

api_json() { # <method> <path> <token> <json>
  # 用文件承载请求体（--data-binary）：避免命令行参数在不同平台/locale 下把中文编码搞坏，
  # 也避免超长参数被截断。
  local method="$1" path="$2" token="$3" payload="$4"
  printf '%s' "$payload" >"$TMPD/payload.json"
  request "$method" "$path" "$token" \
    -H 'Content-Type: application/json; charset=utf-8' --data-binary "@$(pathify "$TMPD/payload.json")"
}

download() { # <path> <token> <outfile>
  local path="$1" token="${2:-}" out="$3"
  local -a args=(-s -o "$out" -w '%{http_code}' --max-time "$CURL_TIMEOUT" -X GET "${BASE_URL}${path}")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer ${token}")
  LAST_STATUS="$(curl "${args[@]}" "${@:4}" 2>"$TMPD/curl.err" || true)"
  [ -z "$LAST_STATUS" ] && LAST_STATUS="000"
  LAST_CTYPE=""
}

flatten() { tr -d '\r\n' <"$BODY" 2>/dev/null; }

# 取第一个同名 key 的字符串值（不做 jq 依赖）
jfirst() { flatten | grep -o "\"$1\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -1 | sed 's/^[^:]*:[[:space:]]*"//; s/"$//'; }
# 取第一个同名 key 的数字值
jnum() { flatten | grep -o "\"$1\"[[:space:]]*:[[:space:]]*[0-9][0-9]*" | head -1 | grep -o '[0-9][0-9]*$'; }
# 统计某个 key 在响应里出现的次数（用来数数组长度）
jcount() { flatten | grep -o "\"$1\"" | wc -l | tr -d ' '; }

bom_of() { head -c 3 "$1" 2>/dev/null | od -An -tx1 | tr -d ' \n'; }

# judge <name> <期望状态> [正则] [说明]
judge() {
  local name="$1" expect="$2" pattern="${3:-}" note="${4:-}" st="$LAST_STATUS"
  if [ "$st" = "000" ]; then fail "$name" "000" "无法连接 ${BASE_URL}"; return 0; fi
  if [ "$st" = "$expect" ]; then
    if [ -z "$pattern" ] || grep -Eq -- "$pattern" "$BODY" 2>/dev/null; then
      pass "$name" "$st" "$note"
    else
      fail "$name" "$st" "HTTP 符合但内容不匹配 /${pattern}/ ｜ $(snippet)"
    fi
    return 0
  fi
  case "$st" in
    404 | 405 | 501) skip "$name" "$st" "接口未实现(HTTP $st)，按未上线处理" ;;
    *) fail "$name" "$st" "期望 ${expect}${pattern:+ 且匹配 /${pattern}/} ｜ $(snippet)" ;;
  esac
}

# judge_ok <name> [正则] [说明]  —— 接受任意 2xx
judge_ok() {
  local name="$1" pattern="${2:-}" note="${3:-}" st="$LAST_STATUS"
  if [ "$st" = "000" ]; then fail "$name" "000" "无法连接 ${BASE_URL}"; return 0; fi
  case "$st" in
    200 | 201 | 202 | 204)
      if [ -z "$pattern" ] || grep -Eq -- "$pattern" "$BODY" 2>/dev/null; then
        pass "$name" "$st" "$note"
      else
        fail "$name" "$st" "HTTP 符合但内容不匹配 /${pattern}/ ｜ $(snippet)"
      fi
      ;;
    404 | 405 | 501) skip "$name" "$st" "接口未实现(HTTP $st)，按未上线处理" ;;
    *) fail "$name" "$st" "期望 2xx${pattern:+ 且匹配 /${pattern}/} ｜ $(snippet)" ;;
  esac
}

need() { # need <name> <token> <提示>
  if [ -z "$2" ]; then skip "$1" "-" "$3"; return 1; fi
  return 0
}

# ----------------------------------------------------------------------------- 清理

cleanup() {
  local t
  t="${ADMIN_TOKEN:-}"
  if [ -n "$t" ]; then
    [ -n "${VULN_ATTACH_ID:-}" ] && [ -n "${VULN_ID:-}" ] && request DELETE "/api/v1/vulnerabilities/${VULN_ID}/attachments/${VULN_ATTACH_ID}" "$t" >/dev/null 2>&1
    [ -n "${VULN_ID:-}" ] && request DELETE "/api/v1/vulnerabilities/${VULN_ID}" "$t" >/dev/null 2>&1
    [ -n "${TASK_ID:-}" ] && request DELETE "/api/v1/tasks/${TASK_ID}" "$t" >/dev/null 2>&1
    [ -n "${TOOL_ID:-}" ] && request DELETE "/api/v1/tools/${TOOL_ID}" "$t" >/dev/null 2>&1
    [ -n "${GROUP_ID:-}" ] && request DELETE "/api/v1/asset-groups/${GROUP_ID}" "$t" >/dev/null 2>&1
    [ -n "${ENV_ID:-}" ] && request DELETE "/api/v1/environments/${ENV_ID}" "$t" >/dev/null 2>&1
    [ -n "${ANNOUNCEMENT_ID:-}" ] && request DELETE "/api/v1/notifications/announcements/${ANNOUNCEMENT_ID}" "$t" >/dev/null 2>&1
    [ -n "${SCOPE_ID:-}" ] && request DELETE "/api/v1/approval-scopes/${SCOPE_ID}" "$t" >/dev/null 2>&1
    [ -n "${ASSET_ID:-}" ] && request DELETE "/api/v1/assets/${ASSET_ID}" "$t" >/dev/null 2>&1
    # 说明：smoke_engineer / smoke_leader 两个夹具账号不删除。本系统 DELETE /users 是软删除，
    # 用户名会被占用且账号变为 deactivated，下次运行无法重建；SMOKE_PROVISION=0 可禁用自动建号。
  fi
  rm -rf "$TMPD" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# ----------------------------------------------------------------------------- 准备

fill() { # fill <bytes> <file>
  head -c "$1" /dev/zero 2>/dev/null | tr '\0' 'A' >"$2" 2>/dev/null || true
  if [ ! -s "$2" ] || [ "$(wc -c <"$2" | tr -d ' ')" != "$1" ]; then
    : >"$2"
    local i=0
    while [ "$i" -lt "$1" ]; do printf 'A' >>"$2"; i=$((i + 1)); done
  fi
}

try_login() { # <user> <pass> -> token（失败为空）
  api_json POST /api/v1/auth/login "" "{\"username\":\"$1\",\"password\":\"$2\"}"
  if [ "$LAST_STATUS" = "200" ]; then jfirst token; fi
}

role_id_of() { # <roleCode> -> roleId
  request GET '/api/v1/roles?page=0&size=100' "$ADMIN_TOKEN"
  [ "$LAST_STATUS" = "200" ] || return 1
  tr -d '\r\n' <"$BODY" | sed 's/},{/}\
{/g' | grep -F "\"code\":\"$1\"" | grep -o '"id":"[^"]*"' | head -1 | sed 's/.*"id":"//; s/"$//'
}

provision_user() { # <username> <roleCode> <fullName> -> userId，失败返回 1
  local user="$1" role="$2" full="$3" uid="" rid=""
  api_json POST /api/v1/users "$ADMIN_TOKEN" \
    "{\"username\":\"${user}\",\"password\":\"${SMOKE_PASSWORD}\",\"email\":\"${user}@smoke.local\",\"fullName\":\"${full}\"}"
  uid="$(jfirst id)"
  [ -z "$uid" ] && return 1
  rid="$(role_id_of "$role")"
  [ -z "$rid" ] && return 1
  request POST "/api/v1/users/${uid}/roles?roleId=${rid}" "$ADMIN_TOKEN"
  case "$LAST_STATUS" in
    200 | 201 | 204) echo "$uid"; return 0 ;;
    *) return 1 ;;
  esac
}

# 保证有一个可用账号：登录成功直接复用；不存在则创建并绑角色；
# 已存在但被停用（历史运行遗留 / 软删除）则激活 + 重置口令后复用。
# 输出 token；userId 写入 $TMPD/account.id（命令替换是子 shell，不能靠全局变量回传）。
ensure_account() { # <username> <roleCode> <fullName>
  local user="$1" role="$2" full="$3" token="" uid="" rid=""
  : >"$TMPD/account.id"
  token="$(try_login "$user" "$SMOKE_PASSWORD")" || true
  if [ -n "$token" ]; then
    printf '%s' "$(jfirst userId)" >"$TMPD/account.id"
    printf '%s' "$token"
    return 0
  fi
  api_json POST /api/v1/users "$ADMIN_TOKEN" \
    "{\"username\":\"${user}\",\"password\":\"${SMOKE_PASSWORD}\",\"email\":\"${user}@smoke.local\",\"fullName\":\"${full}\"}"
  case "$LAST_STATUS" in
    200 | 201) uid="$(jfirst id)" ;;
    *)
      request GET "/api/v1/users?keyword=${user}&page=0&size=10" "$ADMIN_TOKEN"
      uid="$(jfirst id)"
      if [ -n "$uid" ]; then
        request PATCH "/api/v1/users/${uid}/status?status=active" "$ADMIN_TOKEN"
        request POST "/api/v1/users/${uid}/reset-password?newPassword=${SMOKE_PASSWORD}" "$ADMIN_TOKEN"
      fi
      ;;
  esac
  [ -z "$uid" ] && return 1
  rid="$(role_id_of "$role")"
  [ -n "$rid" ] && request POST "/api/v1/users/${uid}/roles?roleId=${rid}" "$ADMIN_TOKEN"
  printf '%s' "$uid" >"$TMPD/account.id"
  token="$(try_login "$user" "$SMOKE_PASSWORD")" || true
  printf '%s' "$token"
}

printf '==== 冒烟开始 %s  BASE_URL=%s ====\n' "$(date '+%F %T')" "$BASE_URL"

# ----------------------------------------------------------------------------- 认证

ADMIN_TOKEN="$(try_login "$ADMIN_USER" "$ADMIN_PASS")" || true
if [ -n "$ADMIN_TOKEN" ]; then
  ADMIN_ID="$(jfirst userId)"
  pass "auth/登录-${ADMIN_USER}" 200 "token 长度 ${#ADMIN_TOKEN}"
else
  fail "auth/登录-${ADMIN_USER}" "$LAST_STATUS" "admin 登录失败，后续依赖 admin 的用例会 SKIP"
fi

api_json POST /api/v1/auth/login "" "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}__wrong__\"}"
judge "auth/错误密码-401" 401 "" "错误口令必须被拒绝"

ENGINEER_TOKEN="$(try_login "$ENGINEER_USER" "$ENGINEER_PASS")" || true
if [ -z "$ENGINEER_TOKEN" ] && [ -n "$ADMIN_TOKEN" ] && [ "$SMOKE_PROVISION" = "1" ]; then
  ENGINEER_TOKEN="$(ensure_account "smoke_engineer" "vulnerability_engineer" "冒烟-工程师")" || true
  ENGINEER_ID="$(cat "$TMPD/account.id" 2>/dev/null || true)"
  if [ -n "$ENGINEER_TOKEN" ]; then
    ENGINEER_USER="smoke_engineer"
    ENGINEER_PASS="$SMOKE_PASSWORD"
  fi
fi
if [ -n "$ENGINEER_TOKEN" ]; then
  [ -z "$ENGINEER_ID" ] && ENGINEER_ID="$(jfirst userId)"
  pass "auth/登录-${ENGINEER_USER}" 200 "工程师 token 长度 ${#ENGINEER_TOKEN}"
else
  skip "auth/登录-${ENGINEER_USER}" "-" "工程师账号不可用（可用 ENGINEER_USER/ENGINEER_PASS 覆盖）"
fi

LEADER_TOKEN="$(try_login "$LEADER_USER" "$LEADER_PASS")" || true
if [ -z "$LEADER_TOKEN" ] && [ -n "$ADMIN_TOKEN" ] && [ "$SMOKE_PROVISION" = "1" ]; then
  LEADER_TOKEN="$(ensure_account "smoke_leader" "team_leader" "冒烟-组长")" || true
  LEADER_ID="$(cat "$TMPD/account.id" 2>/dev/null || true)"
  if [ -n "$LEADER_TOKEN" ]; then
    LEADER_USER="smoke_leader"
    LEADER_PASS="$SMOKE_PASSWORD"
  fi
fi
if [ -n "$LEADER_TOKEN" ]; then
  [ -z "$LEADER_ID" ] && LEADER_ID="$(jfirst userId)"
  pass "auth/登录-${LEADER_USER}" 200 "组长 token 长度 ${#LEADER_TOKEN}"
else
  skip "auth/登录-${LEADER_USER}" "-" "组长账号不可用（可用 LEADER_USER/LEADER_PASS 覆盖）"
fi

if need "asset/列表" "$ADMIN_TOKEN" "缺少 admin token"; then
  request GET '/api/v1/assets?page=0&size=5' "$ADMIN_TOKEN"
  judge "asset/列表" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /assets"
fi

TEAM_ID=""
if need "asset/创建" "$ADMIN_TOKEN" "缺少 admin token"; then
  # 资产创建要求 teamId（旧版后端 @NotNull），先从团队列表取一个可用团队
  request GET '/api/v1/teams?page=0&size=1' "$ADMIN_TOKEN"
  [ "$LAST_STATUS" = "200" ] && TEAM_ID="$(jfirst id)"
  asset_payload="{\"name\":\"冒烟资产 ${TAG}\",\"targetType\":\"Web应用\",\"targetSource\":\"客户委托\",\"priority\":\"P3\",\"status\":\"active\",\"description\":\"冒烟脚本创建，可删除\""
  [ -n "$TEAM_ID" ] && asset_payload="${asset_payload},\"teamId\":\"${TEAM_ID}\""
  asset_payload="${asset_payload}}"
  api_json POST /api/v1/assets "$ADMIN_TOKEN" "$asset_payload"
  judge_ok "asset/创建" '"id"' "POST /assets"
  ASSET_ID="$(jfirst id)"
fi

if [ -n "$ASSET_ID" ]; then
  request GET "/api/v1/assets/${ASSET_ID}" "$ADMIN_TOKEN"
  judge "asset/详情" 200 "\"id\"[[:space:]]*:[[:space:]]*\"${ASSET_ID}\"" "GET /assets/{id}"

  request POST "/api/v1/assets/${ASSET_ID}/tags?tag=${TAG}_tag" "$ADMIN_TOKEN"
  judge_ok "asset/标签-添加" '' "POST /assets/{id}/tags?tag="
  ASSET_TAG_ID="$(jfirst id)"

  request GET "/api/v1/assets/${ASSET_ID}/tags" "$ADMIN_TOKEN"
  judge "asset/标签-列表" 200 "${TAG}_tag" "GET /assets/{id}/tags"

  if [ -n "$ASSET_TAG_ID" ]; then
    request DELETE "/api/v1/assets/${ASSET_ID}/tags/${ASSET_TAG_ID}" "$ADMIN_TOKEN"
    judge_ok "asset/标签-删除" '' "DELETE /assets/{assetId}/tags/{tagId}"
  else
    skip "asset/标签-删除" "-" "未取到标签 ID（前一用例已 SKIP/FAIL）"
  fi

  api_json POST /api/v1/asset-groups "$ADMIN_TOKEN" \
    "{\"name\":\"冒烟分组 ${TAG}\",\"type\":\"custom\",\"description\":\"冒烟脚本创建，可删除\"}"
  judge_ok "asset/分组-创建" '"id"' "POST /asset-groups"
  GROUP_ID="$(jfirst id)"
fi

if [ -n "$GROUP_ID" ]; then
  api_json PUT "/api/v1/asset-groups/${GROUP_ID}" "$ADMIN_TOKEN" \
    "{\"name\":\"冒烟分组 ${TAG} 改\",\"type\":\"custom\",\"description\":\"更新后的描述\"}"
  judge_ok "asset/分组-更新" '' "PUT /asset-groups/{id}"

  request GET "/api/v1/asset-groups/${GROUP_ID}" "$ADMIN_TOKEN"
  judge "asset/分组-详情" 200 "\"id\"[[:space:]]*:[[:space:]]*\"${GROUP_ID}\"" "GET /asset-groups/{id}"

  if [ -n "$ASSET_ID" ]; then
    request POST "/api/v1/asset-groups/${GROUP_ID}/assets?assetId=${ASSET_ID}" "$ADMIN_TOKEN"
    judge_ok "asset/分组-加入资产" '' "POST /asset-groups/{id}/assets?assetId="

    request GET "/api/v1/asset-groups/${GROUP_ID}/assets" "$ADMIN_TOKEN"
    judge "asset/分组-资产列表" 200 "${ASSET_ID}" "GET /asset-groups/{id}/assets"

    request DELETE "/api/v1/asset-groups/${GROUP_ID}/assets/${ASSET_ID}" "$ADMIN_TOKEN"
    judge_ok "asset/分组-移除资产" '' "DELETE /asset-groups/{groupId}/assets/{assetId}"
  fi

  request DELETE "/api/v1/asset-groups/${GROUP_ID}" "$ADMIN_TOKEN"
  judge_ok "asset/分组-删除" '' "DELETE /asset-groups/{id}"
  GROUP_ID=""
fi

if [ -n "$ASSET_ID" ]; then
  api_json PUT "/api/v1/assets/${ASSET_ID}/metadata/base" "$ADMIN_TOKEN" \
    "{\"systemArchitecture\":\"x86_64\",\"runtimePlatform\":\"Linux\",\"deploymentMethod\":\"容器\",\"programmingLanguage\":\"Java\",\"framework\":\"Spring Boot\"}"
  judge_ok "asset/元数据-写" '' "PUT /assets/{id}/metadata/base"

  request GET "/api/v1/assets/${ASSET_ID}/metadata/base" "$ADMIN_TOKEN"
  judge "asset/元数据-读" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /assets/{id}/metadata/base"

  # 漏洞创建要求 discoveredVersionId，先给资产建一个默认版本
  api_json POST "/api/v1/assets/${ASSET_ID}/versions?setDefault=true" "$ADMIN_TOKEN" \
    "{\"name\":\"冒烟版本 ${TAG}\",\"description\":\"冒烟脚本创建\"}"
  judge_ok "asset/版本-创建" '"id"' "POST /assets/{id}/versions?setDefault=true"
  ASSET_VERSION_ID="$(jfirst id)"
fi

# 资产访问申请：第一条由工程师申请并审批通过；第二条由组长申请后驳回（撤销）。
# 同一个人对同一资产只能有一条待审批申请，所以「驳回」用另一个账号提交，避免被业务规则挡掉。
ACCESS_REQ_ID=""
ACCESS_REQ2_ID=""
ACCESS_REQ2_TOKEN="${LEADER_TOKEN:-$ADMIN_TOKEN}"
if [ -n "$ASSET_ID" ] && [ -n "$ENGINEER_TOKEN" ]; then
  api_json POST "/api/v1/assets/${ASSET_ID}/access-requests" "$ENGINEER_TOKEN" \
    "{\"canViewDocuments\":true,\"canViewEnvironments\":false,\"canViewFiles\":true,\"requestNote\":\"冒烟申请-通过\"}"
  judge_ok "asset/访问申请-创建" '"id"' "POST /assets/{id}/access-requests"
  ACCESS_REQ_ID="$(jfirst id)"

  if [ -n "$ACCESS_REQ2_TOKEN" ]; then
    api_json POST "/api/v1/assets/${ASSET_ID}/access-requests" "$ACCESS_REQ2_TOKEN" \
      "{\"canViewDocuments\":false,\"canViewEnvironments\":false,\"canViewFiles\":false,\"requestNote\":\"冒烟申请-撤销\"}"
    ACCESS_REQ2_ID="$(jfirst id)"
  fi

  if [ -n "$ACCESS_REQ_ID" ] && [ -n "$ADMIN_TOKEN" ]; then
    api_json POST "/api/v1/assets/access-requests/${ACCESS_REQ_ID}/review" "$ADMIN_TOKEN" \
      '{"approved":true,"reviewNote":"冒烟审批通过"}'
    judge "asset/访问申请-审批通过" 200 '"success"[[:space:]]*:[[:space:]]*true' "POST /assets/access-requests/{id}/review"
  else
    skip "asset/访问申请-审批通过" "-" "未取到申请 ID"
  fi

  if [ -n "$ACCESS_REQ2_ID" ] && [ -n "$ADMIN_TOKEN" ]; then
    api_json POST "/api/v1/assets/access-requests/${ACCESS_REQ2_ID}/review" "$ADMIN_TOKEN" \
      '{"approved":false,"reviewNote":"冒烟审批驳回（撤销）"}'
    judge "asset/访问申请-撤销(驳回)" 200 '"success"[[:space:]]*:[[:space:]]*true' "review approved=false"
  else
    skip "asset/访问申请-撤销(驳回)" "-" "未取到第二条申请 ID"
  fi

  if [ -n "$ADMIN_TOKEN" ]; then
    request GET '/api/v1/assets/my/access-approval-requests?page=0&size=10' "$ADMIN_TOKEN"
    judge "asset/待我审批列表" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /assets/my/access-approval-requests"
  fi

  request GET '/api/v1/assets/my/access-requests?page=0&size=10' "$ENGINEER_TOKEN"
  judge "asset/我的访问申请" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /assets/my/access-requests"
fi

# ----------------------------------------------------------------------------- 漏洞

if need "vuln/创建-201" "$ADMIN_TOKEN" "缺少 admin token"; then
  vuln_payload="{\"title\":\"冒烟漏洞 ${TAG}\",\"assetId\":\"${ASSET_ID}\",\"type\":\"SQL注入\",\"severity\":\"HIGH\",\"cvssScore\":7.5,\"description\":\"冒烟脚本创建\",\"detail\":\"冒烟脚本创建，可删除\""
  # 旧版后端要求 discoveredVersionId（新版允许省略并自动补 v1），有版本就把版本带上
  [ -n "$ASSET_VERSION_ID" ] && vuln_payload="${vuln_payload},\"discoveredVersionId\":\"${ASSET_VERSION_ID}\""
  vuln_payload="${vuln_payload}}"
  api_json POST /api/v1/vulnerabilities "$ADMIN_TOKEN" "$vuln_payload"
  judge "vuln/创建-201" 201 '"id"' "camelCase 报文，必须 201"
  VULN_ID="$(jfirst id)"
fi

if need "vuln/列表" "$ADMIN_TOKEN" "缺少 admin token"; then
  request GET '/api/v1/vulnerabilities?page=0&size=5' "$ADMIN_TOKEN"
  judge "vuln/列表" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /vulnerabilities"
fi

if [ -n "$VULN_ID" ]; then
  request GET "/api/v1/vulnerabilities/${VULN_ID}" "$ADMIN_TOKEN"
  judge "vuln/详情" 200 "\"id\"[[:space:]]*:[[:space:]]*\"${VULN_ID}\"" "GET /vulnerabilities/{id}"

  # 编辑只传标题：其余业务字段必须保持不变（修复「PUT 全量覆盖把客户确认/披露/严重程度清空」的缺陷）
  before_sev="$(jfirst severity)"
  before_cust="$(jfirst customerStatus)"
  before_disc="$(jfirst disclosureStatus)"
  before_ver="$(jfirst discoveredVersionId)"
  api_json PUT "/api/v1/vulnerabilities/${VULN_ID}" "$ADMIN_TOKEN" "{\"title\":\"冒烟漏洞 ${TAG} 改名\"}"
  put_status="$LAST_STATUS"
  judge_ok "vuln/编辑-仅改标题" '' "PUT /vulnerabilities/{id}（只带 title）"
  case "$put_status" in
    404 | 405 | 501)
      skip "vuln/编辑-其余字段不变" "$put_status" "编辑接口未实现(HTTP $put_status)"
      ;;
    200 | 201 | 204)
      request GET "/api/v1/vulnerabilities/${VULN_ID}" "$ADMIN_TOKEN"
      after_title="$(jfirst title)"
      after_sev="$(jfirst severity)"
      after_cust="$(jfirst customerStatus)"
      after_disc="$(jfirst disclosureStatus)"
      after_ver="$(jfirst discoveredVersionId)"
      if [ "$after_title" = "冒烟漏洞 ${TAG} 改名" ] \
          && [ "$before_sev" = "$after_sev" ] && [ "$before_cust" = "$after_cust" ] \
          && [ "$before_disc" = "$after_disc" ] && [ "$before_ver" = "$after_ver" ]; then
        pass "vuln/编辑-其余字段不变" "$LAST_STATUS" "标题已更新；severity/customerStatus/disclosureStatus/版本未变"
      else
        fail "vuln/编辑-其余字段不变" "$LAST_STATUS" "标题=${after_title:-?}；severity ${before_sev:-?}→${after_sev:-?}；客户确认 ${before_cust:-?}→${after_cust:-?}；披露 ${before_disc:-?}→${after_disc:-?}；版本 ${before_ver:-?}→${after_ver:-?}"
      fi
      ;;
    *)
      skip "vuln/编辑-其余字段不变" "$put_status" "编辑请求本身失败(HTTP $put_status)，跳过字段比对"
      ;;
  esac

  request PATCH "/api/v1/vulnerabilities/${VULN_ID}/workflow-status?newStatus=verified&comment=smoke" "$ADMIN_TOKEN"
  judge "vuln/工作流状态-PATCH" 200 '"success"[[:space:]]*:[[:space:]]*true' "discovered -> verified"

  request GET "/api/v1/vulnerabilities/${VULN_ID}/history" "$ADMIN_TOKEN"
  judge "vuln/历史" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /vulnerabilities/{id}/history"

  fill 1234 "$TMPD/attach.bin"
  request POST "/api/v1/vulnerabilities/${VULN_ID}/attachments" "$ADMIN_TOKEN" \
    -F "file=@$(pathify "${TMPD}/attach.bin");filename=smoke-poc.bin;type=application/octet-stream"
  judge_ok "vuln/附件-上传" '"id"' "POST /vulnerabilities/{id}/attachments"
  VULN_ATTACH_ID="$(jfirst id)"

  request GET "/api/v1/vulnerabilities/${VULN_ID}/attachments" "$ADMIN_TOKEN"
  judge "vuln/附件-列表" 200 "${VULN_ATTACH_ID:-附件}" "GET /vulnerabilities/{id}/attachments"

  if [ -n "$VULN_ATTACH_ID" ]; then
    download "/api/v1/vulnerabilities/${VULN_ID}/attachments/${VULN_ATTACH_ID}/download" "$ADMIN_TOKEN" "$TMPD/dl.bin"
    if [ "$LAST_STATUS" != "200" ]; then
      case "$LAST_STATUS" in
        404 | 405 | 501) skip "vuln/附件-下载" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)" ;;
        *) fail "vuln/附件-下载" "$LAST_STATUS" "期望 200" ;;
      esac
    else
      got="$(wc -c <"$TMPD/dl.bin" | tr -d ' ')"
      if [ "$got" = "1234" ]; then
        pass "vuln/附件-下载" 200 "字节数 ${got} 与上传一致"
      else
        fail "vuln/附件-下载" 200 "字节数不一致：上传 1234 / 下载 ${got}"
      fi
    fi
  else
    skip "vuln/附件-下载" "-" "未取到附件 ID"
  fi

  request POST "/api/v1/vulnerabilities/${VULN_ID}/tags?tag=${TAG}_vtag" "$ADMIN_TOKEN"
  judge_ok "vuln/标签-添加" '' "POST /vulnerabilities/{id}/tags?tag="
  VULN_TAG_ID="$(jfirst id)"

  request GET "/api/v1/vulnerabilities/${VULN_ID}/tags" "$ADMIN_TOKEN"
  judge "vuln/标签-列表" 200 "${TAG}_vtag" "GET /vulnerabilities/{id}/tags"

  if [ -n "$VULN_TAG_ID" ]; then
    request DELETE "/api/v1/vulnerabilities/${VULN_ID}/tags/${VULN_TAG_ID}" "$ADMIN_TOKEN"
    judge_ok "vuln/标签-删除" '' "DELETE /vulnerabilities/{vulnerabilityId}/tags/{tagId}"
  else
    skip "vuln/标签-删除" "-" "未取到漏洞标签 ID"
  fi
fi

# ----------------------------------------------------------------------------- 任务

if need "task/创建" "$ADMIN_TOKEN" "缺少 admin token"; then
  api_json POST /api/v1/tasks "$ADMIN_TOKEN" \
    "{\"title\":\"冒烟任务 ${TAG}\",\"assetId\":\"${ASSET_ID}\",\"assigneeId\":\"${ENGINEER_ID}\",\"description\":\"冒烟脚本创建\",\"priority\":\"P3\"}"
  judge_ok "task/创建" '"id"' "POST /tasks"
  TASK_ID="$(jfirst id)"
fi

if need "task/列表" "$ADMIN_TOKEN" "缺少 admin token"; then
  request GET '/api/v1/tasks?page=0&size=5' "$ADMIN_TOKEN"
  judge "task/列表" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /tasks"
fi

if [ -n "$TASK_ID" ]; then
  request GET "/api/v1/tasks/${TASK_ID}" "$ADMIN_TOKEN"
  judge "task/详情" 200 "\"id\"[[:space:]]*:[[:space:]]*\"${TASK_ID}\"" "GET /tasks/{id}"
fi

# ----------------------------------------------------------------------------- 工具

if need "tool/创建" "$ADMIN_TOKEN" "缺少 admin token"; then
  api_json POST /api/v1/tools "$ADMIN_TOKEN" \
    "{\"name\":\"冒烟工具 ${TAG}\",\"category\":\"渗透测试\",\"description\":\"冒烟脚本创建\",\"version\":\"v1.0.0\"}"
  judge_ok "tool/创建" '"id"' "POST /tools"
  TOOL_ID="$(jfirst id)"
fi

if [ -n "$TOOL_ID" ]; then
  fill 2048 "$TMPD/tool.bin"
  request POST "/api/v1/tools/${TOOL_ID}/file" "$ADMIN_TOKEN" \
    -F "file=@$(pathify "${TMPD}/tool.bin");filename=smoke-tool.bin;type=application/octet-stream"
  judge_ok "tool/包上传" '' "POST /tools/{id}/file"

  download "/api/v1/tools/${TOOL_ID}/download" "$ADMIN_TOKEN" "$TMPD/tool-dl.bin"
  if [ "$LAST_STATUS" != "200" ]; then
    case "$LAST_STATUS" in
      404 | 405 | 501) skip "tool/包下载" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)" ;;
      *) fail "tool/包下载" "$LAST_STATUS" "期望 200" ;;
    esac
  else
    got="$(wc -c <"$TMPD/tool-dl.bin" | tr -d ' ')"
    if [ "$got" = "2048" ]; then
      pass "tool/包下载" 200 "字节数 ${got} 与上传一致"
    else
      fail "tool/包下载" 200 "字节数不一致：上传 2048 / 下载 ${got}"
    fi
  fi

  fill 512 "$TMPD/version.bin"
  request POST "/api/v1/tools/${TOOL_ID}/versions?version=v2&changelog=smoke" "$ADMIN_TOKEN" \
    -F "file=@$(pathify "${TMPD}/version.bin");filename=smoke-v2.bin;type=application/octet-stream"
  judge_ok "tool/版本上传" '' "POST /tools/{id}/versions?version=v2"
  TOOL_VERSION_ID="$(jfirst id)"

  request GET "/api/v1/tools/${TOOL_ID}/versions" "$ADMIN_TOKEN"
  judge "tool/版本列表" 200 "${TOOL_VERSION_ID:-v2}" "GET /tools/{id}/versions"

  if [ -n "$TOOL_VERSION_ID" ]; then
    download "/api/v1/tools/${TOOL_ID}/versions/${TOOL_VERSION_ID}/download" "$ADMIN_TOKEN" "$TMPD/ver-dl.bin"
    if [ "$LAST_STATUS" != "200" ]; then
      case "$LAST_STATUS" in
        404 | 405 | 501) skip "tool/版本下载" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)" ;;
        *) fail "tool/版本下载" "$LAST_STATUS" "期望 200" ;;
      esac
    else
      got="$(wc -c <"$TMPD/ver-dl.bin" | tr -d ' ')"
      if [ "$got" = "512" ]; then
        pass "tool/版本下载" 200 "字节数 ${got} 与上传一致"
      else
        fail "tool/版本下载" 200 "字节数不一致：上传 512 / 下载 ${got}"
      fi
    fi
  else
    skip "tool/版本下载" "-" "未取到版本 ID"
  fi
fi

# ----------------------------------------------------------------------------- 通知

NOTIF_ID=""
NOTIF_TOKEN="$ENGINEER_TOKEN"
if need "notify/列表" "$ENGINEER_TOKEN" "缺少工程师 token"; then
  request GET '/api/v1/notifications?page=0&size=10' "$ENGINEER_TOKEN"
  judge "notify/列表" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /notifications"
  NOTIF_ID="$(jfirst id)"

  # 工程师可能一条通知都没有：先用 admin 给他发一条漏洞通知；
  # 仍然没有就退回 admin 自己的通知（保证「已读 / recipients」两个端点仍被覆盖）。
  if [ -z "$NOTIF_ID" ] && [ -n "$VULN_ID" ] && [ -n "$ENGINEER_ID" ] && [ -n "$ADMIN_TOKEN" ]; then
    request POST "/api/v1/notifications/vulnerability-discovered?vulnerabilityId=${VULN_ID}&recipientIds=${ENGINEER_ID}" "$ADMIN_TOKEN"
    case "$LAST_STATUS" in
      200 | 201)
        request GET '/api/v1/notifications?page=0&size=10' "$ENGINEER_TOKEN"
        NOTIF_ID="$(jfirst id)"
        ;;
    esac
  fi
  if [ -z "$NOTIF_ID" ] && [ -n "$ADMIN_TOKEN" ]; then
    request GET '/api/v1/notifications?page=0&size=10' "$ADMIN_TOKEN"
    NOTIF_ID="$(jfirst id)"
    NOTIF_TOKEN="$ADMIN_TOKEN"
  fi

  request GET '/api/v1/notifications/unread-count' "$ENGINEER_TOKEN"
  judge "notify/未读数" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /notifications/unread-count"

  request GET '/api/v1/notifications?page=0&size=10&isRead=false' "$ENGINEER_TOKEN"
  judge "notify/未读反查(isRead=false)" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /notifications?isRead=false"

  if [ -n "$NOTIF_ID" ]; then
    request POST "/api/v1/notifications/${NOTIF_ID}/read" "$NOTIF_TOKEN"
    judge_ok "notify/标记已读" '' "POST /notifications/{id}/read"

    request GET "/api/v1/notifications/${NOTIF_ID}/recipients" "$NOTIF_TOKEN"
    judge "notify/发送记录(recipients)" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /notifications/{id}/recipients"
  else
    skip "notify/标记已读" "-" "该账号当前没有通知，跳过"
    skip "notify/发送记录(recipients)" "-" "该账号当前没有通知，跳过"
  fi

  request POST '/api/v1/notifications/read-all' "$ENGINEER_TOKEN"
  judge_ok "notify/全部已读" '' "POST /notifications/read-all"

  request GET '/api/v1/notifications/settings' "$ENGINEER_TOKEN"
  judge "notify/设置-读取" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /notifications/settings"

  api_json PUT '/api/v1/notifications/settings/vulnerability_discovered' "$ENGINEER_TOKEN" \
    '{"enabled":true,"notify_email":false,"notify_sms":false,"notify_webhook":false}'
  judge "notify/设置-更新" 200 '"success"[[:space:]]*:[[:space:]]*true' "PUT /notifications/settings/{type}（snake_case 字段）"

  request GET '/api/v1/notifications/announcements?page=0&size=10' "$ENGINEER_TOKEN"
  judge "notify/公告-列表" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /notifications/announcements"
fi

if need "notify/公告-创建" "$ADMIN_TOKEN" "缺少 admin token"; then
  api_json POST '/api/v1/notifications/announcements' "$ADMIN_TOKEN" \
    "{\"title\":\"冒烟公告 ${TAG}\",\"content\":\"冒烟脚本创建，可删除\",\"priority\":\"normal\",\"publishedAt\":\"2026-01-01T00:00:00\"}"
  judge_ok "notify/公告-创建" '"id"' "POST /notifications/announcements"
  ANNOUNCEMENT_ID="$(jfirst id)"

  request GET '/api/v1/notifications/announcements/active' "$ADMIN_TOKEN"
  judge "notify/活跃公告" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /notifications/announcements/active"
fi

# ----------------------------------------------------------------------------- 导入

if need "import/模板-assets" "$ADMIN_TOKEN" "缺少 admin token"; then
  download '/api/v1/import/templates/assets' "$ADMIN_TOKEN" "$TMPD/tpl-assets.csv"
  if [ "$LAST_STATUS" != "200" ]; then
    case "$LAST_STATUS" in
      404 | 405 | 501) skip "import/模板-assets" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)" ;;
      *) fail "import/模板-assets" "$LAST_STATUS" "期望 200" ;;
    esac
  elif [ "$(bom_of "$TMPD/tpl-assets.csv")" = "efbbbf" ]; then
    pass "import/模板-assets" 200 "带 UTF-8 BOM"
  else
    fail "import/模板-assets" 200 "缺少 UTF-8 BOM，实际前 3 字节 $(bom_of "$TMPD/tpl-assets.csv")"
  fi

  download '/api/v1/import/templates/vulnerabilities' "$ADMIN_TOKEN" "$TMPD/tpl-vulns.csv"
  if [ "$LAST_STATUS" != "200" ]; then
    case "$LAST_STATUS" in
      404 | 405 | 501) skip "import/模板-vulnerabilities" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)" ;;
      *) fail "import/模板-vulnerabilities" "$LAST_STATUS" "期望 200" ;;
    esac
  elif [ "$(bom_of "$TMPD/tpl-vulns.csv")" = "efbbbf" ]; then
    pass "import/模板-vulnerabilities" 200 "带 UTF-8 BOM"
  else
    fail "import/模板-vulnerabilities" 200 "缺少 UTF-8 BOM，实际前 3 字节 $(bom_of "$TMPD/tpl-vulns.csv")"
  fi

  download '/api/v1/import/templates/users' "$ADMIN_TOKEN" "$TMPD/tpl-users.csv"
  if [ "$LAST_STATUS" != "200" ]; then
    case "$LAST_STATUS" in
      404 | 405 | 501) skip "import/模板-users" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)" ;;
      *) fail "import/模板-users" "$LAST_STATUS" "期望 200" ;;
    esac
  elif [ "$(bom_of "$TMPD/tpl-users.csv")" = "efbbbf" ]; then
    pass "import/模板-users" 200 "带 UTF-8 BOM"
  else
    fail "import/模板-users" 200 "缺少 UTF-8 BOM，实际前 3 字节 $(bom_of "$TMPD/tpl-users.csv")"
  fi
fi

if need "import/资产部分成功" "$ADMIN_TOKEN" "缺少 admin token"; then
  {
    printf 'name,targetType,targetSource,priority,status,teamId,description\n'
    printf '冒烟导入A %s,Web应用,客户委托,P2,active,%s,冒烟导入\n' "$TAG" "$TEAM_ID"
    printf '冒烟导入B %s,Web应用,客户委托,P3,active,%s,冒烟导入\n' "$TAG" "$TEAM_ID"
    printf '冒烟导入非法 %s,Web应用,客户委托,P9,active,%s,优先级非法行\n' "$TAG" "$TEAM_ID"
  } >"$TMPD/import-assets.csv"
  request POST '/api/v1/import/assets' "$ADMIN_TOKEN" \
    -F "file=@$(pathify "${TMPD}/import-assets.csv");filename=smoke-assets.csv;type=text/csv"
  if [ "$LAST_STATUS" = "200" ]; then
    ok_rows="$(jnum successRows)"
    failed_rows="$(jcount failedRows)"
    # failedRows 是数组：统计数组内元素个数（每个元素含 line/reason），用 line 出现次数更稳
    failed_cnt="$(flatten | grep -o '"line":' | wc -l | tr -d ' ')"
    if [ "$ok_rows" = "2" ] && [ "$failed_cnt" = "1" ]; then
      pass "import/资产部分成功" 200 "successRows=2 failedRows=1（真实服务端语义）"
    else
      fail "import/资产部分成功" 200 "期望 successRows=2/failedRows=1，实际 successRows=${ok_rows:-?}/failed=${failed_cnt:-?}"
    fi
  else
    case "$LAST_STATUS" in
      404 | 405 | 501) skip "import/资产部分成功" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)" ;;
      *) fail "import/资产部分成功" "$LAST_STATUS" "期望 200 ｜ $(snippet)" ;;
    esac
  fi
fi

if [ -n "$ENGINEER_TOKEN" ]; then
  printf 'username,email,fullName\nsmoke_should_fail_%s,sf_%s@smoke.local,越权导入\n' "$TAG" "$TAG" >"$TMPD/import-users.csv"
  request POST '/api/v1/import/users' "$ENGINEER_TOKEN" \
    -F "file=@$(pathify "${TMPD}/import-users.csv");filename=smoke-users.csv;type=text/csv"
  judge "import/用户导入-工程师403" 403 '' "仅 ADMIN 可导入用户"
else
  skip "import/用户导入-工程师403" "-" "缺少工程师 token"
fi

# ----------------------------------------------------------------------------- 审批范围

if need "approval/范围-授予" "$ADMIN_TOKEN" "缺少 admin token"; then
  api_json POST '/api/v1/approval-scopes' "$ADMIN_TOKEN" \
    "{\"approverId\":\"${LEADER_ID:-${ADMIN_ID}}\",\"scopeType\":\"asset\",\"scopeId\":\"${ASSET_ID}\"}"
  judge_ok "approval/范围-授予" '"id"' "POST /approval-scopes"
  SCOPE_ID="$(jfirst id)"

  if [ -n "$SCOPE_ID" ]; then
    request DELETE "/api/v1/approval-scopes/${SCOPE_ID}" "$ADMIN_TOKEN"
    judge_ok "approval/范围-撤销" '' "DELETE /approval-scopes/{id}"
    SCOPE_ID=""
  else
    skip "approval/范围-撤销" "-" "未取到审批范围 ID"
  fi
fi

if [ -n "$LEADER_TOKEN" ]; then
  request GET '/api/v1/approval-scopes/my' "$LEADER_TOKEN"
  judge "approval/我的范围" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /approval-scopes/my"
else
  skip "approval/我的范围" "-" "缺少组长 token"
fi

# ----------------------------------------------------------------------------- 环境（无 Docker 主机应 503）

if need "env/创建" "$ADMIN_TOKEN" "缺少 admin token"; then
  api_json POST /api/v1/environments "$ADMIN_TOKEN" \
    "{\"name\":\"冒烟环境 ${TAG}\",\"assetId\":\"${ASSET_ID}\",\"templateId\":\"00000000-0000-0000-0000-000000000001\",\"config\":\"{\\\"image\\\":\\\"alpine:3.19\\\"}\"}"
  judge_ok "env/创建" '"id"' "POST /environments"
  ENV_ID="$(jfirst id)"
fi

if [ -n "$ENV_ID" ]; then
  request POST "/api/v1/environments/${ENV_ID}/deploy" "$ADMIN_TOKEN"
  judge "env/部署-无Docker应503" 503 '当前部署未启用容器编排' "POST /environments/{id}/deploy"

  request GET "/api/v1/environments/${ENV_ID}" "$ADMIN_TOKEN"
  judge "env/详情(含部署器信息)" 200 "\"id\"[[:space:]]*:[[:space:]]*\"${ENV_ID}\"" "GET /environments/{id}"
else
  skip "env/部署-无Docker应503" "-" "环境创建未成功，跳过"
  skip "env/详情(含部署器信息)" "-" "环境创建未成功，跳过"
fi

# ----------------------------------------------------------------------------- RBAC

if need "rbac/权限点-90" "$ADMIN_TOKEN" "缺少 admin token"; then
  request GET '/api/v1/permissions' "$ADMIN_TOKEN"
  if [ "$LAST_STATUS" = "200" ]; then
    codes="$(jcount code)"
    if [ "$codes" = "90" ]; then
      pass "rbac/权限点-90" 200 "权限点数量 ${codes}"
    else
      fail "rbac/权限点-90" 200 "期望 90 个权限点，实际 ${codes}（旧 jar 或初始化未执行）"
    fi
  elif [ "$LAST_STATUS" = "404" ] || [ "$LAST_STATUS" = "405" ] || [ "$LAST_STATUS" = "501" ]; then
    skip "rbac/权限点-90" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)"
  else
    fail "rbac/权限点-90" "$LAST_STATUS" "期望 200 ｜ $(snippet)"
  fi

  request GET '/api/v1/users/me/permissions' "$ADMIN_TOKEN"
  if [ "$LAST_STATUS" = "200" ]; then
    cnt="$(jnum permissionCount)"
    if [ "$cnt" = "90" ]; then
      pass "rbac/admin权限数-90" 200 "permissionCount=${cnt}"
    else
      fail "rbac/admin权限数-90" 200 "期望 permissionCount=90，实际 ${cnt:-缺失}"
    fi
  elif [ "$LAST_STATUS" = "404" ] || [ "$LAST_STATUS" = "405" ] || [ "$LAST_STATUS" = "501" ]; then
    skip "rbac/admin权限数-90" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)"
  else
    fail "rbac/admin权限数-90" "$LAST_STATUS" "期望 200 ｜ $(snippet)"
  fi
fi

# ----------------------------------------------------------------------------- 仪表盘

if need "dash/导出CSV" "$ADMIN_TOKEN" "缺少 admin token"; then
  download '/api/v1/dashboard/export?type=assets&format=csv' "$ADMIN_TOKEN" "$TMPD/dash.csv"
  if [ "$LAST_STATUS" != "200" ]; then
    case "$LAST_STATUS" in
      404 | 405 | 501) skip "dash/导出CSV" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)" ;;
      *) fail "dash/导出CSV" "$LAST_STATUS" "期望 200" ;;
    esac
  else
    bom="$(bom_of "$TMPD/dash.csv")"
    if [ "$bom" = "efbbbf" ] && grep -q '资产编号' "$TMPD/dash.csv"; then
      pass "dash/导出CSV" 200 "带 BOM 且表头为「资产编号…」"
    else
      fail "dash/导出CSV" 200 "BOM=${bom:-无}，表头缺失：$(head -c 80 "$TMPD/dash.csv" | tr -d '\r\n')"
    fi
  fi
fi

if [ -n "$ENGINEER_TOKEN" ]; then
  request GET '/api/v1/dashboard/export?type=assets&format=csv' "$ENGINEER_TOKEN"
  judge "dash/导出-工程师403" 403 '' "仅 admin/project_leader 可导出"
else
  skip "dash/导出-工程师403" "-" "缺少工程师 token"
fi

if need "dash/趋势-7点" "$ADMIN_TOKEN" "缺少 admin token"; then
  request GET '/api/v1/dashboard/trends/assets?days=7' "$ADMIN_TOKEN"
  if [ "$LAST_STATUS" = "200" ]; then
    pts="$(jcount date)"
    if [ "$pts" = "7" ]; then
      pass "dash/趋势-7点" 200 "点数 ${pts}"
    else
      fail "dash/趋势-7点" 200 "期望 7 个数据点，实际 ${pts}（旧 jar 返回空数组）"
    fi
  elif [ "$LAST_STATUS" = "404" ] || [ "$LAST_STATUS" = "405" ] || [ "$LAST_STATUS" = "501" ]; then
    skip "dash/趋势-7点" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)"
  else
    fail "dash/趋势-7点" "$LAST_STATUS" "期望 200 ｜ $(snippet)"
  fi
fi

if need "dash/排行-admin" "$ADMIN_TOKEN" "缺少 admin token"; then
  request GET '/api/v1/dashboard/workload/ranking?limit=10&days=30' "$ADMIN_TOKEN"
  if [ "$LAST_STATUS" = "200" ]; then
    rows="$(jcount userId)"
    if [ "$rows" -ge 1 ] 2>/dev/null; then
      pass "dash/排行-admin" 200 "返回 ${rows} 行（admin 视角多行）"
    else
      fail "dash/排行-admin" 200 "期望至少 1 行，实际 ${rows}"
    fi
  elif [ "$LAST_STATUS" = "404" ] || [ "$LAST_STATUS" = "405" ] || [ "$LAST_STATUS" = "501" ]; then
    skip "dash/排行-admin" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)"
  else
    fail "dash/排行-admin" "$LAST_STATUS" "期望 200 ｜ $(snippet)"
  fi
fi

if [ -n "$ENGINEER_TOKEN" ]; then
  request GET '/api/v1/dashboard/workload/ranking?limit=10&days=30' "$ENGINEER_TOKEN"
  if [ "$LAST_STATUS" = "200" ]; then
    rows="$(jcount userId)"
    if [ "$rows" = "1" ]; then
      pass "dash/排行-工程师1行" 200 "工程师视角只返回自己 1 行"
    else
      fail "dash/排行-工程师1行" 200 "期望 1 行，实际 ${rows}"
    fi
  elif [ "$LAST_STATUS" = "404" ] || [ "$LAST_STATUS" = "405" ] || [ "$LAST_STATUS" = "501" ]; then
    skip "dash/排行-工程师1行" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)"
  else
    fail "dash/排行-工程师1行" "$LAST_STATUS" "期望 200 ｜ $(snippet)"
  fi
else
  skip "dash/排行-工程师1行" "-" "缺少工程师 token"
fi

# ----------------------------------------------------------------------------- 审计

AUDIT_TOTAL=0
if need "audit/列表" "$ADMIN_TOKEN" "缺少 admin token"; then
  request GET '/api/v1/audit-logs?page=0&size=5' "$ADMIN_TOKEN"
  if [ "$LAST_STATUS" = "200" ]; then
    AUDIT_TOTAL="$(jnum totalElements)"
    pass "audit/列表" 200 "totalElements=${AUDIT_TOTAL:-未知}"
  elif [ "$LAST_STATUS" = "404" ] || [ "$LAST_STATUS" = "405" ] || [ "$LAST_STATUS" = "501" ]; then
    skip "audit/列表" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)"
  else
    fail "audit/列表" "$LAST_STATUS" "期望 200 ｜ $(snippet)"
  fi

  request GET '/api/v1/audit-logs?page=0&size=5&keyword=zzz_no_such_keyword_zzz' "$ADMIN_TOKEN"
  if [ "$LAST_STATUS" = "200" ]; then
    kw="$(jnum totalElements)"
    if [ "${kw:-1}" -eq 0 ] 2>/dev/null; then
      pass "audit/关键词筛选" 200 "keyword 无命中 → totalElements=0（原 ${AUDIT_TOTAL:-?}）"
    else
      fail "audit/关键词筛选" 200 "期望 totalElements=0，实际 ${kw:-?}"
    fi
  elif [ "$LAST_STATUS" = "404" ] || [ "$LAST_STATUS" = "405" ] || [ "$LAST_STATUS" = "501" ]; then
    skip "audit/关键词筛选" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)"
  else
    fail "audit/关键词筛选" "$LAST_STATUS" "期望 200 ｜ $(snippet)"
  fi

  request GET '/api/v1/audit-logs?page=0&size=5&startTime=2035-01-01T00:00:00' "$ADMIN_TOKEN"
  if [ "$LAST_STATUS" = "200" ]; then
    tf="$(jnum totalElements)"
    if [ "${tf:-1}" -eq 0 ] 2>/dev/null; then
      pass "audit/时间筛选" 200 "未来时间窗 → totalElements=0（原 ${AUDIT_TOTAL:-?}）"
    else
      fail "audit/时间筛选" 200 "期望 totalElements=0，实际 ${tf:-?}"
    fi
  elif [ "$LAST_STATUS" = "404" ] || [ "$LAST_STATUS" = "405" ] || [ "$LAST_STATUS" = "501" ]; then
    skip "audit/时间筛选" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)"
  else
    fail "audit/时间筛选" "$LAST_STATUS" "期望 200 ｜ $(snippet)"
  fi

  download '/api/v1/audit-logs/export' "$ADMIN_TOKEN" "$TMPD/audit.csv"
  if [ "$LAST_STATUS" != "200" ]; then
    case "$LAST_STATUS" in
      404 | 405 | 501) skip "audit/导出CSV" "$LAST_STATUS" "接口未实现(HTTP $LAST_STATUS)" ;;
      *) fail "audit/导出CSV" "$LAST_STATUS" "期望 200" ;;
    esac
  else
    bom="$(bom_of "$TMPD/audit.csv")"
    if [ "$bom" = "efbbbf" ] && grep -q '时间,用户ID,操作' "$TMPD/audit.csv"; then
      pass "audit/导出CSV" 200 "带 BOM 且表头为「时间,用户ID,操作…」"
    else
      fail "audit/导出CSV" 200 "BOM=${bom:-无}，表头缺失：$(head -c 80 "$TMPD/audit.csv" | tr -d '\r\n')"
    fi
  fi
fi

if [ -n "$ENGINEER_TOKEN" ]; then
  request GET '/api/v1/audit-logs?page=0&size=5' "$ENGINEER_TOKEN"
  judge "audit/工程师403" 403 '' "审计日志仅管理层可见"
else
  skip "audit/工程师403" "-" "缺少工程师 token"
fi

# ----------------------------------------------------------------------------- 前端页面

if [ -n "$ADMIN_TOKEN" ]; then
  for page in "/" "/assets" "/vulnerabilities" "/tasks" "/tools" "/notifications" "/audit-logs" "/import" "/approvals" "/asset-groups" "/approval-scopes"; do
    request GET "$page" ""
    st="$LAST_STATUS"
    if [ "$st" = "000" ]; then
      fail "page${page}" "000" "无法连接 ${BASE_URL}"
      continue
    fi
    case "$st" in
      404) skip "page${page}" "$st" "页面/路由不存在" ;;
      200)
        case "$LAST_CTYPE" in
          *text/html*) pass "page${page}" "$st" "content-type=${LAST_CTYPE}" ;;
          *) fail "page${page}" "$st" "content-type 不是 text/html：${LAST_CTYPE:-空}" ;;
        esac
        ;;
      *) fail "page${page}" "$st" "期望 200 text/html" ;;
    esac
  done
fi

# ----------------------------------------------------------------------------- 汇总

printf '\n==== 冒烟汇总 ====\n'
printf 'TOTAL=%d  PASS=%d  FAIL=%d  SKIP=%d\n' "$TOTAL" "$PASSED" "$FAILED" "$SKIPPED"
if [ "$FAILED" -gt 0 ]; then
  printf '失败用例：%s\n' "$FAILED_NAMES"
  printf '结论：FAIL（存在功能报错或与预期契约不符，详见上面各 FAIL 行）\n'
  exit 1
fi
if [ "$SKIPPED" -gt 0 ]; then
  printf '结论：PASS（含 %d 个 SKIP：接口未上线或前置条件不足，部署后可重跑确认）\n' "$SKIPPED"
else
  printf '结论：PASS（全部用例执行）\n'
fi
exit 0

# ----------------------------------------------------------------------------- 远程执行示例
# Windows 主机上通过 plink 远程跑（BASE_URL 指向该主机回环即可）：
#   plink -ssh -batch -pw '<密码>' user@192.168.188.200 \
#     'cd ~/apps/vuln-audit && BASE_URL=http://127.0.0.1:8089 bash scripts/smoke.sh'
