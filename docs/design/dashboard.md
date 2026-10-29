# 仪表盘：趋势、工作量排行与配置

本文档记录 `/api/v1/dashboard` 下三个本轮补齐的接口口径，前端按此渲染即可，无需本地兜底聚合。

## 1. 统计窗口与归一规则

| 参数 | 默认 | 上限 | 说明 |
|---|---|---|---|
| `days`（趋势、排行） | 30 | 365 | `<=0` 取 30；`>365` 收敛为 365 |
| `limit`（排行） | 10 | 100 | `<=0` 取 10；`>100` 收敛为 100 |

窗口定义：`[今天 - (days-1) 天 00:00:00, 明天 00:00:00)`，即**含今天在内共 days 个自然日**，服务器本地时区。

## 2. 趋势接口

```
GET /api/v1/dashboard/trends/assets?days=7
GET /api/v1/dashboard/trends/vulnerabilities?days=7
GET /api/v1/dashboard/trends/workload?days=7
```

响应（`data` 恒为 `days` 个点，无数据的天补 0，保证曲线连续）：

```json
{
  "success": true,
  "data": {
    "days": 7,
    "startDate": "2026-10-23",
    "endDate": "2026-10-29",
    "data": [
      { "date": "2026-10-23", "count": 0 },
      { "date": "2026-10-24", "count": 1 }
    ]
  }
}
```

| 接口 | 归日字段 | 统计口径 |
|---|---|---|
| `/trends/assets` | `assets.created_at` | 当日新增资产数 |
| `/trends/vulnerabilities` | `vulnerabilities.created_at` | 当日新发现漏洞数 |
| `/trends/workload` | `tasks.completed_at`，为空回退 `tasks.updated_at` | 当日**已完成**任务数（`status=completed`） |

> **workload 时间字段口径（重要）**：优先用 `completed_at`；历史数据中存在「已完成但未写 `completed_at`」的任务，
> 这类任务回退使用 `updated_at`，避免被漏统计。`/trends/workload` 按当前登录人过滤（`assigned_to`）。

## 3. 跨用户工作量排行

```
GET /api/v1/dashboard/workload/ranking?limit=10&days=30
Authorization: Bearer <token>
```

```json
{
  "success": true,
  "data": [
    { "userId": "uuid", "username": "admin", "taskCount": 3, "estimatedHours": 6, "actualHours": 3 }
  ]
}
```

- 聚合：窗口内任务按 `assigned_to` 分组，`taskCount` 为任务数，`estimatedHours` / `actualHours` 为工时求和（空值按 0）。
- 归日/入窗字段与工作量趋势一致：`completed_at` 优先，回退 `updated_at`。
- 排序：`taskCount` 倒序，同数按 `username` 升序；`limit` 截断。
- **数据边界**：`admin` / `project_leader` / `team_leader` 返回全量排行；
  其他角色只返回自己那条（无任务时也返回 `taskCount=0` 的行，便于前端稳定渲染）。
  判定入口为 `AccessGuard#isManager`，与现有数据级授权体系一致。

## 4. 仪表盘配置

```
GET    /api/v1/dashboard/config
POST   /api/v1/dashboard/config      # 保存/更新（upsert）
PUT    /api/v1/dashboard/my          # 同 saveConfig
DELETE /api/v1/dashboard/config
```

| 场景 | 行为 |
|---|---|
| 只传 `cards`（不传 `layout`） | 新建时 `layout` 兜底为 `[]`；已有配置保持原 `layout`，返回 200（此前因 `layout` NOT NULL 直接 500） |
| 只传 `layout` | 已保存的 `cards` 保持不变 |
| 两者都不传 / 空串 | 新建时分别兜底 `[]` / `[]`；已有配置不变 |
| 重复保存同一份内容 | 返回 200，同一用户始终只有一行（`dashboard_configs.user_id` 唯一），幂等 |
| 读取未保存过配置 | 返回 `{layout:"[]", cards:"[]"}` 的默认对象，**不写库** |

字段说明：`layout`、`cards` 都是**字符串形式**的 JSON（TEXT 列），前端需要自行 `JSON.parse`。

## 5. 验证

```bash
# 后端用例
cd backend && mvn -B test -Dtest=DashboardTrendTest,DashboardRankingTest

# 线上核对
TOKEN=$(curl -s -X POST http://HOST:PORT/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r .data.token)

# 7 个点，最后一点是今天
curl -s "http://HOST:PORT/api/v1/dashboard/trends/assets?days=7" -H "Authorization: Bearer $TOKEN" \
  | jq '.data.data | length, .[-1]'

# admin 多行、工程师只有自己一行
curl -s "http://HOST:PORT/api/v1/dashboard/workload/ranking?limit=10&days=30" \
  -H "Authorization: Bearer $TOKEN" | jq '.data | length'
curl -s -X POST http://HOST:PORT/api/v1/dashboard/config -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"cards":"[\"assets\"]"}' | jq '.data.layout'   # 期望 "[]"
```
