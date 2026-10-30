#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成验收报告 §2 用的鉴权矩阵 markdown 片段（依赖 scan-auth-*.py 的输出）。"""
import io
import json
import pathlib
import sys
from collections import Counter, defaultdict

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

_REPO = pathlib.Path(__file__).resolve().parents[2]
rows = json.load(open(str(_REPO / '.qa-tmp' / 'auth_final.json'), encoding='utf-8'))
WRITE = {'POST', 'PUT', 'PATCH', 'DELETE'}
SENSITIVE = ('export', 'download', 'users', 'permissions', 'roles', 'audit', 'statistics',
             '/me', 'claim', 'access-request', 'settings', 'recipients')

print('## 统计')
print('总端点', len(rows))
print('写端点', sum(1 for r in rows if r['verb'] in WRITE))
print('GET 端点', sum(1 for r in rows if r['verb'] == 'GET'))
print('分类', dict(Counter(r['final'] for r in rows)))
print('写端点分类', dict(Counter(r['final'] for r in rows if r['verb'] in WRITE)))
print()

by = defaultdict(list)
for r in rows:
    by[r['controller']].append(r)

print('## 2.2 分控制器明细（写 / 敏感读）')
for c in sorted(by):
    items = [r for r in by[c] if r['verb'] in WRITE or any(k in r['path'] for k in SENSITIVE)]
    if not items:
        continue
    print('### ' + c)
    print('| 方法 | 端点 | 校验 |')
    print('|---|---|---|')
    for r in items:
        print('| %s | `%s` | %s |' % (r['verb'], r['path'], r['final']))
    print()

print('## 2.3 无守卫端点（写 + 敏感读）')
for r in rows:
    if r['final'] in ('无', '仅登录(取当前用户)'):
        if r['verb'] in WRITE or any(k in r['path'] for k in SENSITIVE):
            print('%-26s %-6s %-56s %s' % (r['controller'], r['verb'], r['path'], r['final']))
