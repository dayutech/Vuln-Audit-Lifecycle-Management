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

# ----------------------------------------------------------------------------- 客户 / 项目 / 期次前置（t75）
# POST /assets 自 t69/t78 起强制要求 projectId（缺则 400），因此所有建资产用例都先用这里的前置数据。
CUST_ID=""
CUST2_ID=""
PROJECT_ID=""
PROJECT2_ID=""
PHASE1_ID=""
PHASE2_ID=""
if need "cust/创建" "$ADMIN_TOKEN" "缺少 admin token"; then
  api_json POST '/api/v1/customers' "$ADMIN_TOKEN" \
    "{\"code\":\"SMK-CUST-${TAG}\",\"name\":\"冒烟客户 ${TAG}\",\"industry\":\"互联网\",\"level\":\"A\",\"contactName\":\"冒烟联系人\",\"description\":\"冒烟脚本创建\"}"
  judge_ok "cust/创建" '"id"' "POST /customers"
  CUST_ID="$(jfirst id)"

  api_json POST '/api/v1/customers' "$ADMIN_TOKEN" \
    "{\"code\":\"SMK-CUST2-${TAG}\",\"name\":\"冒烟客户二 ${TAG}\",\"industry\":\"制造业\",\"level\":\"B\",\"description\":\"冒烟脚本创建\"}"
  judge_ok "cust/创建-第二个" '"id"' "POST /customers（跨客户挂靠用）"
  CUST2_ID="$(jfirst id)"
fi

if [ -n "$CUST_ID" ]; then
  api_json POST '/api/v1/projects' "$ADMIN_TOKEN" \
    "{\"code\":\"SMK-PRJ-${TAG}\",\"name\":\"冒烟项目 ${TAG}\",\"customerId\":\"${CUST_ID}\",\"managerId\":\"${ADMIN_ID}\",\"type\":\"PENTEST\",\"isPhased\":true}"
  judge_ok "proj/创建-分期" '"id"' "POST /projects（isPhased=true）"
  PROJECT_ID="$(jfirst id)"

  if [ -n "$PROJECT_ID" ]; then
    api_json POST "/api/v1/projects/${PROJECT_ID}/phases" "$ADMIN_TOKEN" \
      "{\"name\":\"一期 ${TAG}\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-03-31\"}"
    judge_ok "proj/期次-创建1" '"id"' "POST /projects/{id}/phases"
    PHASE1_ID="$(jfirst id)"
    api_json POST "/api/v1/projects/${PROJECT_ID}/phases" "$ADMIN_TOKEN" \
      "{\"name\":\"二期 ${TAG}\",\"startDate\":\"2026-04-01\",\"endDate\":\"2026-06-30\"}"
    judge_ok "proj/期次-创建2" '"id"' "POST /projects/{id}/phases"
    PHASE2_ID="$(jfirst id)"
    request GET "/api/v1/projects/${PROJECT_ID}" "$ADMIN_TOKEN"
    judge "proj/详情-含期次" 200 '"phases"' "GET /projects/{id}"
  fi
  if [ -n "$CUST2_ID" ]; then
    api_json POST '/api/v1/projects' "$ADMIN_TOKEN" \
      "{\"code\":\"SMK-PRJ2-${TAG}\",\"name\":\"冒烟项目二 ${TAG}\",\"customerId\":\"${CUST2_ID}\",\"managerId\":\"${ADMIN_ID}\",\"type\":\"PENTEST\",\"isPhased\":false}"
    judge_ok "proj/创建-第二个(不分期)" '"id"' "POST /projects（跨客户挂靠用）"
    PROJECT2_ID="$(jfirst id)"
  fi
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
  # t69/t78：建资产必须挂项目（缺 projectId 直接 400）
  [ -n "$PROJECT_ID" ] && asset_payload="${asset_payload},\"projectId\":\"${PROJECT_ID}\""
  [ -n "$PHASE1_ID" ] && asset_payload="${asset_payload},\"phaseId\":\"${PHASE1_ID}\""
  [ -n "$PROJECT_ID" ] && asset_payload="${asset_payload},\"linkType\":\"TARGET\""
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
  # t93 起：非团队成员/非负责人申请会被 403，正例改由管理员发起（跨团队 403 由 audit/access-request 段覆盖）
  api_json POST "/api/v1/assets/${ASSET_ID}/access-requests" "$ADMIN_TOKEN" \
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

# ----------------------------------------------------------------------------- 认领生命周期（t39）
# 覆盖：申请 → 审批 → 本人完成（修复前恒 403）→ 状态 completed；
#       非认领人（含管理员）完成 403；管理层强制放弃 200；未登录读我的认领 401。
CLAIM_ID=""
CLAIM2_ID=""
if [ -n "$ASSET_ID" ] && [ -n "$ENGINEER_TOKEN" ] && [ -n "$ADMIN_TOKEN" ]; then
  api_json POST "/api/v1/assets/${ASSET_ID}/claims" "$ENGINEER_TOKEN" \
    "{\"assetId\":\"${ASSET_ID}\",\"note\":\"冒烟认领-本人完成\"}"
  judge_ok "claim/创建" '"id"' "POST /assets/{id}/claims"
  CLAIM_ID="$(jfirst id)"

  if [ -n "$CLAIM_ID" ]; then
    api_json POST "/api/v1/assets/claims/${CLAIM_ID}/review" "$ADMIN_TOKEN" \
      '{"approved":true,"reviewNote":"冒烟认领审批通过"}'
    judge "claim/审批通过" 200 '"success"[[:space:]]*:[[:space:]]*true' "POST /assets/claims/{id}/review"

    request POST "/api/v1/assets/claims/${CLAIM_ID}/complete" "$ENGINEER_TOKEN"
    judge "claim/本人完成(修复前恒403)" 200 '"success"[[:space:]]*:[[:space:]]*true' "POST /assets/claims/{id}/complete"

    request GET "/api/v1/assets/${ASSET_ID}/claims?page=0&size=10" "$ADMIN_TOKEN"
    judge "claim/状态为completed" 200 '"status"[[:space:]]*:[[:space:]]*"completed"' "GET /assets/{id}/claims"
  else
    skip "claim/审批通过" "-" "未取到认领 ID"
    skip "claim/本人完成(修复前恒403)" "-" "未取到认领 ID"
    skip "claim/状态为completed" "-" "未取到认领 ID"
  fi

  # 第二条认领：工程师申请并审批通过，用于「非本人完成 403」与「管理层强制放弃 200」
  api_json POST "/api/v1/assets/${ASSET_ID}/claims" "$ENGINEER_TOKEN" \
    "{\"assetId\":\"${ASSET_ID}\",\"note\":\"冒烟认领-强制放弃\"}"
  CLAIM2_ID="$(jfirst id)"
  if [ -n "$CLAIM2_ID" ]; then
    api_json POST "/api/v1/assets/claims/${CLAIM2_ID}/review" "$ADMIN_TOKEN" \
      '{"approved":true,"reviewNote":"冒烟认领2审批通过"}'
    if [ "$LAST_STATUS" = "200" ]; then
      # 非认领人完成：管理员也必须是 403（"完成"是认领人确认交付）
      request POST "/api/v1/assets/claims/${CLAIM2_ID}/complete" "$ADMIN_TOKEN"
      judge "claim/非本人完成-403" 403 '' "管理员不是认领人"

      # 管理层强制放弃
      if [ -n "$LEADER_TOKEN" ]; then
        request POST "/api/v1/assets/claims/${CLAIM2_ID}/abandon?note=%E5%86%92%E7%83%9F%E5%BC%BA%E5%88%B6%E6%94%BE%E5%BC%83" "$LEADER_TOKEN"
        judge "claim/管理层强制放弃" 200 '"success"[[:space:]]*:[[:space:]]*true' "POST /assets/claims/{id}/abandon（组长）"
      else
        skip "claim/管理层强制放弃" "-" "缺少组长 token"
      fi
    else
      skip "claim/非本人完成-403" "$LAST_STATUS" "第二条认领审批未成功"
      skip "claim/管理层强制放弃" "$LAST_STATUS" "第二条认领审批未成功"
    fi
  else
    skip "claim/非本人完成-403" "-" "未取到第二条认领 ID"
    skip "claim/管理层强制放弃" "-" "未取到第二条认领 ID"
  fi

  # 未登录读「我的认领」：不得退化为全量查询（t39 连带修复）。
  # 本系统未配置 AuthenticationEntryPoint，Spring Security 对匿名请求统一回 403（与 /assets 等一致）；
  # 控制器内的 401 分支只在「已认证但解析不到用户」时触发，故此处接受 401/403，关键是不返回全量。
  # t45 后未认证一律 401（AuthenticationEntryPoint 已配置）
  request GET '/api/v1/assets/my/claims?page=0&size=10' ""
  judge "claim/未登录读我的认领-401" 401 '' "未认证必须 401（t45）"

  request GET '/api/v1/assets/my/claims?page=0&size=10' "invalid.token.value"
  judge "auth/无效token-401" 401 '' "无效 token 也是 401（t45）"
fi

# ----------------------------------------------------------------------------- 鉴权收紧（t40）
# 工程师：团队/环境/公告的写操作 403；对应的查询接口保持 200（避免前端白屏）。
if [ -n "$ENGINEER_TOKEN" ]; then
  api_json POST '/api/v1/teams' "$ENGINEER_TOKEN" \
    "{\"name\":\"冒烟越权团队 ${TAG}\",\"description\":\"应被拒绝\",\"leaderId\":\"${ADMIN_ID}\",\"createdBy\":\"${ADMIN_ID}\"}"
  judge "authz/工程师建团队-403" 403 '' "POST /teams 需 ADMIN/PROJECT_LEADER"

  request DELETE "/api/v1/teams/00000000-0000-0000-0000-0000000000aa" "$ENGINEER_TOKEN"
  judge "authz/工程师删团队-403" 403 '' "DELETE /teams/{id} 需角色门槛"

  api_json POST '/api/v1/environments' "$ENGINEER_TOKEN" \
    "{\"name\":\"冒烟越权环境 ${TAG}\",\"assetId\":\"${ASSET_ID}\",\"templateId\":\"00000000-0000-0000-0000-000000000001\",\"config\":\"{}\"}"
  judge "authz/工程师建环境-403" 403 '' "POST /environments 需管理层"

  api_json POST '/api/v1/notifications/announcements' "$ENGINEER_TOKEN" \
    "{\"title\":\"冒烟越权公告 ${TAG}\",\"content\":\"应被拒绝\",\"priority\":\"normal\"}"
  judge "authz/工程师发公告-403" 403 '' "POST /announcements 需 ADMIN/PROJECT_LEADER"

  request GET '/api/v1/teams?page=0&size=5' "$ENGINEER_TOKEN"
  judge "authz/工程师读团队列表-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "查询保持开放"

  request GET '/api/v1/environments?page=0&size=5' "$ENGINEER_TOKEN"
  judge "authz/工程师读环境列表-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "查询保持开放"

  request GET '/api/v1/notifications/announcements?page=0&size=5' "$ENGINEER_TOKEN"
  judge "authz/工程师读公告列表-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "查询保持开放"

  # ===== 用户域（t45）：管理视角收紧 + 轻量目录不泄漏 PII =====
  request GET '/api/v1/users?page=0&size=5' "$ENGINEER_TOKEN"
  judge "users/工程师列表-403" 403 '' "GET /users 需 ADMIN/PROJECT_LEADER"
  if [ -n "$ADMIN_ID" ]; then
    request GET "/api/v1/users/${ADMIN_ID}" "$ENGINEER_TOKEN"
    judge "users/工程师详情-403" 403 '' "GET /users/{id} 需管理层"
  else
    skip "users/工程师详情-403" "-" "未取到 admin id"
  fi
  request GET '/api/v1/users/check-username?username=admin' "$ENGINEER_TOKEN"
  judge "users/工程师check-username-403" 403 '' "check-username 需管理层"
  request GET '/api/v1/users/check-email?email=admin@example.com' "$ENGINEER_TOKEN"
  judge "users/工程师check-email-403" 403 '' "check-email 需管理层"

  request GET '/api/v1/users/directory?limit=5' "$ENGINEER_TOKEN"
  judge "users/工程师目录-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "人员下拉数据源（仅登录）"
  if grep -Eq '"email"|"phone"|"roles"|"passwordHash"|"status"|"lastLoginAt"' "$BODY"; then
    fail "users/目录无PII字段" "$LAST_STATUS" "目录响应含敏感字段 ｜ $(snippet)"
  else
    pass "users/目录无PII字段" "$LAST_STATUS" "仅 id/username/fullName"
  fi
  request GET '/api/v1/users/directory?keyword=admin&limit=3' "$ENGINEER_TOKEN"
  judge "users/目录keyword-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "keyword 只匹配 username/fullName"

  # 「我的信息」对所有角色开放：实际路由是 /auth/me 与 /users/me/permissions
  # （/users/me 不存在，会被 /users/{id} 当作 id 解析而返回 400，已在报告中说明）
  for pair in "admin:${ADMIN_TOKEN}" "engineer:${ENGINEER_TOKEN}" "leader:${LEADER_TOKEN}"; do
    who="${pair%%:*}"
    tk="${pair#*:}"
    if [ -n "$tk" ]; then
      request GET '/api/v1/auth/me' "$tk"
      judge "users/${who}-auth-me-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /auth/me 对登录用户开放"
      request GET '/api/v1/users/me/permissions' "$tk"
      judge "users/${who}-me-permissions-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /users/me/permissions 对登录用户开放"
    else
      skip "users/${who}-auth-me-200" "-" "缺少 ${who} token"
      skip "users/${who}-me-permissions-200" "-" "缺少 ${who} token"
    fi
  done
fi

# ----------------------------------------------------------------------------- 资产可见性不变量（t46）
# 列表里每个资产 id 的详情必须 200；列表外的资产详情必须 403（三账号各一遍）。
if [ -n "$ADMIN_TOKEN" ] && [ -n "$ENGINEER_TOKEN" ] && [ -n "$LEADER_TOKEN" ]; then
  ALL_IDS="$(mktemp)"
  request GET '/api/v1/assets?page=0&size=200' "$ADMIN_TOKEN"
  flatten | sed 's/"customers":\[[^]]*\]//g; s/"projects":\[[^]]*\]//g; s/"projectLinks":\[[^]]*\]//g' | grep -o '"id"[[:space:]]*:[[:space:]]*"[0-9a-f-]\{36\}"' | sed 's/.*"id"[[:space:]]*:[[:space:]]*"//; s/"$//' | sort -u >"$ALL_IDS"

  visibility_check() { # <name前缀> <token>
    local prefix="$1" tk="$2" list_ids bad seen=0 outside=""
    list_ids="$(mktemp)"
    request GET '/api/v1/assets?page=0&size=50' "$tk"
    flatten | sed 's/"customers":\[[^]]*\]//g; s/"projects":\[[^]]*\]//g; s/"projectLinks":\[[^]]*\]//g' | grep -o '"id"[[:space:]]*:[[:space:]]*"[0-9a-f-]\{36\}"' | sed 's/.*"id"[[:space:]]*:[[:space:]]*"//; s/"$//' | sort -u >"$list_ids"
    bad=0
    while IFS= read -r aid; do
      [ -z "$aid" ] && continue
      seen=$((seen + 1))
      [ "$seen" -gt 5 ] && break
      request GET "/api/v1/assets/${aid}" "$tk"
      # t147：列表 → 详情之间夹具可能已被软删/清掉（404），这不算可见性缺陷；只把 401/403/5xx 计为失败
      case "$LAST_STATUS" in
        200) ;;
        404) seen=$((seen - 1)); echo "    detail($aid)=404（已删除，跳过）" >&2 ;;
        *) bad=$((bad + 1)); echo "    detail($aid)=${LAST_STATUS}" >&2 ;;
      esac
    done <"$list_ids"
    if [ "$seen" -eq 0 ]; then
      pass "vis/${prefix}-列表空" "$LAST_STATUS" "该账号列表为空，逐条详情跳过"
    elif [ "$bad" -eq 0 ]; then
      pass "vis/${prefix}-列表内详情全200" "200" "抽查 ${seen} 个资产详情均 200"
    else
      fail "vis/${prefix}-列表内详情全200" "4xx" "${seen} 个里有 ${bad} 个详情非 200"
    fi
    outside="$(comm -23 "$ALL_IDS" "$list_ids" | head -1)"
    if [ -n "$outside" ]; then
      request GET "/api/v1/assets/${outside}" "$tk"
      judge "vis/${prefix}-列表外详情403" 403 '' "不可见资产详情必须 403"
    elif [ "$prefix" = "admin" ]; then
      pass "vis/${prefix}-列表外详情403" "-" "管理员可见全部资产，无列表外样本（合理开放）"
    else
      skip "vis/${prefix}-列表外详情403" "-" "该账号可见全部资产，无列表外样本"
    fi
    rm -f "$list_ids"
  }

  visibility_check "admin" "$ADMIN_TOKEN"
  visibility_check "engineer" "$ENGINEER_TOKEN"
  visibility_check "leader" "$LEADER_TOKEN"
  rm -f "$ALL_IDS"
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

  # t40 修复：不传 publishedAt 时服务端补 now（此前因 NOT NULL 直接 500）
  api_json POST '/api/v1/notifications/announcements' "$ADMIN_TOKEN" \
    "{\"title\":\"冒烟公告-无发布时间 ${TAG}\",\"content\":\"不传 publishedAt\",\"priority\":\"normal\"}"
  judge_ok "notify/公告-创建-不传publishedAt" '"id"' "服务端补 publishedAt=now（t40）"
  ANNOUNCEMENT_ID2="$(jfirst id)"
  [ -n "$ANNOUNCEMENT_ID2" ] && request DELETE "/api/v1/notifications/announcements/${ANNOUNCEMENT_ID2}" "$ADMIN_TOKEN" >/dev/null 2>&1
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
    # failedRows 是数组；t78 起响应还含 notes[]（同样是 {line,...}），因此按 reason 计数更准
    failed_cnt="$(flatten | grep -o '"reason":' | wc -l | tr -d ' ')"
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

  # t40 提示：snapshots 缺 name 会先 400（参数绑定早于方法级守卫），用于确认参数校验生效
  api_json POST "/api/v1/environments/${ENV_ID}/snapshots" "$ADMIN_TOKEN" '{"description":"冒烟无 name"}'
  judge "env/快照-缺name-400" 400 '' "POST /environments/{id}/snapshots（缺 name）"
else
  skip "env/部署-无Docker应503" "-" "环境创建未成功，跳过"
  skip "env/详情(含部署器信息)" "-" "环境创建未成功，跳过"
  skip "env/快照-缺name-400" "-" "环境创建未成功，跳过"
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

# ----------------------------------------------------------------------------- 第二批鉴权收口（t49/t50/t51/t55）
# 12 组写操作对工程师必须 403；对应查询仍 200（避免前端白屏）。
RAND1="00000000-0000-4000-8000-0000000aa54a"
RAND2="00000000-0000-4000-8000-0000000bb54b"
if [ -n "$ENGINEER_TOKEN" ] && [ -n "$ASSET_ID" ]; then
  # 资产域（t49）：建资产 / 版本 / 默认 / 最新 / 申请明细 / 标签删除 / 标签新增 / 分组写
  authz_asset_payload="{\"name\":\"冒烟越权资产 ${TAG}\",\"targetType\":\"Web应用\",\"targetSource\":\"客户委托\",\"priority\":\"P3\",\"status\":\"active\""
  [ -n "$TEAM_ID" ] && authz_asset_payload="${authz_asset_payload},\"teamId\":\"${TEAM_ID}\""
  [ -n "$PROJECT_ID" ] && authz_asset_payload="${authz_asset_payload},\"projectId\":\"${PROJECT_ID}\""
  authz_asset_payload="${authz_asset_payload}}"
  api_json POST '/api/v1/assets' "$ENGINEER_TOKEN" "$authz_asset_payload"
  judge "authz2/工程师建资产-403" 403 '' "POST /assets 需管理层"

  api_json POST "/api/v1/assets/${ASSET_ID}/versions" "$ENGINEER_TOKEN" '{"version":"v9.9","description":"越权"}'
  judge "authz2/工程师建资产版本-403" 403 '' "POST /assets/{id}/versions 需资产写权"

  request PUT "/api/v1/assets/${ASSET_ID}/versions/${RAND1}/default" "$ENGINEER_TOKEN"
  judge "authz2/工程师设默认版本-403" 403 '' "PUT /versions/{id}/default 需资产写权"

  request PUT "/api/v1/assets/${ASSET_ID}/versions/${RAND1}/latest" "$ENGINEER_TOKEN"
  judge "authz2/工程师设最新版本-403" 403 '' "PUT /versions/{id}/latest 需资产写权"

  request GET "/api/v1/assets/${ASSET_ID}/access-requests?page=0&size=5" "$ENGINEER_TOKEN"
  judge "authz2/工程师读他人申请明细-403" 403 '' "GET /assets/{id}/access-requests 限负责人/审批人/管理层"

  request DELETE "/api/v1/assets/${ASSET_ID}/tags/${RAND1}" "$ENGINEER_TOKEN"
  judge "authz2/工程师删资产标签-403" 403 '' "DELETE /assets/{id}/tags/{tagId} 需资产写权"

  request POST "/api/v1/assets/${ASSET_ID}/tags?tag=smoke_t54" "$ENGINEER_TOKEN"
  judge "authz2/工程师加资产标签-403" 403 '' "POST /assets/{id}/tags 需资产写权（t51 追加）"

  api_json POST '/api/v1/asset-groups' "$ENGINEER_TOKEN" \
    "{\"name\":\"冒烟越权分组 ${TAG}\",\"type\":\"custom\"}"
  judge "authz2/工程师建分组-403" 403 '' "POST /asset-groups 需管理层"
  # 分组样本：admin 现建一个，工程师改/删应 403，最后与管理端删除
  authz_group_payload="{\"name\":\"冒烟鉴权分组 ${TAG}\",\"type\":\"custom\"}"
  api_json POST '/api/v1/asset-groups' "$ADMIN_TOKEN" "$authz_group_payload"
  AUTHZ_GROUP_ID="$(jfirst id)"
  if [ -n "$AUTHZ_GROUP_ID" ]; then
    api_json PUT "/api/v1/asset-groups/${AUTHZ_GROUP_ID}" "$ENGINEER_TOKEN" \
      "{\"name\":\"冒烟分组改名 ${TAG}\",\"type\":\"custom\"}"
    judge "authz2/工程师改分组-403" 403 '' "PUT /asset-groups/{id} 需管理层"
    request DELETE "/api/v1/asset-groups/${AUTHZ_GROUP_ID}" "$ENGINEER_TOKEN"
    judge "authz2/工程师删分组-403" 403 '' "DELETE /asset-groups/{id} 需管理层"
    request DELETE "/api/v1/asset-groups/${AUTHZ_GROUP_ID}" "$ADMIN_TOKEN" >/dev/null 2>&1
  else
    skip "authz2/工程师改分组-403" "$LAST_STATUS" "分组样本创建失败"
    skip "authz2/工程师删分组-403" "$LAST_STATUS" "分组样本创建失败"
  fi

  # 协作域（t50）：删他人文章 / 漏洞通知群发 / 投递记录 / 建任务
  request DELETE "/api/v1/knowledge/${RAND1}" "$ENGINEER_TOKEN"
  judge "authz2/工程师删他人文章-403" 403 '' "DELETE /knowledge/{id} 限作者或管理层"

  request POST "/api/v1/notifications/vulnerability-discovered?vulnerabilityId=${RAND1}&recipientIds=${ADMIN_ID}" "$ENGINEER_TOKEN"
  judge "authz2/工程师发漏洞通知-403" 403 '' "POST /notifications/vulnerability-discovered 需管理层"

  request GET "/api/v1/notifications/${RAND1}/recipients" "$ENGINEER_TOKEN"
  judge "authz2/工程师读他人投递记录-403" 403 '' "GET /notifications/{id}/recipients 限接收人/管理层"

  api_json POST '/api/v1/tasks' "$ENGINEER_TOKEN" \
    "{\"title\":\"冒烟越权任务 ${TAG}\",\"description\":\"越权\",\"type\":\"audit\",\"priority\":\"medium\"}"
  judge "authz2/工程师建任务-403" 403 '' "POST /tasks 需管理层"

  # 组织/工具域（t51/t55）：他团队成员列表 / 工具写
  request GET "/api/v1/teams/${RAND1}/members" "$ENGINEER_TOKEN"
  judge "authz2/工程师读无关团队成员-403" 403 '' "GET /teams/{id}/members 限可见团队"
  if [ -n "$TOOL_ID" ]; then
    api_json PUT "/api/v1/tools/${TOOL_ID}" "$ENGINEER_TOKEN" \
      "{\"name\":\"冒烟越权工具 ${TAG}\",\"category\":\"scanner\",\"description\":\"越权\"}"
    judge "authz2/工程师改工具-403" 403 '' "PUT /tools/{id} 需管理层"
    api_json POST "/api/v1/tools/${TOOL_ID}/versions" "$ENGINEER_TOKEN" '{"versionNo":99}'
    judge "authz2/工程师传工具版本-403" 403 '' "POST /tools/{id}/versions 需管理层"
    api_json POST "/api/v1/tools/${TOOL_ID}/tags?tag=smoke_t54" "$ENGINEER_TOKEN" '{}'
    judge "authz2/工程师加工具标签-403" 403 '' "POST /tools/{id}/tags 需管理层（t55）"
    request DELETE "/api/v1/tools/${TOOL_ID}/tags?tag=smoke_t54" "$ENGINEER_TOKEN"
    judge "authz2/工程师删工具标签-403" 403 '' "DELETE /tools/{id}/tags 需管理层（t55）"
    [ -f "$TMPD/t54-tool.bin" ] || printf 't54-tool-payload' >"$TMPD/t54-tool.bin"
    request POST "/api/v1/tools/${TOOL_ID}/file" "$ENGINEER_TOKEN" -F "file=@$(pathify "$TMPD/t54-tool.bin")"
    judge "authz2/工程师上传工具包-403" 403 '' "POST /tools/{id}/file 需管理层（t55）"
  else
    skip "authz2/工程师改工具-403" "-" "无工具样本"
    skip "authz2/工程师传工具版本-403" "-" "无工具样本"
    skip "authz2/工程师加工具标签-403" "-" "无工具样本"
    skip "authz2/工程师删工具标签-403" "-" "无工具样本"
    skip "authz2/工程师上传工具包-403" "-" "无工具样本"
  fi

  # 查询不受影响（正向）
  for item in "/api/v1/assets?page=0&size=5:资产列表" "/api/v1/knowledge?page=0&size=5:知识库列表" \
    "/api/v1/notifications?page=0&size=5:通知列表" "/api/v1/tasks?page=0&size=5:任务列表" \
    "/api/v1/tools?page=0&size=5:工具列表"; do
    p="${item%%:*}"
    label="${item##*:}"
    request GET "$p" "$ENGINEER_TOKEN"
    judge "authz2/工程师${label}-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "查询保持开放"
  done

  # t55 顺带修复：对不存在的工具加标签应 404（此前静默 200）
  if [ -n "$ADMIN_TOKEN" ]; then
    api_json POST "/api/v1/tools/${RAND2}/tags?tag=smoke_t54" "$ADMIN_TOKEN" '{}'
    judge "tools/不存在工具加标签-404" 404 '' "不存在的工具返回 404（t55 修复）"
  fi
fi


# ----------------------------------------------------------------------------- 第三批：后端消息 i18n + 四类资源标签写权限（t60/t61）
RAND3="00000000-0000-4000-8000-0000000dd63a"
if [ -n "$ENGINEER_TOKEN" ] && [ -n "$ADMIN_TOKEN" ]; then
  # 1) 后端消息 i18n：Accept-Language: en → 英文；不带 → 中文
  # 404 用管理员取（工程师访问随机资产会先被数据级守卫拦成 403，拿不到 404 文案）
  request GET "/api/v1/assets/${RAND3}" "$ADMIN_TOKEN" -H 'Accept-Language: en'
  judge "i18n/404-英文消息" 404 '(not found|Not found|not exist|Not exist)' "Accept-Language: en"
  request GET "/api/v1/assets/${RAND3}" "$ADMIN_TOKEN"
  judge "i18n/404-中文消息" 404 '不存在' "默认中文"
  # 403 用工程师取（数据级守卫拒绝，文案应随语言切换）
  request GET "/api/v1/assets/${RAND3}" "$ENGINEER_TOKEN" -H 'Accept-Language: en'
  judge "i18n/403-越权访问-英文消息" 403 '(permission|Permission|forbidden|Forbidden|access)' "Accept-Language: en"
  request GET '/api/v1/users?page=0&size=1' "$ENGINEER_TOKEN" -H 'Accept-Language: en'
  judge "i18n/403-英文消息" 403 '(Forbidden|forbidden|permission|Permission|access|Access)' "Accept-Language: en"
  request GET '/api/v1/users?page=0&size=1' "$ENGINEER_TOKEN"
  judge "i18n/403-中文消息" 403 '(无权|权限|禁止|拒绝)' "默认中文"

  # 2) 知识库文章标签：作者本人 200 / 他人 403 / 管理员 200（t61）
  api_json POST '/api/v1/knowledge' "$ENGINEER_TOKEN" \
    "{\"title\":\"冒烟工程师文章 ${TAG}\",\"category\":\"技术\",\"summary\":\"冒烟\",\"content\":\"冒烟内容\"}"
  ENG_ARTICLE_ID="$(jfirst id)"
  api_json POST '/api/v1/knowledge' "$ADMIN_TOKEN" \
    "{\"title\":\"冒烟管理员文章 ${TAG}\",\"category\":\"技术\",\"summary\":\"冒烟\",\"content\":\"冒烟内容\"}"
  ADMIN_ARTICLE_ID="$(jfirst id)"

  if [ -n "$ADMIN_ARTICLE_ID" ]; then
    request POST "/api/v1/knowledge/${ADMIN_ARTICLE_ID}/tags?tag=t63_other" "$ENGINEER_TOKEN"
    judge "tags/工程师改他人文章标签-403" 403 '' "知识库标签：作者或管理层"
  else
    skip "tags/工程师改他人文章标签-403" "-" "文章样本创建失败"
  fi
  if [ -n "$ENG_ARTICLE_ID" ]; then
    request POST "/api/v1/knowledge/${ENG_ARTICLE_ID}/tags?tag=t63_mine" "$ENGINEER_TOKEN"
    judge_ok "tags/作者改自己文章标签-2xx" '' "知识库标签：作者本人"
    request POST "/api/v1/knowledge/${ENG_ARTICLE_ID}/tags?tag=t63_admin" "$ADMIN_TOKEN"
    judge_ok "tags/管理员改他人文章标签-2xx" '' "知识库标签：管理层"
    request DELETE "/api/v1/knowledge/${ENG_ARTICLE_ID}" "$ADMIN_TOKEN" >/dev/null 2>&1
  else
    skip "tags/作者改自己文章标签-2xx" "-" "文章样本创建失败"
    skip "tags/管理员改他人文章标签-2xx" "-" "文章样本创建失败"
  fi
  [ -n "$ADMIN_ARTICLE_ID" ] && request DELETE "/api/v1/knowledge/${ADMIN_ARTICLE_ID}" "$ADMIN_TOKEN" >/dev/null 2>&1

  # 3) 文档标签：作者本人 201 / 他人 403 / 管理员 201 / 文档不存在 404（t61 + t65）
  api_json POST '/api/v1/documents' "$ENGINEER_TOKEN" \
    "{\"title\":\"冒烟工程师文档 ${TAG}\",\"category\":\"技术\",\"summary\":\"冒烟\",\"content\":\"冒烟文档内容\"}"
  ENG_DOC_ID="$(jfirst id)"
  api_json POST '/api/v1/documents' "$ADMIN_TOKEN" \
    "{\"title\":\"冒烟管理员文档 ${TAG}\",\"category\":\"技术\",\"summary\":\"冒烟\",\"content\":\"冒烟文档内容\"}"
  ADMIN_DOC_ID="$(jfirst id)"

  if [ -n "$ADMIN_DOC_ID" ]; then
    request POST "/api/v1/documents/${ADMIN_DOC_ID}/tags?tag=t63_other" "$ENGINEER_TOKEN"
    judge "tags/工程师改他人文档标签-403" 403 '' "文档标签：作者或管理层"
    request DELETE "/api/v1/documents/${ADMIN_DOC_ID}" "$ADMIN_TOKEN" >/dev/null 2>&1
  else
    skip "tags/工程师改他人文档标签-403" "-" "文档样本创建失败"
  fi
  if [ -n "$ENG_DOC_ID" ]; then
    request POST "/api/v1/documents/${ENG_DOC_ID}/tags?tag=t63_mine" "$ENGINEER_TOKEN"
    judge "tags/作者改自己文档标签-201" 201 '"success"[[:space:]]*:[[:space:]]*true' "文档标签：作者本人（201）"
    request POST "/api/v1/documents/${ENG_DOC_ID}/tags?tag=t63_admin" "$ADMIN_TOKEN"
    judge "tags/管理员改他人文档标签-201" 201 '"success"[[:space:]]*:[[:space:]]*true' "文档标签：管理层（201）"
    request DELETE "/api/v1/documents/${ENG_DOC_ID}" "$ADMIN_TOKEN" >/dev/null 2>&1
  else
    skip "tags/作者改自己文档标签-201" "-" "文档样本创建失败"
    skip "tags/管理员改他人文档标签-201" "-" "文档样本创建失败"
  fi

  # 4) 四类资源口径一致性：工具/资产标签仍按管理层与资产写权（不回归）
  request GET "/api/v1/knowledge/${RAND3}/tags" "$ENGINEER_TOKEN"
  judge "tags/不存在文章标签查询-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "标签查询保持开放"
  request POST "/api/v1/knowledge/${RAND3}/tags?tag=x" "$ENGINEER_TOKEN"
  judge "tags/不存在文章加标签-404" 404 '' "父资源不存在 → 404（t61/t65）"
fi


# ----------------------------------------------------------------------------- 客户 / 项目 / 期次 / 挂靠（t75）
RAND4="00000000-0000-4000-8000-0000000ee75a"
if [ -n "$ADMIN_TOKEN" ] && [ -n "$CUST_ID" ] && [ -n "$PROJECT_ID" ] && [ -n "$ASSET_ID" ]; then
  # 1) 客户 stats 与库内一致（stats.assetCount 应为该客户可见资产数，admin 全量）
  request GET "/api/v1/customers/${CUST_ID}/summary" "$ADMIN_TOKEN"
  judge "cust/聚合-summary" 200 '"stats"' "GET /customers/{id}/summary（t76）"
  SUMMARY_ASSETS="$(jnum assetCount)"
  results_summary_ok=0
  [ -n "$SUMMARY_ASSETS" ] && [ "$SUMMARY_ASSETS" -ge 1 ] && results_summary_ok=1
  if [ "$results_summary_ok" = "1" ]; then
    pass "cust/聚合-assetCount≥1" "200" "stats.assetCount=${SUMMARY_ASSETS}"
  else
    fail "cust/聚合-assetCount≥1" "$LAST_STATUS" "summary 未返回 assetCount 或为 0 ｜ $(snippet)"
  fi

  # 2) 资产详情含 projectLinks，且指向本项目
  request GET "/api/v1/assets/${ASSET_ID}" "$ADMIN_TOKEN"
  judge "link/资产详情-projectLinks" 200 '"projectLinks"' "GET /assets/{id}"
  request GET "/api/v1/assets/${ASSET_ID}/project-links" "$ADMIN_TOKEN"
  judge "link/挂靠列表" 200 '"projectId"' "GET /assets/{id}/project-links"

  # 3) 建资产不带 projectId → 400（t69 强约束）
  no_proj_payload="{\"name\":\"冒烟无项目资产 ${TAG}\",\"targetType\":\"Web应用\",\"targetSource\":\"客户委托\",\"priority\":\"P3\",\"status\":\"active\""
  [ -n "$TEAM_ID" ] && no_proj_payload="${no_proj_payload},\"teamId\":\"${TEAM_ID}\""
  no_proj_payload="${no_proj_payload}}"
  api_json POST /api/v1/assets "$ADMIN_TOKEN" "$no_proj_payload"
  judge "link/建资产缺projectId-400" 400 '' "POST /assets 必须挂项目"

  # 4) projectId 不存在 → 404
  bad_proj_payload="{\"name\":\"冒烟坏项目资产 ${TAG}\",\"targetType\":\"Web应用\",\"targetSource\":\"客户委托\",\"priority\":\"P3\",\"status\":\"active\",\"projectId\":\"${RAND4}\""
  [ -n "$TEAM_ID" ] && bad_proj_payload="${bad_proj_payload},\"teamId\":\"${TEAM_ID}\""
  bad_proj_payload="${bad_proj_payload}}"
  api_json POST /api/v1/assets "$ADMIN_TOKEN" "$bad_proj_payload"
  judge "link/建资产projectId不存在-404" 404 '' "不存在的项目 → 404"

  # 5) 期次不属于该项目 → 400
  if [ -n "$PROJECT2_ID" ] && [ -n "$PHASE1_ID" ]; then
    cross_phase_payload="{\"name\":\"冒烟跨期次资产 ${TAG}\",\"targetType\":\"Web应用\",\"targetSource\":\"客户委托\",\"priority\":\"P3\",\"status\":\"active\",\"projectId\":\"${PROJECT2_ID}\",\"phaseId\":\"${PHASE1_ID}\""
    [ -n "$TEAM_ID" ] && cross_phase_payload="${cross_phase_payload},\"teamId\":\"${TEAM_ID}\""
    cross_phase_payload="${cross_phase_payload}}"
    api_json POST /api/v1/assets "$ADMIN_TOKEN" "$cross_phase_payload"
    judge "link/期次不属于项目-400" 400 '' "phaseId 与 projectId 不匹配 → 400"
  else
    skip "link/期次不属于项目-400" "-" "缺少第二个项目/期次样本"
  fi

  # 6) 跨客户挂靠 → 2xx；重复挂靠 → 409；解绑最后一条 → 409
  if [ -n "$PROJECT2_ID" ]; then
    api_json POST "/api/v1/assets/${ASSET_ID}/project-links" "$ADMIN_TOKEN" \
      "{\"projectId\":\"${PROJECT2_ID}\",\"linkType\":\"SHARED\",\"remark\":\"跨客户挂靠\"}"
    judge_ok "link/跨客户挂靠" '' "POST /assets/{id}/project-links（另一客户项目）"
    api_json POST "/api/v1/assets/${ASSET_ID}/project-links" "$ADMIN_TOKEN" \
      "{\"projectId\":\"${PROJECT2_ID}\",\"linkType\":\"SHARED\"}"
    judge "link/重复挂靠-409" 409 '' "同一项目重复挂靠 → 409"
    request GET "/api/v1/assets/${ASSET_ID}/project-links" "$ADMIN_TOKEN"
    LINK2_ID="$(flatten | grep -o "\"linkId\"[[:space:]]*:[[:space:]]*\"[0-9a-f-]\{36\}\"" | tail -1 | sed 's/.*"\([0-9a-f-]\{36\}\)"/\1/')"
    if [ -n "$LINK2_ID" ]; then
      request DELETE "/api/v1/assets/${ASSET_ID}/project-links/${LINK2_ID}" "$ADMIN_TOKEN"
      judge_ok "link/解绑一条" '' "DELETE /assets/{id}/project-links/{linkId}"
    else
      skip "link/解绑一条" "-" "未取到第二条挂靠 id"
    fi
    # 解绑最后一条（只剩主项目挂靠时）→ 409
    request GET "/api/v1/assets/${ASSET_ID}/project-links" "$ADMIN_TOKEN"
    LAST_LINK_ID="$(flatten | grep -o "\"linkId\"[[:space:]]*:[[:space:]]*\"[0-9a-f-]\{36\}\"" | head -1 | sed 's/.*"\([0-9a-f-]\{36\}\)"/\1/')"
    if [ -n "$LAST_LINK_ID" ]; then
      request DELETE "/api/v1/assets/${ASSET_ID}/project-links/${LAST_LINK_ID}" "$ADMIN_TOKEN"
      judge "link/解绑最后一条-409" 409 '' "资产至少保留一个项目挂靠"
    else
      skip "link/解绑最后一条-409" "-" "未取到挂靠 id"
    fi
  fi

  # 7) 删除有项目的客户 → 409；删除有挂靠的项目 → 409
  request DELETE "/api/v1/customers/${CUST_ID}" "$ADMIN_TOKEN"
  judge "cust/删除有项目客户-409" 409 '' "客户下仍有项目"
  request DELETE "/api/v1/projects/${PROJECT_ID}" "$ADMIN_TOKEN"
  judge "proj/删除有挂靠项目-409" 409 '' "项目下仍有资产挂靠"

  # 8) 工程师建客户/项目 → 403
  if [ -n "$ENGINEER_TOKEN" ]; then
    api_json POST '/api/v1/customers' "$ENGINEER_TOKEN" \
      "{\"code\":\"SMK-ENG-${TAG}\",\"name\":\"冒烟越权客户 ${TAG}\"}"
    judge "cust/工程师建客户-403" 403 '' "POST /customers 需 ADMIN/PROJECT_LEADER"
    api_json POST '/api/v1/projects' "$ENGINEER_TOKEN" \
      "{\"code\":\"SMK-ENG-PRJ-${TAG}\",\"name\":\"冒烟越权项目 ${TAG}\",\"customerId\":\"${CUST_ID}\",\"managerId\":\"${ADMIN_ID}\"}"
    judge "proj/工程师建项目-403" 403 '' "POST /projects 需 ADMIN/PROJECT_LEADER"
  fi

  # 9) 团队长改自己项目 200 / 他人项目 403（项目 managerId）
  if [ -n "$LEADER_TOKEN" ] && [ -n "$LEADER_ID" ]; then
    api_json POST '/api/v1/projects' "$ADMIN_TOKEN" \
      "{\"code\":\"SMK-LEAD-${TAG}\",\"name\":\"冒烟组长项目 ${TAG}\",\"customerId\":\"${CUST_ID}\",\"managerId\":\"${LEADER_ID}\"}"
    LEADER_PROJECT_ID="$(jfirst id)"
    if [ -n "$LEADER_PROJECT_ID" ]; then
      api_json PUT "/api/v1/projects/${LEADER_PROJECT_ID}" "$LEADER_TOKEN" \
        "{\"code\":\"SMK-LEAD-${TAG}\",\"name\":\"冒烟组长项目(改名) ${TAG}\",\"customerId\":\"${CUST_ID}\",\"managerId\":\"${LEADER_ID}\"}"
      judge "proj/团队长改自己项目-2xx" 200 '"success"[[:space:]]*:[[:space:]]*true' "managerId=自己"
      api_json PUT "/api/v1/projects/${PROJECT_ID}" "$LEADER_TOKEN" \
        "{\"code\":\"SMK-PRJ-${TAG}\",\"name\":\"冒烟越权改名 ${TAG}\",\"customerId\":\"${CUST_ID}\",\"managerId\":\"${ADMIN_ID}\"}"
      judge "proj/团队长改他人项目-403" 403 '' "managerId≠自己"
    else
      skip "proj/团队长改自己项目-2xx" "$LAST_STATUS" "组长项目创建失败"
      skip "proj/团队长改他人项目-403" "$LAST_STATUS" "组长项目创建失败"
    fi
  fi

  # 10) 项目资产清单：工程师只看到可见资产（admin 至少 1 条）
  request GET "/api/v1/projects/${PROJECT_ID}/assets?page=0&size=20" "$ADMIN_TOKEN"
  judge "proj/资产清单-admin" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /projects/{id}/assets"
  if [ -n "$ENGINEER_TOKEN" ]; then
    request GET "/api/v1/projects/${PROJECT_ID}/assets?page=0&size=20" "$ENGINEER_TOKEN"
    judge "proj/资产清单-工程师" 200 '"success"[[:space:]]*:[[:space:]]*true' "工程师按可见范围返回"
  fi

  # 11) 仪表盘分布卡片接口（t70）
  request GET '/api/v1/dashboard/customer-distribution?topN=5' "$ADMIN_TOKEN"
  judge "dash/客户分布" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /dashboard/customer-distribution"
  request GET '/api/v1/dashboard/project-distribution?topN=5' "$ADMIN_TOKEN"
  judge "dash/项目分布" 200 '"success"[[:space:]]*:[[:space:]]*true' "GET /dashboard/project-distribution"
fi


# ----------------------------------------------------------------------------- 新域必填兜底（t80/t81）
RAND5="00000000-0000-4000-8000-0000000ff81a"
if [ -n "$ADMIN_TOKEN" ] && [ -n "$CUST_ID" ]; then
  # 缺 managerId：t80 前会撞 projects.manager_id NOT NULL → 500；现为 400 且带字段名
  api_json POST '/api/v1/projects' "$ADMIN_TOKEN" \
    "{\"code\":\"SMK-NOMGR-${TAG}\",\"name\":\"冒烟缺经理项目 ${TAG}\",\"customerId\":\"${CUST_ID}\"}"
  judge "proj/new-缺managerId-400" 400 '"managerId"' "POST /projects 缺 managerId → 400 + 字段名（t80）"

  if [ -n "$PROJECT_ID" ]; then
    api_json PUT "/api/v1/projects/${PROJECT_ID}" "$ADMIN_TOKEN" \
      "{\"code\":\"SMK-PRJ-${TAG}\",\"name\":\"冒烟项目 ${TAG}\",\"customerId\":\"${CUST_ID}\"}"
    judge "proj/update-缺managerId-400" 400 '"managerId"' "PUT /projects/{id} 缺 managerId → 400（同 DTO）"
  else
    skip "proj/update-缺managerId-400" "-" "缺少项目样本"
  fi

  api_json POST '/api/v1/projects' "$ADMIN_TOKEN" \
    "{\"code\":\"SMK-BADMGR-${TAG}\",\"name\":\"冒烟坏经理项目 ${TAG}\",\"customerId\":\"${CUST_ID}\",\"managerId\":\"${RAND5}\"}"
  judge "proj/new-managerId不存在-404" 404 '' "经理不存在 → 404（存在性校验保留）"
fi


# ----------------------------------------------------------------------------- 第五批：聚合列表 + 客户/项目导入（t82/t85/t87）
if [ -n "$ADMIN_TOKEN" ] && [ -n "$CUST_ID" ]; then
  # 1) /projects/summary 聚合：一次请求带 phaseCount / assetCount
  request GET '/api/v1/projects/summary?page=0&size=10&sort=createdAt' "$ADMIN_TOKEN"
  judge "proj/summary-聚合" 200 '"phaseCount"' "GET /projects/summary（t82）"
  ADMIN_SUM="$(flatten)"
  judge "proj/summary-assetCount" 200 '"assetCount"' "每项含 assetCount"

  # 2) 口径对比：工程师 assetCount 收敛（≤ admin），phaseCount 不变（组织结构全量）
  if [ -n "$ENGINEER_TOKEN" ] && [ -n "$PROJECT_ID" ]; then
    ADM_PH="$(printf '%s' "$ADMIN_SUM" | grep -o '"phaseCount":[0-9]*' | awk -F':' '{s+=$2} END{print s+0}')"
    ADM_AS="$(printf '%s' "$ADMIN_SUM" | grep -o '"assetCount":[0-9]*' | awk -F':' '{s+=$2} END{print s+0}')"
    request GET '/api/v1/projects/summary?page=0&size=10&sort=createdAt' "$ENGINEER_TOKEN"
    ENG_SUM="$(flatten)"
    ENG_PH="$(printf '%s' "$ENG_SUM" | grep -o '"phaseCount":[0-9]*' | awk -F':' '{s+=$2} END{print s+0}')"
    ENG_AS="$(printf '%s' "$ENG_SUM" | grep -o '"assetCount":[0-9]*' | awk -F':' '{s+=$2} END{print s+0}')"
    if [ "$LAST_STATUS" = "200" ] && [ -n "$ADM_PH" ] && [ "$ADM_PH" -gt 0 ] && [ "$ADM_PH" = "$ENG_PH" ] && [ "$ENG_AS" -le "$ADM_AS" ]; then
      pass "proj/summary-工程师phaseCount不收敛" "200" "phaseCount 合计 admin=$ADM_PH engineer=$ENG_PH（全量）；assetCount 合计 admin=$ADM_AS engineer=$ENG_AS（收敛）"
    else
      fail "proj/summary-工程师phaseCount不收敛" "$LAST_STATUS" "phaseCount admin=${ADM_PH:-?}/engineer=${ENG_PH:-?}; assetCount admin=${ADM_AS:-?}/engineer=${ENG_AS:-?} ｜ $(snippet)"
    fi
    judge "proj/summary-工程师可见" 200 '"assetCount"' "工程师按可见范围返回 assetCount"
  fi

  # 3) 客户 CSV 导入：正常行成功 / 重复 code / ownerName 不存在 → failedRows
  {
    printf 'code,name,industry,level,ownerName,contactName,contactPhone,contactEmail,description\n'
    printf 'SMK-IMP-CUST-%s,Import Cust OK %s,Internet,A,admin,Contact,13800000000,imp@example.com,smoke\n' "$TAG" "$TAG"
    printf 'CUST-DEFAULT,Duplicate Code %s,Internet,B,,, ,,smoke\n' "$TAG"
    printf 'SMK-IMP-CUST2-%s,Import Cust BadOwner %s,Internet,B,nobody_xyz,,,,\n' "$TAG" "$TAG"
  } >"$TMPD/import-customers.csv"
  request POST '/api/v1/import/customers' "$ADMIN_TOKEN" \
    -F "file=@$(pathify "${TMPD}/import-customers.csv");filename=smoke-customers.csv;type=text/csv"
  if [ "$LAST_STATUS" = "200" ]; then
    c_ok="$(jnum successRows)"
    c_bad="$(flatten | grep -o '"reason":' | wc -l | tr -d ' ')"
    if [ "$c_ok" = "1" ] && [ "$c_bad" = "2" ]; then
      pass "import/客户-部分成功" 200 "successRows=1 failedRows=2（重复 code + ownerName 不存在）"
    else
      fail "import/客户-部分成功" 200 "期望 1/2，实际 ${c_ok:-?}/${c_bad:-?} ｜ $(snippet)"
    fi
    judge "import/客户-失败原因可读" 200 '"reason"' "failedRows 带 reason"
  else
    fail "import/客户-部分成功" "$LAST_STATUS" "期望 200 ｜ $(snippet)"
  fi

  # 4) 工程师导入客户 → 403（ADMIN/PROJECT_LEADER）
  if [ -n "$ENGINEER_TOKEN" ]; then
    request POST '/api/v1/import/customers' "$ENGINEER_TOKEN" \
      -F "file=@$(pathify "${TMPD}/import-customers.csv");filename=smoke-customers.csv;type=text/csv"
    judge "import/客户-工程师403" 403 '' "仅 ADMIN/PROJECT_LEADER"
  fi

  # 5) 项目 CSV 导入：正常行 / customerCode 不存在 / type 非法 / 日期格式错
  {
    printf 'code,name,customerCode,type,managerName,startDate,endDate,isPhased,contractNo,description\n'
    printf 'SMK-IMP-PRJ-%s,Import Proj OK %s,SMK-CUST-%s,PENTEST,admin,2026-01-01,2026-06-30,true,CN-%s,smoke\n' "$TAG" "$TAG" "$TAG" "$TAG"
    printf 'SMK-IMP-PRJ2-%s,No Customer %s,NOPE-CUST,PENTEST,admin,2026-01-01,2026-06-30,false,,\n' "$TAG" "$TAG"
    printf 'SMK-IMP-PRJ3-%s,Bad Type %s,SMK-CUST-%s,BADTYPE,admin,2026-01-01,2026-06-30,false,,\n' "$TAG" "$TAG" "$TAG"
    printf 'SMK-IMP-PRJ4-%s,Bad Date %s,SMK-CUST-%s,PENTEST,admin,2026/01/01,2026-06-30,false,,\n' "$TAG" "$TAG" "$TAG"
  } >"$TMPD/import-projects.csv"
  request POST '/api/v1/import/projects' "$ADMIN_TOKEN" \
    -F "file=@$(pathify "${TMPD}/import-projects.csv");filename=smoke-projects.csv;type=text/csv"
  if [ "$LAST_STATUS" = "200" ]; then
    p_ok="$(jnum successRows)"
    p_bad="$(flatten | grep -o '"reason":' | wc -l | tr -d ' ')"
    if [ "$p_ok" = "1" ] && [ "$p_bad" = "3" ]; then
      pass "import/项目-部分成功" 200 "successRows=1 failedRows=3（客户不存在 + type 非法 + 日期格式）"
    else
      fail "import/项目-部分成功" 200 "期望 1/3，实际 ${p_ok:-?}/${p_bad:-?} ｜ $(snippet)"
    fi
  else
    fail "import/项目-部分成功" "$LAST_STATUS" "期望 200 ｜ $(snippet)"
  fi

  # 6) 模板下载带 UTF-8 BOM（customers / projects）
  for tpl in customers projects; do
    request GET "/api/v1/import/templates/${tpl}" "$ADMIN_TOKEN"
    if [ "$LAST_STATUS" = "200" ] && [ "$(flatten | head -c 3 | od -An -tx1 | tr -d ' \n')" = "efbbbf" ]; then
      pass "import/模板-${tpl}-BOM" 200 "首字节 EF BB BF"
    else
      fail "import/模板-${tpl}-BOM" "$LAST_STATUS" "缺少 UTF-8 BOM ｜ $(snippet)"
    fi
  done
fi


# ----------------------------------------------------------------------------- 管理员模拟视角（t90/t92）
if [ -n "$ADMIN_TOKEN" ] && [ -n "$ENGINEER_ID" ]; then
  # 1) admin 发起模拟 → 200 且返回 token / target / actor
  api_json POST '/api/v1/admin/impersonate' "$ADMIN_TOKEN" \
    "{\"userId\":\"${ENGINEER_ID}\",\"reason\":\"冒烟验证 ${TAG}\"}"
  IMP_STATUS="$LAST_STATUS"
  IMP_TOKEN="$(flatten | sed -n 's/.*"token":"\([^"]*\)".*/\1/p' | head -1)"
  if [ "$IMP_STATUS" = "200" ] && [ -n "$IMP_TOKEN" ]; then
    pass "impersonate/发起" 200 "token 长度 ${#IMP_TOKEN}"
  else
    fail "impersonate/发起" "$IMP_STATUS" "未拿到模拟 token ｜ $(snippet)"
  fi
  judge "impersonate/返回target与actor" 200 '"actor"' "响应含 target/actor"

  # 2) 工程师发起 → 403；模拟管理员账号 → 409
  api_json POST '/api/v1/admin/impersonate' "$ENGINEER_TOKEN" \
    "{\"userId\":\"${ENGINEER_ID}\"}"
  judge "impersonate/工程师发起-403" 403 '' "仅 ADMIN"
  ADMIN_ID_FOR_IMP="$(curl -s -X POST "$BASE_URL/api/v1/auth/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin"}' | sed -n 's/.*"userId":"\([^"]*\)".*/\1/p' | head -1)"
  [ -z "$ADMIN_ID_FOR_IMP" ] && ADMIN_ID_FOR_IMP="$(request GET '/api/v1/auth/me' "$ADMIN_TOKEN" >/dev/null; jfirst id)"
  if [ -n "$ADMIN_ID_FOR_IMP" ]; then
    api_json POST '/api/v1/admin/impersonate' "$ADMIN_TOKEN" \
      "{\"userId\":\"${ADMIN_ID_FOR_IMP}\"}"
    judge "impersonate/模拟管理员-409" 409 '' "不能模拟管理员"
  else
    skip "impersonate/模拟管理员-409" "-" "未取到 admin userId"
  fi

  if [ -n "$IMP_TOKEN" ]; then
    # 3) 模拟 token 行为等同目标用户：自身权限 200；列表（需 ADMIN/PL）403；资产可读
    request GET '/api/v1/users/me/permissions' "$IMP_TOKEN"
    judge "impersonate/模拟态-me权限-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "行为等同目标用户"
    request GET '/api/v1/users?page=0&size=1' "$IMP_TOKEN"
    judge "impersonate/模拟态-用户列表按目标权限-403" 403 '' "目标权限决定，非守卫"
    request GET '/api/v1/assets?page=0&size=1' "$IMP_TOKEN"
    judge "impersonate/模拟态-资产列表-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "业务读权限保留"

    # 4) 禁止链式模拟 / 敏感写禁用
    api_json POST '/api/v1/admin/impersonate' "$IMP_TOKEN" \
      "{\"userId\":\"${ENGINEER_ID}\"}"
    judge "impersonate/链式模拟-403" 403 '' "模拟态禁止再发起"
    api_json PUT "/api/v1/users/${ENGINEER_ID}" "$IMP_TOKEN" \
      "{\"fullName\":\"越权改名 ${TAG}\"}"
    judge "impersonate/模拟态改用户-403" 403 '' "身份端点写禁用"

    # 5) 模拟期间业务写（工程师自身有权做的操作：提资产访问申请）→ 审计 details 含 impersonatedBy
    api_json POST '/api/v1/documents' "$IMP_TOKEN" \
      "{\"title\":\"冒烟模拟态文档 ${TAG}\",\"category\":\"技术\",\"summary\":\"模拟态\",\"content\":\"模拟态业务写\"}"
    IMP_WRITE_STATUS="$LAST_STATUS"
    IMP_DOC_ID="$(jfirst id)"
    if [ "$IMP_WRITE_STATUS" = "200" ] || [ "$IMP_WRITE_STATUS" = "201" ]; then
      pass "impersonate/模拟态业务写-2xx" "$IMP_WRITE_STATUS" "POST /documents（目标用户自身权限，审计归属目标用户）"
    else
      fail "impersonate/模拟态业务写-2xx" "$IMP_WRITE_STATUS" "期望 2xx ｜ $(snippet)"
    fi
    request GET '/api/v1/audit-logs?page=0&size=30' "$ADMIN_TOKEN"
    if flatten | grep -q 'impersonatedBy'; then
      pass "impersonate/业务写审计含impersonatedBy" 200 "审计 details 记录 impersonatedBy=<adminId>"
    else
      fail "impersonate/业务写审计含impersonatedBy" "$LAST_STATUS" "未找到 impersonatedBy ｜ $(snippet)"
    fi
    [ -n "$IMP_DOC_ID" ] && request DELETE "/api/v1/documents/${IMP_DOC_ID}" "$ADMIN_TOKEN" >/dev/null 2>&1

    # 6) 退出模拟 → 204
    request POST '/api/v1/admin/impersonate/stop' "$IMP_TOKEN"
    judge "impersonate/退出模拟-204" 204 '' "POST /admin/impersonate/stop"
  fi

  # 7) 审计记录（t93 起为专用事件 IMPERSONATE_START / IMPERSONATE_STOP，拦截器不再重复写）
  request GET '/api/v1/audit-logs?page=0&size=5&action=IMPERSONATE_START' "$ADMIN_TOKEN"
  if flatten | grep -q 'IMPERSONATE_START'; then
    pass "impersonate/审计-发起事件" 200 "action=IMPERSONATE_START（专用事件）"
  else
    fail "impersonate/审计-发起事件" "$LAST_STATUS" "未找到 IMPERSONATE_START ｜ $(snippet)"
  fi
  request GET '/api/v1/audit-logs?page=0&size=5&action=IMPERSONATE_STOP' "$ADMIN_TOKEN"
  if flatten | grep -q 'IMPERSONATE_STOP'; then
    pass "impersonate/审计-停止事件" 200 "action=IMPERSONATE_STOP（专用事件）"
  else
    fail "impersonate/审计-停止事件" "$LAST_STATUS" "未找到 IMPERSONATE_STOP ｜ $(snippet)"
  fi
fi


# ----------------------------------------------------------------------------- 审计语义补全与状态码统一（t93/t94）
if [ -n "$ADMIN_TOKEN" ] && [ -n "$ENGINEER_ID" ]; then
  START_BEFORE="$(request GET '/api/v1/audit-logs?page=0&size=1&action=IMPERSONATE_START' "$ADMIN_TOKEN" >/dev/null; jnum totalElements)"
  STOP_BEFORE="$(request GET '/api/v1/audit-logs?page=0&size=1&action=IMPERSONATE_STOP' "$ADMIN_TOKEN" >/dev/null; jnum totalElements)"

  CTRL_BEFORE="$(request GET '/api/v1/audit-logs?page=0&size=1&keyword=AdminImpersonationController' "$ADMIN_TOKEN" >/dev/null; jnum totalElements)"
  api_json POST '/api/v1/admin/impersonate' "$ADMIN_TOKEN" \
    "{\"userId\":\"${ENGINEER_ID}\",\"reason\":\"冒烟审计验证 ${TAG}\"}"
  AUD_IMP_TOKEN="$(flatten | sed -n 's/.*"token":"\([^"]*\)".*/\1/p' | head -1)"
  if [ -n "$AUD_IMP_TOKEN" ]; then
    # ① 发起审计：四要素齐全（actor / target / ttlSeconds / reason）
    request GET '/api/v1/audit-logs?page=0&size=5&action=IMPERSONATE_START' "$ADMIN_TOKEN"
    AUD_START_BODY="$(flatten)"
    if printf '%s' "$AUD_START_BODY" | grep -q 'actor=' && \
       printf '%s' "$AUD_START_BODY" | grep -q 'target=' && \
       printf '%s' "$AUD_START_BODY" | grep -q 'ttlSeconds=' && \
       printf '%s' "$AUD_START_BODY" | grep -q 'reason='; then
      pass "audit/发起审计四要素" 200 "details 含 actor= / target= / ttlSeconds= / reason="
    else
      fail "audit/发起审计四要素" "$LAST_STATUS" "四要素缺失 ｜ $(snippet)"
    fi

    # ② 一次模拟只 1 条 START（拦截器不再重复写）
    START_AFTER="$(jnum totalElements)"
    if [ -n "$START_BEFORE" ] && [ -n "$START_AFTER" ] && [ "$START_AFTER" -eq $((START_BEFORE + 1)) ]; then
      pass "audit/一次模拟仅1条START" 200 "before=$START_BEFORE after=$START_AFTER"
    else
      fail "audit/一次模拟仅1条START" "$LAST_STATUS" "before=${START_BEFORE:-?} after=${START_AFTER:-?} ｜ $(snippet)"
    fi

    # ③ 不存在拦截器重复行（AdminImpersonationController）
    CTRL_AFTER="$(request GET '/api/v1/audit-logs?page=0&size=1&keyword=AdminImpersonationController' "$ADMIN_TOKEN" >/dev/null; jnum totalElements)"
    if [ -n "$CTRL_BEFORE" ] && [ -n "$CTRL_AFTER" ] && [ "$CTRL_AFTER" = "$CTRL_BEFORE" ]; then
      pass "audit/无拦截器重复行" 200 "拦截器行数未增加（before=$CTRL_BEFORE after=$CTRL_AFTER；历史行来自 t90/t92 旧版本）"
    else
      fail "audit/无拦截器重复行" "$LAST_STATUS" "拦截器行数增加 before=${CTRL_BEFORE:-?} after=${CTRL_AFTER:-?} ｜ $(snippet)"
    fi

    # ④ 停止审计：STOP 条数 +1 且 details 含 actor/target
    request POST '/api/v1/admin/impersonate/stop' "$AUD_IMP_TOKEN"
    if [ "$LAST_STATUS" = "204" ]; then
      request GET '/api/v1/audit-logs?page=0&size=5&action=IMPERSONATE_STOP' "$ADMIN_TOKEN"
      AUD_STOP_BODY="$(flatten)"
      STOP_AFTER="$(jnum totalElements)"
      if printf '%s' "$AUD_STOP_BODY" | grep -q 'actor=' && \
         printf '%s' "$AUD_STOP_BODY" | grep -q 'target=' && \
         [ -n "$STOP_BEFORE" ] && [ "$STOP_AFTER" -eq $((STOP_BEFORE + 1)) ]; then
        pass "audit/停止审计" 200 "before=$STOP_BEFORE after=$STOP_AFTER，details 含 actor=/target="
      else
        fail "audit/停止审计" "$LAST_STATUS" "before=${STOP_BEFORE:-?} after=${STOP_AFTER:-?} ｜ $(snippet)"
      fi
    else
      fail "audit/停止审计" "$LAST_STATUS" "停止模拟失败 ｜ $(snippet)"
    fi
  fi

  # ⑤ 访问申请状态码：不存在 404 / 跨团队工程师 403（t93 前为 400 或 201）
  RAND6="00000000-0000-4000-8000-0000000aa94a"
  api_json POST "/api/v1/assets/${RAND6}/access-requests" "$ADMIN_TOKEN" "{\"reason\":\"冒烟不存在资产\"}"
  judge "access-request/资产不存在-404" 404 '' "资产不存在仍 404"
  if [ -n "$ENGINEER_TOKEN" ] && [ -n "$ASSET_ID" ]; then
    api_json POST "/api/v1/assets/${ASSET_ID}/access-requests" "$ENGINEER_TOKEN" \
      "{\"reason\":\"冒烟跨团队申请 ${TAG}\"}"
    judge "access-request/跨团队-403" 403 '' "非团队成员/非负责人（t93 前为 400）"
  fi
fi

# ----------------------------------------------------------------------------- 登录页主题偏好（t96/t100/t98）
PREF_PATH='/api/v1/users/me/preferences'
if [ -n "$ADMIN_TOKEN" ]; then
  # ① 未认证 → 401
  request GET "$PREF_PATH" ""
  judge "pref/未认证-401" 401 '' "GET /users/me/preferences 未带 token"

  # ② 归位 pencil 并回读（同时作为后续用例的基线）
  api_json PUT "$PREF_PATH" "$ADMIN_TOKEN" '{"loginTheme":"pencil"}'
  judge "pref/PUT-归位pencil-200" 200 '"loginTheme"[[:space:]]*:[[:space:]]*"pencil"' "PUT /users/me/preferences"
  request GET "$PREF_PATH" "$ADMIN_TOKEN"
  judge "pref/GET-默认pencil" 200 '"loginTheme"[[:space:]]*:[[:space:]]*"pencil"' "GET 回读默认值"

  # ③ 四套角色主题各一次：PUT 200 且 /auth/me 回显一致
  for theme in geometric owl pixel-eyes pencil; do
    api_json PUT "$PREF_PATH" "$ADMIN_TOKEN" "{\"loginTheme\":\"${theme}\"}"
    put_st="$LAST_STATUS"
    put_note="$(snippet)"
    request GET '/api/v1/auth/me' "$ADMIN_TOKEN"
    me_ok=no
    flatten | grep -Eq "\"loginTheme\"[[:space:]]*:[[:space:]]*\"${theme}\"" && me_ok=yes
    if [ "$put_st" = "200" ] && [ "$me_ok" = "yes" ]; then
      pass "pref/主题-${theme}" 200 "PUT 200 且 /auth/me 回显 ${theme}"
    else
      fail "pref/主题-${theme}" "$put_st" "PUT=${put_st} me回显=${me_ok} ｜ ${put_note}"
    fi
  done

  # ④ 抽象四套（t100 扩展）逐个 PUT → 200，并以最后一次回显收尾
  abs_ok=yes; abs_bad=""
  for theme in aurora mono-grid orbit big-type; do
    api_json PUT "$PREF_PATH" "$ADMIN_TOKEN" "{\"loginTheme\":\"${theme}\"}"
    [ "$LAST_STATUS" = "200" ] || { abs_ok=no; abs_bad="${abs_bad} ${theme}=${LAST_STATUS}"; }
  done
  request GET '/api/v1/auth/me' "$ADMIN_TOKEN"
  if [ "$abs_ok" = "yes" ] && flatten | grep -q '"loginTheme":"big-type"'; then
    pass "pref/抽象主题-4套" 200 "aurora / mono-grid / orbit / big-type 均 200 且回显 big-type"
  else
    fail "pref/抽象主题-4套" "$LAST_STATUS" "失败:${abs_bad:- none} ｜ $(snippet)"
  fi

  # ⑤ 非法值 → 400（中文 + 英文各一条，均需带完整支持列表）
  api_json PUT "$PREF_PATH" "$ADMIN_TOKEN" '{"loginTheme":"mono_grid"}'
  judge "pref/非法值-中文400" 400 '不支持的登录页主题' "下划线拼写非法；消息含支持列表"
  printf '%s' '{"loginTheme":"monoGrid"}' >"$TMPD/payload.json"
  request PUT "$PREF_PATH" "$ADMIN_TOKEN" -H 'Content-Type: application/json; charset=utf-8' \
    -H 'Accept-Language: en' --data-binary "@$(pathify "$TMPD/payload.json")"
  judge "pref/非法值-英文400" 400 'Unsupported login page theme' "Accept-Language: en"

  # ⑥ 只能改自己：请求体带他人 userId 被忽略（工程师视角改自己，管理员偏好不受影响）
  if [ -n "$ENGINEER_TOKEN" ]; then
    request GET '/api/v1/auth/me' "$ADMIN_TOKEN"
    admin_id_now="$(jfirst id)"
    api_json PUT "$PREF_PATH" "$ENGINEER_TOKEN" \
      "{\"loginTheme\":\"owl\",\"userId\":\"${admin_id_now}\"}"
    judge "pref/只能改自己-PUT200" 200 '"loginTheme"[[:space:]]*:[[:space:]]*"owl"' "带 userId 被忽略"
    request GET "$PREF_PATH" "$ADMIN_TOKEN"
    judge "pref/只能改自己-管理员不受影响" 200 '"loginTheme"[[:space:]]*:[[:space:]]*"big-type"' "管理员仍为 big-type"
    # 归位 pencil，保持环境干净
    api_json PUT "$PREF_PATH" "$ADMIN_TOKEN" '{"loginTheme":"pencil"}' >/dev/null
  fi

  # ⑦ 登录响应携带 loginTheme
  api_json POST '/api/v1/auth/login' "" '{"username":"admin","password":"admin"}'
  judge "pref/登录响应含loginTheme" 200 '"loginTheme"[[:space:]]*:[[:space:]]*"pencil"' "POST /auth/login 自带主题，前端无需再查"

  # ⑧ 审计单条：一次 PUT 只写一条 UPDATE_LOGIN_THEME，且拦截器不再重复写
  before_theme="$(request GET '/api/v1/audit-logs?page=0&size=1&action=UPDATE_LOGIN_THEME' "$ADMIN_TOKEN" >/dev/null; jnum totalElements)"
  before_ctrl="$(request GET '/api/v1/audit-logs?page=0&size=1&keyword=updateMyPreferences' "$ADMIN_TOKEN" >/dev/null; jnum totalElements)"
  api_json PUT "$PREF_PATH" "$ADMIN_TOKEN" '{"loginTheme":"owl"}'
  after_theme="$(request GET '/api/v1/audit-logs?page=0&size=1&action=UPDATE_LOGIN_THEME' "$ADMIN_TOKEN" >/dev/null; jnum totalElements)"
  after_ctrl="$(request GET '/api/v1/audit-logs?page=0&size=1&keyword=updateMyPreferences' "$ADMIN_TOKEN" >/dev/null; jnum totalElements)"
  if [ -n "$before_theme" ] && [ "$after_theme" -eq $((before_theme + 1)) ] && [ "$after_ctrl" = "$before_ctrl" ]; then
    pass "pref/审计单条" 200 "UPDATE_LOGIN_THEME $before_theme→$after_theme（+1），拦截器行 ${before_ctrl:-0}→${after_ctrl:-0}（+0）"
  else
    fail "pref/审计单条" "$LAST_STATUS" "theme ${before_theme:-?}→${after_theme:-?} ctrl ${before_ctrl:-?}→${after_ctrl:-?} ｜ $(snippet)"
  fi
  # 收尾归位 pencil（与 t96/t97/t100 基线一致）
  api_json PUT "$PREF_PATH" "$ADMIN_TOKEN" '{"loginTheme":"pencil"}' >/dev/null
fi

# ----------------------------------------------------------------------------- 访问申请：同团队 201 / 重复 pending 409（t93/t98）
if [ -n "$ADMIN_TOKEN" ] && [ -n "$ENGINEER_TOKEN" ] && [ -n "$ENGINEER_ID" ] && [ -n "$ASSET_ID" ] && [ -n "$TEAM_ID" ]; then
  request POST "/api/v1/teams/${TEAM_ID}/members?userId=${ENGINEER_ID}&role=member" "$ADMIN_TOKEN"
  add_member_st="$LAST_STATUS"
  if [ "$add_member_st" = "200" ] || [ "$add_member_st" = "201" ]; then
    api_json POST "/api/v1/assets/${ASSET_ID}/access-requests" "$ENGINEER_TOKEN" \
      "{\"canViewDocuments\":true,\"canViewEnvironments\":false,\"canViewFiles\":false,\"requestNote\":\"冒烟同团队申请 ${TAG}\"}"
    judge "access-request/同团队-201" 201 '' "团队成员可申请（t93 前会被 400 挡掉）"
    same_team_req_id="$(jfirst id)"

    api_json POST "/api/v1/assets/${ASSET_ID}/access-requests" "$ENGINEER_TOKEN" \
      "{\"requestNote\":\"冒烟重复申请 ${TAG}\"}"
    judge "access-request/重复pending-409" 409 '' "同一人对同一资产已有 pending"

    # 收尾：撤销该申请（驳回）并移出团队，避免影响后续/环境
    [ -n "$same_team_req_id" ] && api_json POST "/api/v1/assets/access-requests/${same_team_req_id}/review" "$ADMIN_TOKEN" \
      '{"approved":false,"reviewNote":"冒烟清理：同团队申请"}' >/dev/null
    request DELETE "/api/v1/teams/${TEAM_ID}/members/${ENGINEER_ID}" "$ADMIN_TOKEN" >/dev/null
  else
    skip "access-request/同团队-201" "$add_member_st" "未能把工程师加入团队夹具（$(snippet)）"
    skip "access-request/重复pending-409" "-" "依赖上一条"
  fi
fi

# ----------------------------------------------------------------------------- 账号体系：注册 / 审批 / 找回密码 / SSO（t102/t103/t104/t107）
RUN_SEQ="$(date +%s)"
SUF="${RUN_SEQ: -7}"
PH_A="139${SUF}1"; PH_B="139${SUF}2"; PH_C="139${SUF}3"; PH_D="139${SUF}4"; PH_E="139${SUF}5"; PH_F="139${SUF}6"
SMK_USER="smk_reg_${SUF}"
SMK_USER2="smk_rej_${SUF}"
REG_PASS="RegSmoke1!"
REG_NEWPASS="RegSmoke2@"
REG_ID=""
REG_ID2=""
DEV_CODE=""

# ---- 注册：校验与限流 ----
api_json POST '/api/v1/auth/register/send-code' "" \
  "{\"channel\":\"sms\",\"target\":\"12345\",\"purpose\":\"REGISTER\"}"
judge "reg/手机号非法-400" 400 '' "目标手机号格式非法"

api_json POST '/api/v1/auth/register/send-code' "" \
  "{\"channel\":\"sms\",\"target\":\"${PH_A}\",\"purpose\":\"REGISTER\"}"
judge "reg/发码-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "POST /auth/register/send-code"
DEV_CODE="$(jfirst devCode)"

api_json POST '/api/v1/auth/register/send-code' "" \
  "{\"channel\":\"sms\",\"target\":\"${PH_A}\",\"purpose\":\"REGISTER\"}"
judge "reg/重复发码-429" 429 '' "同一 target 冷却期内再次发码"

api_json POST '/api/v1/auth/register' "" \
  "{\"username\":\"smk_nocode_${SUF}\",\"password\":\"${REG_PASS}\",\"phone\":\"${PH_B}\",\"code\":\"000000\"}"
judge "reg/未发码直接注册-400" 400 '' "未发码即提交"

# 邮箱填了但没验邮箱码 → 400
api_json POST '/api/v1/auth/register/send-code' "" \
  "{\"channel\":\"sms\",\"target\":\"${PH_C}\",\"purpose\":\"REGISTER\"}"
code_c="$(jfirst devCode)"
api_json POST '/api/v1/auth/register' "" \
  "{\"username\":\"smk_mail_${SUF}\",\"password\":\"${REG_PASS}\",\"phone\":\"${PH_C}\",\"code\":\"${code_c:-000000}\",\"email\":\"smk_${SUF}@example.com\"}"
judge "reg/邮箱未验-400" 400 '' "填了 email 但缺 emailCode"

# 错误验证码 → 400
api_json POST '/api/v1/auth/register/send-code' "" \
  "{\"channel\":\"sms\",\"target\":\"${PH_D}\",\"purpose\":\"REGISTER\"}"
api_json POST '/api/v1/auth/register' "" \
  "{\"username\":\"smk_badcode_${SUF}\",\"password\":\"${REG_PASS}\",\"phone\":\"${PH_D}\",\"code\":\"999999\"}"
judge "reg/错误验证码-400" 400 '' "验证码不匹配"

# ---- 注册成功 → 待审批不可登录 ----
if [ -z "$DEV_CODE" ]; then
  skip "reg/注册成功-201" "-" "后端未开启 dev-mode（无 devCode），无法在冒烟中完成注册链路"
  skip "reg/待审批登录-403" "-" "依赖上一条"
  skip "reg/重复用户名-409" "-" "依赖上一条"
else
  api_json POST '/api/v1/auth/register/send-code' "" \
    "{\"channel\":\"sms\",\"target\":\"${PH_E}\",\"purpose\":\"REGISTER\"}"
  E_CODE="$(jfirst devCode)"
  api_json POST '/api/v1/auth/register' "" \
    "{\"username\":\"${SMK_USER}\",\"password\":\"${REG_PASS}\",\"phone\":\"${PH_E}\",\"code\":\"${E_CODE:-000000}\"}"
  judge "reg/注册成功-201" 201 '"success"[[:space:]]*:[[:space:]]*true' "POST /auth/register（pending_approval）"
  REG_ID="$(jfirst id)"

  api_json POST '/api/v1/auth/login' "" "{\"username\":\"${SMK_USER}\",\"password\":\"${REG_PASS}\"}"
  judge "reg/待审批登录-403" 403 '' "pending_approval 不可登录"

  api_json POST '/api/v1/auth/register/send-code' "" \
    "{\"channel\":\"sms\",\"target\":\"${PH_F}\",\"purpose\":\"REGISTER\"}"
  code_f="$(jfirst devCode)"
  api_json POST '/api/v1/auth/register' "" \
    "{\"username\":\"${SMK_USER}\",\"password\":\"${REG_PASS}\",\"phone\":\"${PH_F}\",\"code\":\"${code_f:-000000}\"}"
  judge "reg/重复用户名-409" 409 '' "用户名已被占位用户占用"
fi

# ---- 审批 ----
request GET '/api/v1/admin/registration-requests?status=PENDING&page=0&size=20' "${ENGINEER_TOKEN:-}"
judge "reg/非管理员列表-403" 403 '' "仅 ADMIN/PROJECT_LEADER"

if [ -n "$REG_ID" ]; then
  request GET '/api/v1/admin/registration-requests?status=PENDING&page=0&size=50' "$ADMIN_TOKEN"
  if flatten | grep -q "${SMK_USER}"; then
    pass "reg/管理员列表含申请" 200 "列表命中 ${SMK_USER}"
  else
    fail "reg/管理员列表含申请" "$LAST_STATUS" "未命中 ${SMK_USER} ｜ $(snippet)"
  fi

  api_json POST "/api/v1/admin/registration-requests/${REG_ID}/approve" "$ADMIN_TOKEN" \
    '{"roles":["vulnerability_engineer"]}'
  judge "reg/审批通过-200" 200 '"loginEnabled"[[:space:]]*:[[:space:]]*true' "approve → 账号激活"

  api_json POST '/api/v1/auth/login' "" "{\"username\":\"${SMK_USER}\",\"password\":\"${REG_PASS}\"}"
  judge "reg/审批后登录-200" 200 '"token"' "审批后可登录"
else
  skip "reg/管理员列表含申请" "-" "无注册申请夹具"
  skip "reg/审批通过-200" "-" "无注册申请夹具"
  skip "reg/审批后登录-200" "-" "无注册申请夹具"
fi

# ---- 拒绝 -> 登录 403（t111 拒绝后释放唯一键，可复用同名）----
if [ -n "$DEV_CODE" ]; then
  PH_G="139${SUF}7"
  api_json POST '/api/v1/auth/register/send-code' "" \
    "{\"channel\":\"sms\",\"target\":\"${PH_G}\",\"purpose\":\"REGISTER\"}"
  code_g="$(jfirst devCode)"
  api_json POST '/api/v1/auth/register' "" \
    "{\"username\":\"${SMK_USER2}\",\"password\":\"${REG_PASS}\",\"phone\":\"${PH_G}\",\"code\":\"${code_g:-000000}\"}"
  REG_ID2="$(jfirst id)"
  if [ -n "$REG_ID2" ]; then
    api_json POST "/api/v1/admin/registration-requests/${REG_ID2}/reject" "$ADMIN_TOKEN" '{"note":"冒烟拒绝"}'
    judge "reg/拒绝-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "POST /{id}/reject"
    api_json POST '/api/v1/auth/login' "" "{\"username\":\"${SMK_USER2}\",\"password\":\"${REG_PASS}\"}"
    if [ "$LAST_STATUS" = "403" ]; then
      pass "reg/拒绝后不可登录" 403 "rejected 状态 → 403（占位用户保留）"
    elif [ "$LAST_STATUS" = "401" ]; then
      pass "reg/拒绝后不可登录" 401 "t111 拒绝即删占位用户 → 401 用户名或密码错误（同样不可登录）"
    else
      fail "reg/拒绝后不可登录" "$LAST_STATUS" "期望 401/403 ｜ $(snippet)"
    fi
  else
    skip "reg/拒绝-200" "$LAST_STATUS" "第二个注册未成功 ｜ $(snippet)"
    skip "reg/拒绝后不可登录" "-" "依赖上一条"
  fi
else
  skip "reg/拒绝-200" "-" "后端未开启 dev-mode"
  skip "reg/拒绝后不可登录" "-" "依赖上一条"
fi

# ---- 忘记密码（用已审批账号的手机号）----
if [ -n "$REG_ID" ]; then
  api_json POST '/api/v1/auth/password-reset/send-code' "" "{\"username\":\"${SMK_USER}\",\"channel\":\"sms\",\"target\":\"${PH_E}\"}"
  judge "reset/发码-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "username + target 匹配（t117 双因子）"
  RST_CODE="$(jfirst devCode)"

  # t117：用户名存在但 target 属于别人 → 200 且不发码（dev-mode 下无 devCode 即证明未发送）
  api_json POST '/api/v1/auth/password-reset/send-code' "" "{\"username\":\"${SMK_USER}\",\"channel\":\"sms\",\"target\":\"13900000000\"}"
  if [ "$LAST_STATUS" = "200" ] && ! flatten | grep -q 'devCode'; then
    pass "reset/双因子不匹配-200且不发码" 200 "username 与 target 不属于同一账号 → 防枚举"
  else
    fail "reset/双因子不匹配-200且不发码" "$LAST_STATUS" "期望 200 且无 devCode ｜ $(snippet)"
  fi

  api_json POST '/api/v1/auth/password-reset/send-code' "" "{\"username\":\"${SMK_USER}\",\"channel\":\"sms\",\"target\":\"${PH_E}\"}"
  judge "reset/重复发码-429" 429 '' "同一 target 冷却期内再次发码"

  api_json POST '/api/v1/auth/password-reset/confirm' "" \
    "{\"username\":\"${SMK_USER}\",\"target\":\"${PH_E}\",\"code\":\"000000\",\"newPassword\":\"${REG_NEWPASS}\"}"
  judge "reset/错误码-400" 400 '' "验证码不匹配"

  api_json POST '/api/v1/auth/password-reset/confirm' "" \
    "{\"username\":\"someone_else\",\"target\":\"${PH_E}\",\"code\":\"${RST_CODE:-000000}\",\"newPassword\":\"${REG_NEWPASS}\"}"
  judge "reset/confirm双因子不匹配-400" 400 '' "username 与 target 不一致 → 400 且密码不变"

  if [ -n "$RST_CODE" ]; then
    api_json POST '/api/v1/auth/password-reset/confirm' "" \
      "{\"username\":\"${SMK_USER}\",\"target\":\"${PH_E}\",\"code\":\"${RST_CODE}\",\"newPassword\":\"${REG_NEWPASS}\"}"
    judge "reset/重置成功-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "验证码 + 新密码"

    api_json POST '/api/v1/auth/login' "" "{\"username\":\"${SMK_USER}\",\"password\":\"${REG_PASS}\"}"
    judge "reset/旧密码-401" 401 '' "旧密码失效"

    api_json POST '/api/v1/auth/login' "" "{\"username\":\"${SMK_USER}\",\"password\":\"${REG_NEWPASS}\"}"
    judge "reset/新密码-200" 200 '"token"' "新密码可登录"
  else
    skip "reset/重置成功-200" "-" "未取到 devCode"
    skip "reset/旧密码-401" "-" "依赖上一条"
    skip "reset/新密码-200" "-" "依赖上一条"
  fi

  api_json POST '/api/v1/auth/password-reset/send-code' "" \
    "{\"username\":\"no_such_user\",\"channel\":\"sms\",\"target\":\"13900000000\"}"
  if [ "$LAST_STATUS" = "200" ] && ! flatten | grep -q 'devCode'; then
    pass "reset/未知账号-200-无devCode" 200 "防枚举：不存在账号也 200 且不发码"
  else
    fail "reset/未知账号-200-无devCode" "$LAST_STATUS" "期望 200 且无 devCode ｜ $(snippet)"
  fi
else
  skip "reset/发码-200" "-" "无已审批账号夹具"
  skip "reset/重复发码-429" "-" "依赖上一条"
  skip "reset/错误码-400" "-" "依赖上一条"
  skip "reset/重置成功-200" "-" "依赖上一条"
  skip "reset/旧密码-401" "-" "依赖上一条"
  skip "reset/新密码-200" "-" "依赖上一条"
  skip "reset/未知账号-200-无devCode" "-" "依赖上一条"
fi

# ---- SSO（默认全关闭；未配置凭据时不显示、authorize-url 503）----
# ---- SSO（t116 新口径：启用即展示 + configured 标记；凭据 Web 可配）----
request GET '/api/v1/auth/sso/providers' ""
judge "sso/providers-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "公开端点，未登录可读"

api_json PUT '/api/v1/admin/sso/providers/github' "${ENGINEER_TOKEN:-}" \
  '{"enabled":true}'
judge "sso/非管理员配置-403" 403 '' "仅 ADMIN（t116 PUT 配置面）"

api_json PUT '/api/v1/admin/sso/providers/github' "$ADMIN_TOKEN" \
  '{"enabled":true,"clientId":"smoke-client","clientSecret":"smoke-secret-123","authorizeUrl":"https://github.com/login/oauth/authorize","tokenUrl":"https://github.com/login/oauth/access_token","userinfoUrl":"https://api.github.com/user"}'
judge "sso/管理员配置凭据-200" 200 '"success"[[:space:]]*:[[:space:]]*true' "PUT clientId+secret+URLs"

request GET '/api/v1/admin/sso/providers' "$ADMIN_TOKEN"
if [ "$LAST_STATUS" = "200" ] && ! flatten | grep -q 'smoke-secret-123'; then
  pass "sso/密钥不回显明文" 200 "管理端响应仅掩码，无明文 secret"
else
  fail "sso/密钥不回显明文" "$LAST_STATUS" "响应疑似含明文密钥 ｜ $(snippet)"
fi

request GET '/api/v1/auth/sso/providers' ""
if flatten | grep -q '"code":"github"' && flatten | grep -q '"configured":true'; then
  pass "sso/启用且已配置-公开列表含configured=true" 200 "t116：启用即展示"
else
  fail "sso/启用且已配置-公开列表含configured=true" "$LAST_STATUS" "未出现 configured=true ｜ $(snippet)"
fi

# 空串保存不清空密钥
api_json PUT '/api/v1/admin/sso/providers/github' "$ADMIN_TOKEN" '{"clientSecret":""}'
st_keep="$LAST_STATUS"
request GET '/api/v1/auth/sso/providers' ""
if [ "$st_keep" = "200" ] && flatten | grep -q '"configured":true'; then
  pass "sso/空串保存不清空密钥" 200 "留空 = 保持原值（configured 仍 true）"
else
  fail "sso/空串保存不清空密钥" "$st_keep" "configured 变 false ｜ $(snippet)"
fi

# 只改 scopes 保存 → 密钥仍在
api_json PUT '/api/v1/admin/sso/providers/github' "$ADMIN_TOKEN" '{"scopes":"read:user,user:email"}'
request GET '/api/v1/auth/sso/providers' ""
if flatten | grep -q '"configured":true'; then
  pass "sso/只改scopes不丢密钥" 200 "部分更新语义正确"
else
  fail "sso/只改scopes不丢密钥" "$LAST_STATUS" "configured 变 false ｜ $(snippet)"
fi

# 清除密钥 → configured=false，但仍展示（enabled）
api_json PUT '/api/v1/admin/sso/providers/github' "$ADMIN_TOKEN" '{"clearClientSecret":true}'
request GET '/api/v1/auth/sso/providers' ""
if flatten | grep -q '"code":"github"' && flatten | grep -q '"configured":false'; then
  pass "sso/清除密钥后仍展示且configured=false" 200 "启用即展示（t116 口径）"
else
  fail "sso/清除密钥后仍展示且configured=false" "$LAST_STATUS" "未命中 ｜ $(snippet)"
fi

# enabled=false → 公开列表不再返回
api_json PUT '/api/v1/admin/sso/providers/github' "$ADMIN_TOKEN" '{"enabled":false}'
request GET '/api/v1/auth/sso/providers' ""
if flatten | grep -q '"code":"github"'; then
  fail "sso/关闭后公开列表不返回" "$LAST_STATUS" "已关闭仍返回 ｜ $(snippet)"
else
  pass "sso/关闭后公开列表不返回" 200 "enabled=false 即不展示"
fi
api_json PUT '/api/v1/admin/sso/providers/github' "$ADMIN_TOKEN" '{"enabled":false,"clearClientSecret":true}' >/dev/null
# ----------------------------------------------------------------------------- 角色授予边界（t115，防自助提权）
# 非 ADMIN 不得把 admin 等管理类角色授予他人；ADMIN 不受限。
PL_TOKEN=""
if [ -n "$ADMIN_TOKEN" ] && [ "$SMOKE_PROVISION" = "1" ]; then
  PL_TOKEN="$(ensure_account "smoke_pl" "project_leader" "冒烟-项目经理")" || true
fi
if [ -z "$PL_TOKEN" ]; then
  skip "rbac/PL授予admin-403" "-" "未取到 project_leader 账号"
  skip "rbac/ADMIN授予admin-200" "-" "依赖上一条"
else
  # 造一个待审批申请（dev-mode 才能拿码；否则退化为直接构造请求体走 403 判定）
  ROLE_PH="139${SUF}8"
  api_json POST '/api/v1/auth/register/send-code' "" \
    "{\"channel\":\"sms\",\"target\":\"${ROLE_PH}\",\"purpose\":\"REGISTER\"}"
  ROLE_CODE="$(jfirst devCode)"
  api_json POST '/api/v1/auth/register' "" \
    "{\"username\":\"smk_role_${SUF}\",\"password\":\"${REG_PASS}\",\"phone\":\"${ROLE_PH}\",\"code\":\"${ROLE_CODE:-000000}\"}"
  ROLE_REQ_ID="$(jfirst id)"

  if [ -n "$ROLE_REQ_ID" ]; then
    api_json POST "/api/v1/admin/registration-requests/${ROLE_REQ_ID}/approve" "$PL_TOKEN" '{"roles":["admin"]}'
    judge "rbac/PL授予admin-403" 403 '' "PROJECT_LEADER 不能把 admin 授出去（t115）"

    api_json POST "/api/v1/admin/registration-requests/${ROLE_REQ_ID}/approve" "$ADMIN_TOKEN" '{"roles":["admin"]}'
    judge "rbac/ADMIN授予admin-200" 200 '"loginEnabled"[[:space:]]*:[[:space:]]*true' "ADMIN 可授予 admin（对照）"
  else
    skip "rbac/PL授予admin-403" "$LAST_STATUS" "未造出待审批申请（可能未开 dev-mode）｜ $(snippet)"
    skip "rbac/ADMIN授予admin-200" "-" "依赖上一条"
  fi
fi

# ---- 软删除（t140）：204 / 404 / 同 code 409 / includeDeleted 可见 / restore ----
  SD_CODE="SMK-SD-${TAG}"
  api_json POST '/api/v1/customers' "$ADMIN_TOKEN" \
    "{\"code\":\"${SD_CODE}\",\"name\":\"冒烟软删客户 ${TAG}\",\"industryName\":\"金融业\"}"
  SD_ID="$(jfirst id)"
  judge "softdel/建客户-201" 201 '"id"' "POST /customers（软删夹具）"
  if [ -n "$SD_ID" ]; then
    request DELETE "/api/v1/customers/${SD_ID}" "$ADMIN_TOKEN"
    judge "softdel/删除-204" 204 '' "DELETE /customers/{id}（软删）"

    request GET "/api/v1/customers/${SD_ID}" "$ADMIN_TOKEN"
    judge "softdel/删后详情-404" 404 '' "软删后详情不可见"

    api_json POST '/api/v1/customers' "$ADMIN_TOKEN" \
      "{\"code\":\"${SD_CODE}\",\"name\":\"冒烟软删客户重建 ${TAG}\",\"industryName\":\"金融业\"}"
    judge "softdel/同code重建-409" 409 '' "软删行仍占唯一键"

    request GET '/api/v1/admin/customers?includeDeleted=true&page=0&size=100' "$ADMIN_TOKEN"
    if [ "$LAST_STATUS" = "200" ] && flatten | grep -q "$SD_CODE"; then
      pass "softdel/管理端可见已删" 200 "includeDeleted=true 命中 ${SD_CODE}"
    else
      fail "softdel/管理端可见已删" "$LAST_STATUS" "未命中 ｜ $(snippet)"
    fi

    request POST "/api/v1/admin/customers/${SD_ID}/restore" "$ADMIN_TOKEN"
    judge "softdel/恢复-204" 204 '' "POST /admin/customers/{id}/restore"

    request GET "/api/v1/customers/${SD_ID}" "$ADMIN_TOKEN"
    judge_ok "softdel/恢复后可查" '' "GET 详情 200"

    request DELETE "/api/v1/customers/${SD_ID}" "$ADMIN_TOKEN" >/dev/null 2>&1
  else
    skip "softdel/删除-204" "$LAST_STATUS" "软删夹具未创建"
    skip "softdel/删后详情-404" "-" "依赖上一条"
    skip "softdel/同code重建-409" "-" "依赖上一条"
    skip "softdel/管理端可见已删" "-" "依赖上一条"
    skip "softdel/恢复-204" "-" "依赖上一条"
    skip "softdel/恢复后可查" "-" "依赖上一条"
  fi

  # ---- 标签（t141/t145）：挂靠自动打标签 / 列表内联 tags / 批量端点 / 下拉 ----
  if [ -n "${ASSET_ID:-}" ]; then
    request GET "/api/v1/assets/${ASSET_ID}/tags" "$ADMIN_TOKEN"
    if [ "$LAST_STATUS" = "200" ] && flatten | grep -q "冒烟项目 ${TAG}"; then
      pass "tag/挂靠自动打项目名" 200 "命中项目名"
    else
      fail "tag/挂靠自动打项目名" "$LAST_STATUS" "未命中项目名 ｜ $(snippet)"
    fi

    request GET '/api/v1/assets?page=0&size=5' "$ADMIN_TOKEN"
    if [ "$LAST_STATUS" = "200" ] && flatten | grep -q '"tags"'; then
      pass "tag/资产列表内联tags" 200 "每项含 tags"
    else
      fail "tag/资产列表内联tags" "$LAST_STATUS" "未见 tags ｜ $(snippet)"
    fi

    request GET "/api/v1/assets/tags?ids=${ASSET_ID}" "$ADMIN_TOKEN"
    judge "tag/批量端点-200" 200 "\"${ASSET_ID}\"" "GET /assets/tags?ids="

    request GET '/api/v1/tags' "$ADMIN_TOKEN"
    judge "tag/下拉-200" 200 '"assetCount"' "GET /tags"

    request GET '/api/v1/admin/tag-definitions?includeDeleted=true&page=0&size=50' "$ADMIN_TOKEN"
    judge "tag/管理端列表-200" 200 '"content"' "GET /admin/tag-definitions"

    api_json POST '/api/v1/admin/tag-definitions' "$ENGINEER_TOKEN" '{"name":"越权标签"}'
    judge "tag/非ADMIN写-403" 403 '' "POST /admin/tag-definitions 仅 ADMIN"
  else
    skip "tag/挂靠自动打项目名" "-" "无资产夹具"
    skip "tag/资产列表内联tags" "-" "依赖上一条"
    skip "tag/批量端点-200" "-" "依赖上一条"
    skip "tag/下拉-200" "-" "依赖上一条"
    skip "tag/管理端列表-200" "-" "依赖上一条"
    skip "tag/非ADMIN写-403" "-" "依赖上一条"
  fi

  # ---- 项目批量资产（t154）与审批历史（t156）----
  if [ -n "${PROJECT_ID:-}" ] && [ -n "${CUST_ID:-}" ]; then
    request GET "/api/v1/projects/${PROJECT_ID}/available-assets?page=0&size=10" "$ADMIN_TOKEN"
    judge "bulk/可选资产列表-200" 200 '"content"' "GET /projects/{id}/available-assets"

    api_json POST '/api/v1/projects' "$ADMIN_TOKEN" \
      "{\"code\":\"SMK-BULK-${TAG}\",\"name\":\"冒烟批量项目 ${TAG}\",\"customerId\":\"${CUST_ID}\",\"managerId\":\"${ADMIN_ID}\"}"
    BULK_PID="$(jfirst id)"
    if [ -n "$BULK_PID" ]; then
      api_json POST '/api/v1/assets' "$ADMIN_TOKEN" \
        "{\"name\":\"冒烟批量资产 ${TAG}\",\"targetType\":\"Web应用\",\"targetSource\":\"客户委托\",\"priority\":\"P3\",\"status\":\"active\",\"teamId\":\"${TEAM_ID}\",\"projectId\":\"${BULK_PID}\"}"
      BULK_AID="$(jfirst id)"
    fi
    if [ -n "${BULK_AID:-}" ]; then
      api_json POST "/api/v1/projects/${PROJECT_ID}/assets" "$ADMIN_TOKEN" \
        "{\"assetIds\":[\"${BULK_AID}\"]}"
      judge "bulk/批量添加-200-added1" 200 '"added"[[:space:]]*:[[:space:]]*1' "POST /projects/{id}/assets"

      api_json POST "/api/v1/projects/${PROJECT_ID}/assets" "$ADMIN_TOKEN" \
        "{\"assetIds\":[\"${BULK_AID}\"]}"
      judge "bulk/重复添加-skipped1" 200 '"skipped"[[:space:]]*:[[:space:]]*1' "幂等：已挂靠同项目"

      QV_BULK="$(flatten | grep -o '"skipped"[[:space:]]*:[[:space:]]*[0-9]*' | head -1 | grep -o '[0-9]*$')"

      # A2 仍挂 P2 → 从 P1 移除安全（removed=1）
      api_json DELETE "/api/v1/projects/${PROJECT_ID}/assets" "$ADMIN_TOKEN" \
        "{\"assetIds\":[\"${BULK_AID}\"]}"
      judge "bulk/移除一条-removed1" 200 '"removed"[[:space:]]*:[[:space:]]*1' "DELETE body 用 assetIds"

      # 从唯一挂靠的项目移除 → 409（一条都不删）
      api_json DELETE "/api/v1/projects/${BULK_PID}/assets" "$ADMIN_TOKEN" \
        "{\"assetIds\":[\"${BULK_AID}\"]}"
      judge "bulk/移除唯一挂靠-409" 409 '' "移除后零挂靠 → 409"

      api_json POST '/api/v1/projects/00000000-0000-0000-0000-0000000000ff/assets' "$ADMIN_TOKEN" \
        "{\"assetIds\":[\"${BULK_AID}\"]}"
      judge "bulk/项目不存在-404" 404 '' "POST /projects/{未知id}/assets"

      request DELETE "/api/v1/assets/${BULK_AID}" "$ADMIN_TOKEN" >/dev/null 2>&1
      request DELETE "/api/v1/projects/${BULK_PID}" "$ADMIN_TOKEN" >/dev/null 2>&1
    else
      skip "bulk/批量添加-200-added1" "$LAST_STATUS" "批量夹具未就绪"
      skip "bulk/重复添加-skipped1" "-" "依赖上一条"
      skip "bulk/移除一条-removed1" "-" "依赖上一条"
      skip "bulk/移除唯一挂靠-409" "-" "依赖上一条"
      skip "bulk/项目不存在-404" "-" "依赖上一条"
    fi
  else
    skip "bulk/可选资产列表-200" "-" "缺少项目/客户夹具"
    skip "bulk/批量添加-200-added1" "-" "缺少项目夹具"
    skip "bulk/重复添加-skipped1" "-" "依赖上一条"
    skip "bulk/移除一条-removed1" "-" "依赖上一条"
    skip "bulk/移除唯一挂靠-409" "-" "依赖上一条"
    skip "bulk/项目不存在-404" "-" "依赖上一条"
  fi

  # 审批历史（t156）
  request GET '/api/v1/assets/my/access-approval-history?as=reviewer&status=all&page=0&size=10' "$ADMIN_TOKEN"
  judge "approval/历史列表-200" 200 '"content"' "GET /assets/my/access-approval-history"
  request GET '/api/v1/assets/my/access-approval-history?as=bogus' "$ADMIN_TOKEN"
  judge "approval/非法as-400" 400 '' "as 白名单"
  request GET '/api/v1/assets/my/access-approval-history/count?as=reviewer' "$ADMIN_TOKEN"
  judge "approval/历史计数-200" 200 '"approved"' "GET .../access-approval-history/count"
  request GET '/api/v1/assets/my/access-approval-history?as=reviewer' ""
  judge "approval/未登录-401" 401 '' "只读端点仍需登录"

  # ---- 上传审批（t160）----
  printf 'small tool\n' > /tmp/smk_tool_small.txt
  api_upload_tool() {
    local extra="$1"
    request POST "/api/v1/uploads/pending?targetType=TOOL&name=smk_review_${TAG}${extra}" "$ADMIN_TOKEN"
  }
  # 小文件 → DIRECT 落业务表
  DIR_ST=$(curl -s -o /tmp/smk_up.json -w '%{http_code}' -X POST "$BASE_URL/api/v1/uploads/pending?targetType=TOOL&name=smk_direct_${TAG}" \
    -H "Authorization: Bearer $ADMIN_TOKEN" -F 'file=@/tmp/smk_tool_small.txt')
  DIR_STATUS=$(python3 -c "import json;d=json.load(open('/tmp/smk_up.json'));print((d.get('data') or {}).get('status',''))" 2>/dev/null)
  if [ "$DIR_ST" = "200" ] || [ "$DIR_ST" = "201" ]; then
    pass "upload/小文件直接落库(${DIR_STATUS:-DIRECT})" "$DIR_ST" "≤阈值 → DIRECT"
  else
    fail "upload/小文件直接落库(${DIR_STATUS:-DIRECT})" "$DIR_ST" "期望 200/201"
  fi

  request GET '/api/v1/admin/upload-approvals?status=PENDING&page=0&size=10' "$ADMIN_TOKEN"
  judge "upload/管理员审批列表-200" 200 '"content"' "GET /admin/upload-approvals"
  request GET '/api/v1/admin/upload-approvals?status=PENDING&page=0&size=10' "$ENGINEER_TOKEN"
  judge "upload/非管理员审批列表-403" 403 '' "仅 ADMIN"

  # ---- 知识库/文档附件（t162）----
  api_json POST '/api/v1/knowledge' "$ADMIN_TOKEN" \
    "{\"title\":\"冒烟附件文章 ${TAG}\",\"category\":\"技术\",\"summary\":\"冒烟\",\"content\":\"冒烟内容\"}"
  KNOWLEDGE_ID="$(jfirst id)"
  if [ -n "${KNOWLEDGE_ID:-}" ]; then
    printf 'PDF-ish payload %s\n' "$TAG" > /tmp/smk_attach.pdf
    ATT_ST=$(curl -s -o /tmp/smk_att.json -w '%{http_code}' -X POST "$BASE_URL/api/v1/knowledge/${KNOWLEDGE_ID}/attachments" \
      -H "Authorization: Bearer $ADMIN_TOKEN" -F 'file=@/tmp/smk_attach.pdf;filename=冒烟附件.pdf')
    ATT_ID=$(python3 -c "import json;d=json.load(open('/tmp/smk_att.json'));print((d.get('data') or {}).get('id',''))" 2>/dev/null)
    [ "$ATT_ST" = "200" ] || [ "$ATT_ST" = "201" ] && [ -n "$ATT_ID" ] && pass "attach/知识库上传-200" "$ATT_ST" "id=${ATT_ID}" || fail "attach/知识库上传-200" "$ATT_ST" "期望 200/201"
    if [ -n "${ATT_ID:-}" ]; then
      request GET "/api/v1/knowledge/${KNOWLEDGE_ID}/attachments" "$ADMIN_TOKEN"
      judge "attach/列表含上传件" 200 '冒烟附件.pdf' "GET /knowledge/{id}/attachments"
      DL_H=$(curl -s -D - -o /dev/null "$BASE_URL/api/v1/knowledge/${KNOWLEDGE_ID}/attachments/${ATT_ID}/download" -H "Authorization: Bearer $ADMIN_TOKEN")
      printf '%s' "$DL_H" | grep -qi 'filename\*=UTF-8' && pass "attach/下载带 RFC5987 中文名" "200" "$(printf '%s' "$DL_H" | grep -i 'content-disposition' | head -1 | tr -d '\r')" || fail "attach/下载带 RFC5987 中文名" "-" "缺少 filename*"
      # 跨实体 404（用另一个知识库 id 或文档侧）
      OTHER_ST=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/v1/documents/${KNOWLEDGE_ID}/attachments/${ATT_ID}/download" -H "Authorization: Bearer $ADMIN_TOKEN")
      [ "$OTHER_ST" = "404" ] && pass "attach/跨实体下载-404" 404 "ownerType 隔离" || fail "attach/跨实体下载-404" "$OTHER_ST" "期望 404"
      request DELETE "/api/v1/knowledge/${KNOWLEDGE_ID}/attachments/${ATT_ID}" "$ADMIN_TOKEN"
      judge "attach/删除-200" 200 '' "软删 + 删物理文件"
      AFTER_ST=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/v1/knowledge/${KNOWLEDGE_ID}/attachments/${ATT_ID}/download" -H "Authorization: Bearer $ADMIN_TOKEN")
      [ "$AFTER_ST" = "404" ] && pass "attach/删除后下载-404" 404 '' || fail "attach/删除后下载-404" "$AFTER_ST" "期望 404"
    else
      skip "attach/列表含上传件" "-" "附件 id 缺失"
      skip "attach/下载带 RFC5987 中文名" "-" "依赖上一条"
      skip "attach/跨实体下载-404" "-" "依赖上一条"
      skip "attach/删除-200" "-" "依赖上一条"
      skip "attach/删除后下载-404" "-" "依赖上一条"
    fi
  else
    skip "attach/知识库上传-200" "-" "缺少知识库夹具"
    skip "attach/列表含上传件" "-" "缺少知识库夹具"
    skip "attach/下载带 RFC5987 中文名" "-" "缺少知识库夹具"
    skip "attach/跨实体下载-404" "-" "缺少知识库夹具"
    skip "attach/删除-200" "-" "缺少知识库夹具"
    skip "attach/删除后下载-404" "-" "缺少知识库夹具"
  fi

  # ---- 列表 status 过滤（t162）----
  request GET '/api/v1/knowledge?status=DRAFT&page=0&size=2' "$ADMIN_TOKEN"
  judge "knowledge/status=DRAFT-200" 200 '"totalElements"' "数据库过滤"
  request GET '/api/v1/knowledge?status=bogus' "$ADMIN_TOKEN"
  judge "knowledge/非法status-400" 400 '' "白名单"
  request GET '/api/v1/documents?status=PUBLISHED&page=0&size=2' "$ADMIN_TOKEN"
  judge "documents/status=PUBLISHED-200" 200 '"totalElements"' "数据库过滤"

  # ---- 我的上传（t166）----
  request GET '/api/v1/uploads/my?status=all&page=0&size=10' "$ADMIN_TOKEN"
  judge "uploads/my-列表-200" 200 '"content"' "GET /uploads/my"
  request GET '/api/v1/uploads/my?status=PENDING&page=0&size=10' "$ADMIN_TOKEN"
  judge "uploads/my-审核中-200" 200 '' "status=PENDING"
  request GET '/api/v1/uploads/my?status=bogus' "$ADMIN_TOKEN"
  judge "uploads/my-非法status-400" 400 '' "白名单含 CANCELLED"
  RAND_UP="$(python3 -c 'import uuid;print(uuid.uuid4())')"
  request DELETE "/api/v1/uploads/my/${RAND_UP}" "$ADMIN_TOKEN"
  judge "uploads/my-撤回不存在-404" 404 '' "DELETE /uploads/my/{未知id}"
  request GET "/api/v1/admin/upload-approvals?status=CANCELLED&keyword=x&page=0&size=5" "$ADMIN_TOKEN"
  judge "upload-approvals-CANCELLED-200" 200 '"content"' "管理端支持 CANCELLED/keyword"

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
