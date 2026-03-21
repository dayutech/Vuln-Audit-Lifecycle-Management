# 状态图设计

## 1. 漏洞生命周期状态图

### 1.1 工作流状态 (workflow_status)

```mermaid
stateDiagram-v2
    [*] --> Discovered: 发现漏洞
    Discovered --> Verified: 审核通过
    Discovered --> Closed: 直接关闭
    Verified --> Analyzing: 开始分析
    Verified --> Closed: 直接关闭
    Analyzing --> Analyzed: 分析完成
    Analyzing --> Verified: 分析回退
    Analyzed --> ExploitDev: 客户确认后开始利用开发
    Analyzed --> Closed: 直接关闭
    ExploitDev --> FieldTesting: 提交测试
    ExploitDev --> Analyzed: 开发回退
    FieldTesting --> Delivered: 测试通过
    FieldTesting --> ExploitDev: 测试失败
    Delivered --> Closed: 归档关闭
    Closed --> [*]
    
    note right of Discovered
        漏洞刚被发现
        待初步审核
    end note
    
    note right of Verified
        漏洞已验证
        确认存在
    end note
    
    note right of Analyzing
        进行漏洞利用分析
        编写POC
    end note
    
    note right of Analyzed
        分析已完成
        待客户确认
    end note
    
    note right of ExploitDev
        利用代码开发
        探测/突破/控守
    end note
    
    note right of FieldTesting
        出场测试
        验证利用有效性
    end note
    
    note right of Delivered
        已交付客户
        记录交付时间
    end note
    
    note right of Closed
        漏洞已关闭
        记录经验教训
    end note
```

### 1.2 客户确认状态 (customer_status)

```mermaid
stateDiagram-v2
    [*] --> Pending: 分析完成
    Pending --> Confirmed: 客户确认接收
    Pending --> Rejected: 客户拒绝
    Rejected --> Pending: 重新分析提交
    
    note right of Pending
        待确认
        等待客户确认
    end note
    
    note right of Confirmed
        已确认
        客户确认接收漏洞
    end note
    
    note right of Rejected
        已拒绝
        客户拒绝接收
        需填写拒绝原因
    end note
```

### 1.3 公开披露状态 (disclosure_status)

```mermaid
stateDiagram-v2
    [*] --> Undisclosed: 漏洞发现
    Undisclosed --> Disclosed: 被公开披露
    Disclosed --> HasCVE: 分配CVE编号
    HasCVE --> [*]
    
    note right of Undisclosed
        未公开
        漏洞信息仅团队内部可见
    end note
    
    note right of Disclosed
        已公开
        漏洞被其他人公开披露
        (安全公告、博客等)
    end note
    
    note right of HasCVE
        已分配CVE
        漏洞已被分配CVE编号
    end note
```

### 1.4 厂商修复追踪

> **说明**：厂商修复状态通过公开渠道观察，不主动通知厂商。

| 字段 | 说明 |
|------|------|
| vendor_fixed | 厂商是否已修复（通过公开渠道观察） |
| vendor_fixed_at | 厂商修复时间 |
| vendor_fix_version | 修复版本号 |

### 1.5 状态关联规则

| workflow_status | customer_status | disclosure_status | 说明 |
|-----------------|-----------------|-------------------|------|
| analyzed | pending | undisclosed | 分析完成，等待客户确认 |
| exploit_development | confirmed | any | 客户确认后，进入利用开发 |
| analyzing | rejected | any | 客户拒绝后，回退到分析中 |
| any | any | disclosed | 漏洞被公开披露，不影响工作流 |

> **注意**：
> - disclosure_status 独立于 workflow_status，可在任意阶段更新
> - 厂商修复状态（vendor_fixed）通过公开渠道观察，不主动通知厂商
> - 通过CVE数据库、安全公告、社交媒体等渠道观察漏洞是否被披露

## 2. 资产状态图

```mermaid
stateDiagram-v2
    [*] --> Active: 创建资产
    Active --> Active: 正常使用
    Active --> UnderTest: 开始挖掘
    UnderTest --> Active: 挖掘完成
    UnderTest --> Suspended: 暂停挖掘
    Suspended --> Active: 恢复挖掘
    Suspended --> Archived: 归档
    Active --> Archived: 归档
    Archived --> Active: 恢复使用
    Archived --> [*]
    
    note right of Active
        正常活动状态
        可进行漏洞挖掘
    end note
    
    note right of UnderTest
        正在挖掘中
        独占该资产
    end note
    
    note right of Suspended
        暂停挖掘
        资源释放
    end note
    
    note right of Archived
        已归档
        只读状态
    end note
```

## 3. 环境状态图

> **说明**：本模块为靶场环境管理功能，相关状态暂保持为空实现，待后续需求明确后再行开发。

```mermaid
stateDiagram-v2
    [*] --> Created: 创建环境
    Created --> Configured: 配置完成
    Configured --> Deploying: 部署中
    Deploying --> Running: 部署成功
    Deploying --> Error: 部署失败
    Error --> Configured: 重新配置
    Error --> Destroyed: 销毁
    Running --> Stopped: 停止
    Running --> Updating: 更新中
    Running --> Snapshotting: 创建快照
    Snapshotting --> Running: 快照完成
    Updating --> Running: 更新完成
    Stopped --> Running: 启动
    Stopped --> Destroyed: 销毁
    Destroyed --> [*]
    
    note right of Created
        环境刚创建
        待配置
    end note
    
    note right of Running
        环境运行中
        可正常使用
    end note
    
    note right of Error
        环境异常
        需要排查
    end note
```

## 4. 任务状态图

```mermaid
stateDiagram-v2
    [*] --> Todo: 创建任务
    Todo --> InProgress: 开始处理
    Todo --> Cancelled: 取消任务
    InProgress --> Review: 提交审核
    InProgress --> Blocked: 阻塞
    Blocked --> InProgress: 解除阻塞
    Blocked --> Cancelled: 取消任务
    Review --> Done: 审核通过
    Review --> InProgress: 审核拒绝
    Done --> [*]
    Cancelled --> [*]
    
    note right of Todo
        待处理
        等待分配/认领
    end note
    
    note right of InProgress
        处理中
        进行漏洞挖掘
    end note
    
    note right of Review
        待审核
        等待负责人审核
    end note
    
    note right of Done
        任务完成
        产出已确认
    end note
    
    note right of Blocked
        阻塞
        等待其他任务/资源
    end note
```

## 5. 文档状态图

```mermaid
stateDiagram-v2
    [*] --> Draft: 创建文档
    Draft --> Draft: 继续编辑
    Draft --> Review: 提交审核
    Draft --> Archived: 归档
    Review --> Draft: 审核拒绝
    Review --> Published: 审核通过
    Published --> Updated: 内容更新
    Published --> Archived: 归档
    Updated --> Review: 提交审核
    Updated --> Published: 直接发布
    Archived --> Draft: 恢复编辑
    Archived --> [*]
    
    note right of Draft
        草稿状态
        仅创建者可见
    end note
    
    note right of Review
        待审核
        等待审批
    end note
    
    note right of Published
        已发布
        团队可见
    end note
    
    note right of Archived
        已归档
        只读状态
    end note
```

## 6. 工具状态图

```mermaid
stateDiagram-v2
    [*] --> Uploading: 上传中
    Uploading --> Active: 上传成功
    Uploading --> Failed: 上传失败
    Failed --> Uploading: 重新上传
    Active --> Active: 正常使用
    Active --> Deprecated: 标记废弃
    Deprecated --> Active: 恢复使用
    Deprecated --> Archived: 归档
    Archived --> [*]
    
    note right of Active
        正常可用
        团队可下载
    end note
    
    note right of Deprecated
        已废弃
        建议使用新版本
    end note
```

## 7. 用户状态图

```mermaid
stateDiagram-v2
    [*] --> Pending: 注册待激活
    Pending --> Active: 激活成功
    Pending --> Rejected: 激活拒绝
    Active --> Active: 正常使用
    Active --> Suspended: 账户暂停
    Active --> Deactivated: 账户注销
    Suspended --> Active: 恢复使用
    Suspended --> Deactivated: 永久注销
    Deactivated --> [*]
    
    note right of Pending
        待激活
        等待管理员审核
    end note
    
    note right of Active
        正常活动
        可正常使用系统
    end note
    
    note right of Suspended
        暂停使用
        临时限制登录
    end note
```

## 8. 团队成员状态图

```mermaid
stateDiagram-v2
    [*] --> Available: 加入团队
    Available --> Busy: 开始任务
    Available --> Offline: 离开
    Busy --> Available: 任务完成
    Busy --> Offline: 离开
    Offline --> Available: 返回
    Offline --> Left: 离开团队
    Left --> [*]
    
    note right of Available
        空闲
        可接收新任务
    end note
    
    note right of Busy
        工作中
        有任务进行中
    end note
    
    note right of Offline
        离线
        暂时不可用
    end note
```

## 9. 资产认领状态图

> **说明**：资产认领为自动批准，无需人工审核

```mermaid
stateDiagram-v2
    [*] --> Approved: 提交认领（自动批准）
    Approved --> Completed: 工程师完成挖掘
    Approved --> Abandoned: 工程师放弃认领
    Completed --> [*]
    Abandoned --> [*]
    
    note right of Approved
        已批准
        漏洞挖掘工程师可以开始漏洞挖掘
    end note
    
    note right of Completed
        已完成
        工程师完成漏洞挖掘工作
    end note
    
    note right of Abandoned
        已放弃
        工程师主动放弃认领
    end note
```

## 10. 漏洞优先级与状态关系

```mermaid
graph LR
    subgraph Critical["严重 P0"]
        C1[Discovered] --> C2[Verified]
        C2 --> C3[Analyzing]
        C3 --> C4[Analyzed]
        C4 --> C5[ExploitDev]
        C5 --> C6[FieldTesting]
        C6 --> C7[Delivered]
        C7 --> C8[Closed]
    end
    
    subgraph High["高 P1"]
        H1[Discovered] --> H2[Verified]
        H2 --> H3[Analyzing]
        H3 --> H4[Analyzed]
        H4 --> H5[ExploitDev]
        H5 --> H6[FieldTesting]
        H6 --> H7[Delivered]
        H7 --> H8[Closed]
    end
    
    subgraph Medium["中 P2"]
        M1[Discovered] --> M2[Verified]
        M2 --> M3[Analyzing]
        M3 --> M4[Analyzed]
        M4 --> M5[ExploitDev]
        M5 --> M6[FieldTesting]
        M6 --> M7[Delivered]
        M7 --> M8[Closed]
    end
    
    subgraph Low["低 P3"]
        L1[Discovered] --> L2[Verified]
        L2 --> L3[Analyzing]
        L3 --> L4[Analyzed]
        L4 --> L5[ExploitDev]
        L5 --> L6[FieldTesting]
        L6 --> L7[Delivered]
        L7 --> L8[Closed]
    end
    
    style Critical fill:#ff6666
    style High fill:#ffaa66
    style Medium fill:#ffdd66
    style Low fill:#66aaff
```

> **说明**：客户确认状态(customer_status)和公开披露状态(disclosure_status)独立于工作流状态，不影响优先级流转。

## 11. 状态转换规则表

### 11.1 漏洞工作流状态转换规则

| 当前状态 | 目标状态 | 触发条件 | 权限要求 | 备注 |
|----------|----------|----------|----------|------|
| Discovered | Verified | 审核通过 | 团队负责人 | 需要至少一名审核人 |
| Discovered | Closed | 直接关闭 | 团队负责人 | 无效漏洞 |
| Verified | Analyzing | 开始分析 | 漏洞挖掘工程师 | 自动通知分配人 |
| Verified | Closed | 直接关闭 | 团队负责人 | 取消挖掘 |
| Analyzing | Analyzed | 分析完成 | 漏洞挖掘工程师 | 需要提交分析报告 |
| Analyzing | Verified | 分析回退 | 漏洞挖掘工程师 | 需要重新验证 |
| Analyzed | ExploitDev | 客户确认后开始利用开发 | 漏洞挖掘工程师 | 需要客户确认 |
| Analyzed | Closed | 直接关闭 | 团队负责人 | 取消挖掘 |
| ExploitDev | FieldTesting | 提交出场测试 | 漏洞挖掘工程师 | 利用代码完成 |
| ExploitDev | Analyzed | 开发回退 | 漏洞挖掘工程师 | 需要重新分析 |
| FieldTesting | Delivered | 测试通过 | 漏洞挖掘工程师 | 可以交付 |
| FieldTesting | ExploitDev | 测试失败 | 漏洞挖掘工程师 | 需要修复利用代码 |
| Delivered | Closed | 归档关闭 | 团队负责人 | 完成归档 |

### 11.2 客户确认状态转换规则

| 当前状态 | 目标状态 | 触发条件 | 权限要求 | 备注 |
|----------|----------|----------|----------|------|
| Pending | Confirmed | 客户确认接收 | 客户 | 确认接收漏洞 |
| Pending | Rejected | 客户拒绝 | 客户 | 需填写拒绝原因 |
| Rejected | Pending | 重新分析提交 | 漏洞挖掘工程师 | 修改后重新提交 |

### 11.3 公开披露状态转换规则

| 当前状态 | 目标状态 | 触发条件 | 权限要求 | 备注 |
|----------|----------|----------|----------|------|
| Undisclosed | Disclosed | 漏洞被公开披露 | 系统自动/手动更新 | 通过公开渠道观察 |
| Disclosed | HasCVE | 分配CVE编号 | 系统自动/手动更新 | CVE数据库同步 |

### 11.4 其他状态转换规则

| 当前状态 | 目标状态 | 触发条件 | 权限要求 | 备注 |
|----------|----------|----------|----------|------|
| Todo | InProgress | 开始处理 | 任务接收人 | 更新团队状态 |
| InProgress | Review | 提交审核 | 任务处理人 | 产出需可验证 |
| Review | Done | 审核通过 | 团队负责人 | 自动通知 |
| Draft | Review | 提交审核 | 文档作者 | 需指定审核人 |
| Review | Published | 审核通过 | 审核人 | 自动索引 |
| Approved | Completed | 挖掘完成 | 漏洞挖掘工程师 | 完成漏洞挖掘 |
| Approved | Abandoned | 放弃认领 | 漏洞挖掘工程师 | 主动放弃 |

## 12. 资产访问审批状态图

```mermaid
stateDiagram-v2
    [*] --> Pending: 申请访问
    Pending --> Approved: 审批通过
    Pending --> Rejected: 审批拒绝
    Approved --> Active: 获得访问权限
    Active --> Expired: 权限过期
    Rejected --> Pending: 重新申请
    Active --> [*]: 任务完成
    Expired --> [*]: 权限结束
```

| 当前状态 | 目标状态 | 触发条件 | 权限要求 | 备注 |
|----------|----------|----------|----------|------|
| Pending | Approved | 审批通过 | 部门领导 | 在审批范围内 |
| Pending | Rejected | 审批拒绝 | 部门领导 | 在审批范围内 |
| Approved | Active | 权限生效 | 系统 | 自动激活 |
| Active | Expired | 权限过期 | 系统 | 超过授权时间 |
| Rejected | Pending | 重新申请 | 申请者 | 修改申请内容 |
| Expired | Pending | 重新申请 | 申请者 | 需要重新审批 |
