# 状态图设计

## 1. 漏洞生命周期状态图

```mermaid
stateDiagram-v2
    [*] --> Discovered: 发现漏洞
    Discovered --> Verified: 审核通过
    Discovered --> Rejected: 审核拒绝
    Verified --> Analyzing: 开始分析
    Analyzing --> Analyzed: 分析完成
    Analyzed --> Reported: 报告编写完成
    Reported --> Closed: 归档关闭
    Reported --> Fixed: 厂商已修复
    Fixed --> Closed: 归档
    Rejected --> [*]
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
        待编写报告
    end note
    
    note right of Reported
        漏洞报告已提交
        完成归档
    end note
    
    note right of Fixed
        厂商已修复漏洞
        记录修复时间
    end note
    
    note right of Closed
        漏洞已关闭
        记录经验教训
    end note
```

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

## 9. 漏洞优先级与状态关系

```mermaid
graph LR
    subgraph Critical["严重 P0"]
        C1[Discovered] --> C2[Verified]
        C2 --> C3[Analyzing]
        C3 --> C4[Reported]
        C4 --> C5[Closed]
    end
    
    subgraph High["高 P1"]
        H1[Discovered] --> H2[Verified]
        H2 --> H3[Analyzing]
        H3 --> H4[Reported]
        H4 --> H5[Closed]
    end
    
    subgraph Medium["中 P2"]
        M1[Discovered] --> M2[Verified]
        M2 --> M3[Analyzing]
        M3 --> M4[Reported]
        M4 --> M5[Closed]
    end
    
    subgraph Low["低 P3"]
        L1[Discovered] --> L2[Verified]
        L2 --> L3[Analyzing]
        L3 --> L4[Reported]
        L4 --> L5[Closed]
    end
    
    style Critical fill:#ff6666
    style High fill:#ffaa66
    style Medium fill:#ffdd66
    style Low fill:#66aaff
```

## 10. 状态转换规则表

| 当前状态 | 目标状态 | 触发条件 | 权限要求 | 备注 |
|----------|----------|----------|----------|------|
| Discovered | Verified | 审核通过 | 团队负责人 | 需要至少一名审核人 |
| Discovered | Rejected | 审核拒绝 | 团队负责人 | 需要填写拒绝原因 |
| Verified | Analyzing | 开始分析 | 漏洞工程师 | 自动通知分配人 |
| Analyzing | Analyzed | 分析完成 | 漏洞工程师 | 需要提交分析报告 |
| Analyzed | Reported | 报告完成 | 漏洞工程师 | 报告需包含POC |
| Reported | Closed | 归档关闭 | 团队负责人 | 完成归档 |
| Todo | InProgress | 开始处理 | 任务接收人 | 更新团队状态 |
| InProgress | Review | 提交审核 | 任务处理人 | 产出需可验证 |
| Review | Done | 审核通过 | 团队负责人 | 自动通知 |
| Draft | Review | 提交审核 | 文档作者 | 需指定审核人 |
| Review | Published | 审核通过 | 审核人 | 自动索引 |
