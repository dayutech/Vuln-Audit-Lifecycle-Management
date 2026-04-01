-- 漏洞审计生命周期管理系统数据库初始化脚本
-- 创建数据库
CREATE DATABASE IF NOT EXISTS vuln_audit DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE vuln_audit;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id UUID NOT NULL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    avatar VARCHAR(500),
    position VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 角色表
CREATE TABLE IF NOT EXISTS roles (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 权限表
CREATE TABLE IF NOT EXISTS permissions (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    api_path VARCHAR(200) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS user_roles (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_by UUID NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 角色权限关联表
CREATE TABLE IF NOT EXISTS role_permissions (
    id UUID NOT NULL PRIMARY KEY,
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_permission (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户权限关联表
CREATE TABLE IF NOT EXISTS user_permissions (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    granted_by UUID NOT NULL,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    UNIQUE KEY uk_user_permission (user_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 团队表
CREATE TABLE IF NOT EXISTS teams (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id UUID,
    description TEXT,
    leader_id UUID NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 团队成员关联表
CREATE TABLE IF NOT EXISTS team_members (
    id UUID NOT NULL PRIMARY KEY,
    user_id UUID NOT NULL,
    team_id UUID NOT NULL,
    is_leader BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'available',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_team_user (team_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入默认角色
INSERT INTO roles (id, name, code, description, is_system) VALUES
    (UUID(), '系统管理员', 'admin', '系统最高权限管理员', TRUE),
    (UUID(), '项目负责人', 'project_leader', '项目负责人，管理项目下所有资产和团队', TRUE),
    (UUID(), '小组长', 'team_leader', '团队小组长，管理团队成员和分配任务', TRUE),
    (UUID(), '漏洞挖掘工程师', 'vulnerability_engineer', '漏洞挖掘工程师，新成员默认角色', TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 插入默认管理员用户 (密码: Admin@123)
-- 注意：实际部署时应该修改密码
INSERT INTO users (id, username, email, password_hash, full_name, status) VALUES
    (UUID(), 'admin', 'admin@vulnaudit.com', '$2a$10$y4gTlQLF9rlGK1T8vEdoQuvzyVMqdxP31haV9oxrg8kLbJ4yy7uGC', '系统管理员', 'active')
ON DUPLICATE KEY UPDATE username = VALUES(username);
