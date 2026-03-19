# 系统用例图设计

## 1. 系统总体用例图

```mermaid
graph TD
    subgraph Actor["参与者"]
        Admin["系统管理员"]
        TeamLeader["团队负责人"]
        Engineer["漏洞工程师"]
        Auditor["审计人员"]
        NewMember["新成员"]
    end

    subgraph Asset_Module["目标资产管理"]
        UC1["创建资产"]
        UC2["查询资产"]
        UC3["更新资产"]
        UC4["删除资产"]
        UC5["资产分类管理"]
        UC6["查看资产历史"]
    end

    subgraph Env_Module["环境管理"]
        UC7["创建环境"]
        UC8["环境快照"]
        UC9["环境部署"]
        UC10["环境监控"]
        UC11["模板管理"]
    end

    subgraph Doc_Module["文档管理"]
        UC12["创建文档"]
        UC13["文档版本管理"]
        UC14["文档检索"]
        UC15["申请查看文档"]
        UC16["审批文档访问"]
        UC17["查看共享文档"]
    end

    subgraph Vuln_Module["漏洞管理"]
        UC16["创建漏洞"]
        UC17["漏洞验证"]
        UC18["漏洞利用分析"]
        UC19["漏洞报告"]
        UC20["漏洞跟踪"]
    end

    subgraph Tool_Module["工具管理"]
        UC21["上传工具"]
        UC22["工具检索"]
        UC23["工具下载"]
        UC24["工具版本管理"]
    end

    subgraph Team_Module["团队协作"]
        UC25["创建任务"]
        UC26["任务分配"]
        UC27["进度查看"]
        UC28["工作状态查看"]
        UC29["重复检测"]
    end

    subgraph Auth_Module["权限管理"]
        UC30["角色管理"]
        UC31["权限分配"]
        UC32["操作审计"]
        UC33["数据备份"]
    end

    subgraph Knowledge_Module["知识库"]
        UC34["创建知识文章"]
        UC35["知识检索"]
        UC36["知识评论"]
    end

    Admin --> Asset_Module
    Admin --> Env_Module
    Admin --> Doc_Module
    Admin --> Vuln_Module
    Admin --> Tool_Module
    Admin --> Team_Module
    Admin --> Auth_Module
    Admin --> Knowledge_Module

    TeamLeader --> Asset_Module
    TeamLeader --> Env_Module
    TeamLeader --> Doc_Module
    TeamLeader --> Vuln_Module
    TeamLeader --> Tool_Module
    TeamLeader --> Team_Module
    TeamLeader --> Knowledge_Module

    Engineer --> Asset_Module
    Engineer --> Env_Module
    Engineer --> Doc_Module
    Engineer --> Vuln_Module
    Engineer --> Tool_Module
    Engineer --> Team_Module
    Engineer --> Knowledge_Module

    Auditor --> Asset_Module
    Auditor --> Doc_Module
    Auditor --> Vuln_Module
    Auditor --> Auth_Module

    NewMember --> Asset_Module
    NewMember --> Env_Module
    NewMember --> Doc_Module
    NewMember --> Tool_Module
    NewMember --> Knowledge_Module
```

## 2. 目标资产管理用例图

```mermaid
graph TD
    Actor["漏洞工程师/团队负责人"] --> UC1["创建资产"]
    Actor --> UC2["查询资产"]
    Actor --> UC3["更新资产"]
    Actor --> UC4["删除资产"]
    Actor --> UC5["资产分类管理"]
    Actor --> UC6["查看资产历史"]
    Actor --> UC7["关联漏洞查看"]
    Actor --> UC8["申请访问资产"]
    Actor --> UC9["审批资产访问"]
    Actor --> UC10["查看认领列表"]
    Actor --> UC11["上传资产文件"]
    Actor --> UC12["下载资产文件"]
    Actor --> UC13["管理文件版本"]
    Actor --> UC14["删除资产文件"]
    
    UC1 --> DB["资产数据库"]
    UC2 --> DB
    UC3 --> DB
    UC4 --> DB
    UC5 --> DB
    UC6 --> DB
    UC7 --> DB
    UC8 --> DB
    UC9 --> DB
    UC10 --> DB
    UC11 --> DB
    UC12 --> DB
    UC13 --> DB
    UC14 --> DB
    UC15 --> DB
    
    UC1 --> Validate["数据验证"]
    UC2 --> Search["全文检索"]
    UC3 --> Validate
    UC6 --> History["历史记录服务"]
    UC8 --> AccessControl["访问控制服务"]
    UC11 --> FileService["文件服务(本地存储，预留对象存储接口)"]
    UC12 --> FileService
    UC13 --> FileService
    UC14 --> FileService
```

## 3. 环境管理用例图

> **说明**：本模块为靶场环境管理功能，相关用例暂保持为空实现，待后续需求明确后再行开发。

```mermaid
graph TD
    subgraph Actor["参与者"]
        Engineer["漏洞工程师"]
        Leader["团队负责人"]
    end

    subgraph Use_Cases["用例"]
        UC1["创建环境"]
        UC2["环境快照"]
        UC3["环境回滚"]
        UC4["环境部署"]
        UC5["环境监控"]
        UC6["模板管理"]
        UC7["配置版本管理"]
    end
    
    Engineer --> UC1
    Engineer --> UC2
    Engineer --> UC3
    Engineer --> UC4
    Engineer --> UC5
    Engineer --> UC6
    Engineer --> UC7
    
    Leader --> UC1
    Leader --> UC2
    Leader --> UC4
    Leader --> UC6
    
    UC1 --> Docker["Docker服务"]
    UC2 --> Docker
    UC3 --> Docker
    UC4 --> Docker
    UC5 --> Monitor["监控服务"]
    UC6 --> Template["模板存储"]
    UC7 --> Version["版本控制"]
```

## 4. 漏洞生命周期管理用例图

```mermaid
graph TD
    subgraph Actor["参与者"]
        Engineer["漏洞工程师"]
        Leader["团队负责人"]
    end

    subgraph Discovery["发现阶段"]
        UC1["提交漏洞"]
        UC2["初步审核"]
    end

    subgraph Verification["验证阶段"]
        UC3["漏洞验证"]
        UC4["影响范围评估"]
    end

    subgraph Analysis["分析阶段"]
        UC5["漏洞利用分析"]
        UC6["编写POC"]
        UC7["编写利用代码"]
    end

    subgraph Report["报告阶段"]
        UC8["编写报告"]
        UC9["报告审核"]
        UC10["报告发布"]
    end

    subgraph Close["关闭阶段"]
        UC11["漏洞归档"]
        UC12["经验总结"]
    end
    
    Engineer --> UC1
    Engineer --> UC3
    Engineer --> UC5
    Engineer --> UC6
    Engineer --> UC7
    Engineer --> UC8
    Engineer --> UC11
    Engineer --> UC13
    
    Leader --> UC2
    Leader --> UC4
    Leader --> UC9
    Leader --> UC10
    Leader --> UC12
    Leader --> UC14
    
    UC1 --> UC2
    UC2 --> UC3
    UC3 --> UC5
    UC5 --> UC6
    UC6 --> UC7
    UC7 --> UC8
    UC8 --> UC9
    UC9 --> UC10
    UC10 --> UC11
    UC11 --> UC12
    UC12 --> UC13
    UC13 --> UC14
```

## 5. 工具资源库用例图

```mermaid
graph TD
    subgraph Actor["参与者"]
        Engineer["漏洞工程师"]
        Admin["管理员"]
    end

    subgraph Tool_Management["工具管理"]
        UC1["上传工具"]
        UC2["工具分类"]
        UC3["工具检索"]
        UC4["工具下载"]
        UC5["工具版本管理"]
        UC6["工具评论"]
        UC7["工具下架"]
    end
    
    Engineer --> UC1
    Engineer --> UC2
    Engineer --> UC3
    Engineer --> UC4
    Engineer --> UC6
    
    Admin --> UC1
    Admin --> UC5
    Admin --> UC7
    
    UC1 --> FileStore["文件存储(本地，预留对象存储接口)"]
    UC3 --> Search["搜索服务"]
    UC4 --> FileStore
    UC5 --> Version["版本控制"]
```

## 6. 团队协作用例图

```mermaid
graph TD
    subgraph Actor["参与者"]
        Engineer["漏洞工程师"]
        Leader["团队负责人"]
    end

    subgraph Task_Management["任务管理"]
        UC1["创建任务"]
        UC2["分配任务"]
        UC3["任务认领"]
        UC4["更新进度"]
        UC5["任务评论"]
        UC6["任务关闭"]
    end

    subgraph Progress["进度管理"]
        UC7["进度看板"]
        UC8["工作统计"]
        UC9["重复检测"]
    end

    subgraph Communication["沟通协作"]
        UC10["状态更新"]
        UC11["通知提醒"]
    end
    
    Engineer --> UC1
    Engineer --> UC3
    Engineer --> UC4
    Engineer --> UC5
    Engineer --> UC6
    Engineer --> UC7
    Engineer --> UC10
    
    Leader --> UC1
    Leader --> UC2
    Leader --> UC7
    Leader --> UC8
    Leader --> UC9
    Leader --> UC11
    
    UC1 --> TaskDB["任务数据库"]
    UC2 --> TaskDB
    UC3 --> TaskDB
    UC4 --> TaskDB
    UC5 --> TaskDB
    UC6 --> TaskDB
    UC7 --> TaskDB
    UC8 --> Statistics["统计服务"]
    UC9 --> Dedup["去重检测服务"]
    UC10 --> Status["状态服务"]
    UC11 --> Notify["通知服务"]
```

## 7. 权限管理用例图

```mermaid
graph TD
    subgraph Actor["参与者"]
        Admin["系统管理员"]
    end

    subgraph User_Management["用户管理"]
        UC1["用户创建"]
        UC2["用户禁用"]
        UC3["用户信息更新"]
    end

    subgraph Role_Management["角色管理"]
        UC4["角色创建"]
        UC5["角色权限配置"]
        UC6["角色分配"]
        UC7["角色删除"]
    end

    subgraph Security["安全管理"]
        UC8["操作审计"]
        UC9["数据备份"]
        UC10["权限验证"]
    end
    
    Admin --> UC1
    Admin --> UC2
    Admin --> UC3
    Admin --> UC4
    Admin --> UC5
    Admin --> UC6
    Admin --> UC7
    Admin --> UC8
    Admin --> UC9
    
    UC1 --> UserDB["用户数据库"]
    UC2 --> UserDB
    UC3 --> UserDB
    UC4 --> RoleDB["角色数据库"]
    UC5 --> RoleDB
    UC6 --> RoleDB
    UC7 --> RoleDB
    UC8 --> Audit["审计日志"]
    UC9 --> Backup["备份服务"]
    UC10 --> Auth["认证服务"]
```

## 8. 知识库用例图

```mermaid
graph TD
    subgraph Actor["参与者"]
        Engineer["漏洞工程师"]
        NewMember["新成员"]
    end

    subgraph Knowledge_Management["知识管理"]
        UC1["创建知识文章"]
        UC2["编辑知识文章"]
        UC3["文章审核"]
        UC4["文章检索"]
        UC5["文章评论"]
        UC6["文章收藏"]
    end

    subgraph Learning["学习成长"]
        UC7["新手入门"]
        UC8["技能树"]
        UC9["在线问答"]
    end
    
    Engineer --> UC1
    Engineer --> UC2
    Engineer --> UC3
    Engineer --> UC4
    Engineer --> UC5
    
    NewMember --> UC4
    NewMember --> UC7
    NewMember --> UC8
    NewMember --> UC9
    
    UC1 --> KB["知识库"]
    UC2 --> KB
    UC3 --> KB
    UC4 --> Search["搜索服务"]
    UC5 --> KB
    UC7 --> Guide["入门指南"]
    UC8 --> SkillTree["技能树"]
    UC9 --> QNA["问答系统"]
```

## 9. 用例说明表

| 用例编号 | 用例名称 | 参与者 | 描述 |
|----------|----------|--------|------|
| UC-ASSET-001 | 创建资产 | 漏洞工程师 | 创建新的目标资产记录 |
| UC-ASSET-002 | 查询资产 | 所有用户 | 根据条件查询资产信息 |
| UC-ASSET-003 | 申请访问资产 | 安全工程师 | 申请访问某资产，部门领导控制可查看的内容范围 |
| UC-ASSET-004 | 审批资产访问 | 团队负责人/部门领导 | 审批安全工程师的资产访问申请，控制可查看的内容 |
| UC-ASSET-005 | 查看认领列表 | 安全工程师/团队负责人 | 查看资产的认领情况或个人认领记录 |
| UC-ASSET-006 | 上传资产文件 | 漏洞工程师 | 上传目标系统的ISO镜像、VMDK、安装包等文件 |
| UC-ASSET-007 | 下载资产文件 | 漏洞工程师 | 下载已授权的资产文件进行漏洞分析 |
| UC-ASSET-008 | 管理文件版本 | 漏洞工程师 | 上传新版本文件并保留历史版本 |
| UC-ASSET-009 | 删除资产文件 | 漏洞工程师 | 删除资产关联的文件 |
| UC-ASSET-010 | 查看应用元数据 | 认领工程师 | 查看资产的应用元数据信息（当前版本） |
| UC-ASSET-011 | 填写应用元数据 | 认领工程师 | 首次填写应用元数据（version=1） |
| UC-ASSET-012 | 更新应用元数据 | 认领工程师 | 更新元数据并保留历史，每次必须填写修改理由 |
| UC-ASSET-013 | 查看历史版本 | 认领工程师 | 查看应用元数据的所有历史版本列表 |
| UC-ASSET-014 | 查看版本详情 | 认领工程师 | 查看指定历史版本的完整信息 |
| UC-ENV-001 | 创建环境 | 漏洞工程师 | 基于模板创建新环境 |
| UC-ENV-002 | 环境快照 | 漏洞工程师 | 保存当前环境配置快照 |
| UC-DOC-001 | 在线创建文档 | 漏洞工程师 | 在线创建新的挖掘文档，选择类型和关联资产 |
| UC-DOC-002 | 在线编辑文档 | 漏洞工程师 | 富文本编辑器在线编辑文档内容 |
| UC-DOC-003 | 在线查看文档 | 漏洞工程师 | 在线查看文档内容，支持渲染富文本 |
| UC-DOC-004 | 删除文档 | 漏洞工程师 | 删除自己创建的文档 |
| UC-DOC-005 | 文档版本管理 | 漏洞工程师 | 管理文档的历史版本，支持回滚 |
| UC-DOC-006 | 发布/取消发布 | 漏洞工程师 | 将文档发布或取消发布 |
| UC-DOC-007 | 搜索文档 | 漏洞工程师 | 全文检索文档内容 |
| UC-VULN-001 | 提交漏洞 | 漏洞工程师 | 提交新发现的漏洞 |
| UC-VULN-002 | 漏洞验证 | 漏洞工程师 | 验证漏洞真实性和影响范围 |
| UC-TOOL-001 | 上传工具 | 漏洞工程师 | 上传新的工具或POC |
| UC-TOOL-002 | 工具检索 | 漏洞工程师 | 根据条件检索工具 |
| UC-TASK-001 | 创建任务 | 团队负责人 | 创建新的工作任务 |
| UC-TASK-002 | 任务分配 | 团队负责人 | 将任务分配给成员 |
| UC-AUTH-001 | 管理权限 | 管理员 | 创建、编辑、删除API权限 |
| UC-AUTH-002 | 管理角色 | 管理员 | 创建、编辑、删除角色，分配权限 |
| UC-AUTH-003 | 分配角色 | 管理员 | 为用户分配角色，支持多角色 |
| UC-AUTH-004 | 直接授权 | 管理员 | 直接分配特定权限给用户（可设置过期时间） |
| UC-AUTH-005 | 查看权限 | 所有用户 | 查看当前用户拥有的权限 |
| UC-AUTH-006 | 管理团队 | 管理员/项目负责人 | 创建、编辑、删除团队，管理团队成员 |
| UC-KNOW-001 | 创建文章 | 漏洞工程师 | 编写知识分享文章 |
| UC-KNOW-002 | 知识检索 | 所有用户 | 检索知识库内容 |
