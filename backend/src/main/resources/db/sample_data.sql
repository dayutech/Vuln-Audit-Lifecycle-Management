SET @now = NOW();

INSERT INTO roles (id, name, code, description, is_system, created_at, updated_at) VALUES
('11111111-1111-1111-1111-111111111111', '系统管理员', 'admin', '系统管理角色', TRUE, @now, @now),
('22222222-2222-2222-2222-222222222222', '项目负责人', 'project_leader', '项目管理角色', TRUE, @now, @now),
('33333333-3333-3333-3333-333333333333', '漏洞挖掘工程师', 'vulnerability_engineer', '漏洞挖掘角色', TRUE, @now, @now)
ON DUPLICATE KEY UPDATE
name = VALUES(name),
description = VALUES(description),
is_system = VALUES(is_system),
updated_at = VALUES(updated_at);

INSERT INTO users (id, username, email, phone, password_hash, full_name, avatar, position, status, created_at, updated_at, last_login) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'admin', 'admin@vulnaudit.com', '13800000001', '$2b$12$51UFqyiZs/PmvPpD70whH.9wvix.P4Y1b900Ol8vY9fiK92CfAzDa', '系统管理员', NULL, '安全负责人', 'active', @now, @now, NULL),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'leader.demo', 'leader.demo@vulnaudit.com', '13800000002', '$2b$12$51UFqyiZs/PmvPpD70whH.9wvix.P4Y1b900Ol8vY9fiK92CfAzDa', '演示组长', NULL, '项目负责人', 'active', @now, @now, NULL),
('cccccccc-cccc-cccc-cccc-cccccccccccc', 'engineer.demo', 'engineer.demo@vulnaudit.com', '13800000003', '$2b$12$51UFqyiZs/PmvPpD70whH.9wvix.P4Y1b900Ol8vY9fiK92CfAzDa', '演示工程师', NULL, '漏洞挖掘工程师', 'active', @now, @now, NULL)
ON DUPLICATE KEY UPDATE
email = VALUES(email),
phone = VALUES(phone),
password_hash = VALUES(password_hash),
full_name = VALUES(full_name),
position = VALUES(position),
status = VALUES(status),
updated_at = VALUES(updated_at);

INSERT INTO user_roles (id, user_id, role_id, assigned_by, assigned_at) VALUES
('44444444-4444-4444-4444-444444444441', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', @now),
('44444444-4444-4444-4444-444444444442', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', @now),
('44444444-4444-4444-4444-444444444443', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '33333333-3333-3333-3333-333333333333', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', @now)
ON DUPLICATE KEY UPDATE
assigned_by = VALUES(assigned_by),
assigned_at = VALUES(assigned_at);

INSERT INTO teams (id, name, parent_id, description, leader_id, created_by, created_at, updated_at) VALUES
('55555555-5555-5555-5555-555555555551', '攻防实验室', NULL, '漏洞挖掘与攻防演练团队', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', @now, @now)
ON DUPLICATE KEY UPDATE
name = VALUES(name),
description = VALUES(description),
leader_id = VALUES(leader_id),
created_by = VALUES(created_by),
updated_at = VALUES(updated_at);

INSERT INTO team_members (id, user_id, team_id, is_leader, status, joined_at) VALUES
('66666666-6666-6666-6666-666666666661', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '55555555-5555-5555-5555-555555555551', TRUE, 'available', @now),
('66666666-6666-6666-6666-666666666662', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '55555555-5555-5555-5555-555555555551', FALSE, 'available', @now)
ON DUPLICATE KEY UPDATE
is_leader = VALUES(is_leader),
status = VALUES(status),
joined_at = VALUES(joined_at);

INSERT INTO assets (id, name, code, team_id, target_type, target_source, description, priority, status, owner_id, created_at, updated_at) VALUES
('77777777-7777-7777-7777-777777777771', '示例电商主站', 'DEMO-ASSET-001', '55555555-5555-5555-5555-555555555551', 'web', 'customer', '用于演示漏洞管理流程的电商业务系统', 'P1', 'active', 'cccccccc-cccc-cccc-cccc-cccccccccccc', @now, @now),
('77777777-7777-7777-7777-777777777772', '示例支付网关', 'DEMO-ASSET-002', '55555555-5555-5555-5555-555555555551', 'api', 'internal', '用于演示高优先级漏洞处理的支付接口', 'P0', 'under_test', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', @now, @now)
ON DUPLICATE KEY UPDATE
name = VALUES(name),
team_id = VALUES(team_id),
target_type = VALUES(target_type),
target_source = VALUES(target_source),
description = VALUES(description),
priority = VALUES(priority),
status = VALUES(status),
owner_id = VALUES(owner_id),
updated_at = VALUES(updated_at);

INSERT INTO vulnerabilities (id, title, asset_id, cve_id, type, severity, cvss_score, workflow_status, customer_status, disclosure_status, customer_confirmed_at, customer_rejected_at, customer_reject_reason, exploit_stage, exploit_code, field_test_status, field_test_result, delivered_at, delivery_note, disclosure_date, disclosure_source, vendor_fixed, vendor_fixed_at, vendor_fix_version, description, detail, impact, poc_code, discoverer_id, assigned_to, created_at, updated_at) VALUES
('88888888-8888-8888-8888-888888888881', '支付回调签名校验绕过', '77777777-7777-7777-7777-777777777772', NULL, '业务逻辑', 'critical', 9.1, 'analyzing', 'pending', 'undisclosed', NULL, NULL, NULL, '探测', NULL, '待测试', NULL, NULL, NULL, NULL, NULL, FALSE, NULL, NULL, '攻击者可伪造支付成功回调导致越权记账', '回调接口未强制验证签名字段，存在条件分支绕过。', '可能导致财务数据失真与资金损失', 'POST /callback 携带空签名字段仍被接受', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'cccccccc-cccc-cccc-cccc-cccccccccccc', @now, @now),
('88888888-8888-8888-8888-888888888882', '商品搜索XSS', '77777777-7777-7777-7777-777777777771', NULL, 'XSS', 'high', 7.4, 'verified', 'confirmed', 'undisclosed', @now, NULL, NULL, '突破', '<script>alert(1)</script>', '已通过', '在测试环境可稳定触发弹窗', NULL, NULL, NULL, NULL, FALSE, NULL, NULL, '搜索参数回显未转义', '关键词参数进入模板时未经过输出编码。', '可导致会话劫持与页面篡改', '搜索 q=<script>alert(1)</script>', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', @now, @now)
ON DUPLICATE KEY UPDATE
title = VALUES(title),
asset_id = VALUES(asset_id),
type = VALUES(type),
severity = VALUES(severity),
cvss_score = VALUES(cvss_score),
workflow_status = VALUES(workflow_status),
customer_status = VALUES(customer_status),
disclosure_status = VALUES(disclosure_status),
exploit_stage = VALUES(exploit_stage),
field_test_status = VALUES(field_test_status),
vendor_fixed = VALUES(vendor_fixed),
description = VALUES(description),
detail = VALUES(detail),
impact = VALUES(impact),
poc_code = VALUES(poc_code),
discoverer_id = VALUES(discoverer_id),
assigned_to = VALUES(assigned_to),
updated_at = VALUES(updated_at);

INSERT INTO tasks (id, title, description, asset_id, vulnerability_id, reference_id, assigned_to, assigned_by, status, priority, type, due_date, completed_at, created_at, updated_at) VALUES
('99999999-9999-9999-9999-999999999991', '复测支付回调漏洞', '根据修复方案进行回归验证并更新状态', '77777777-7777-7777-7777-777777777772', '88888888-8888-8888-8888-888888888881', '88888888-8888-8888-8888-888888888881', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'in_progress', 'P0', 'vulnerability', DATE_ADD(@now, INTERVAL 3 DAY), NULL, @now, @now),
('99999999-9999-9999-9999-999999999992', '输出XSS修复建议文档', '整理可落地防护策略并同步给研发', '77777777-7777-7777-7777-777777777771', '88888888-8888-8888-8888-888888888882', '88888888-8888-8888-8888-888888888882', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'pending', 'P1', 'document', DATE_ADD(@now, INTERVAL 5 DAY), NULL, @now, @now)
ON DUPLICATE KEY UPDATE
title = VALUES(title),
description = VALUES(description),
assigned_to = VALUES(assigned_to),
assigned_by = VALUES(assigned_by),
status = VALUES(status),
priority = VALUES(priority),
type = VALUES(type),
due_date = VALUES(due_date),
updated_at = VALUES(updated_at);

INSERT INTO knowledge_articles (id, title, category, summary, content, tags, author_id, status, view_count, created_at, updated_at) VALUES
('aaaaaaaa-1111-1111-1111-111111111111', '支付回调安全校验实践', '漏洞原理', '总结支付回调签名校验的常见错误与修复策略', '建议统一使用时间戳+随机数+HMAC方案，并在网关层做重放拦截。', '支付,签名,鉴权', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'published', 18, @now, @now),
('aaaaaaaa-2222-2222-2222-222222222222', 'XSS 输出编码清单', '测试方法', '前后端输出编码策略速查表', '按上下文选择HTML/JS/URL编码，避免混用。', 'XSS,编码,前端安全', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'published', 25, @now, @now)
ON DUPLICATE KEY UPDATE
title = VALUES(title),
summary = VALUES(summary),
content = VALUES(content),
tags = VALUES(tags),
author_id = VALUES(author_id),
status = VALUES(status),
view_count = VALUES(view_count),
updated_at = VALUES(updated_at);

INSERT INTO notifications (id, title, content, type, priority, sender_id, related_type, related_id, created_at) VALUES
('bbbbbbbb-1111-1111-1111-111111111111', '新高危漏洞已指派', '支付回调签名校验绕过漏洞已创建并指派给你处理。', 'task_assigned', 'high', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'vulnerability', '88888888-8888-8888-8888-888888888881', @now),
('bbbbbbbb-2222-2222-2222-222222222222', '漏洞状态已更新', '商品搜索XSS 已完成验证并进入客户确认。', 'workflow_changed', 'normal', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'vulnerability', '88888888-8888-8888-8888-888888888882', @now)
ON DUPLICATE KEY UPDATE
title = VALUES(title),
content = VALUES(content),
type = VALUES(type),
priority = VALUES(priority),
sender_id = VALUES(sender_id),
related_type = VALUES(related_type),
related_id = VALUES(related_id);

INSERT INTO notification_recipients (id, notification_id, user_id, is_read, read_at, created_at) VALUES
('cccccccc-1111-1111-1111-111111111111', 'bbbbbbbb-1111-1111-1111-111111111111', 'cccccccc-cccc-cccc-cccc-cccccccccccc', FALSE, NULL, @now),
('cccccccc-2222-2222-2222-222222222222', 'bbbbbbbb-2222-2222-2222-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', TRUE, @now, @now)
ON DUPLICATE KEY UPDATE
is_read = VALUES(is_read),
read_at = VALUES(read_at);

SELECT 'sample_data_loaded' AS result;
