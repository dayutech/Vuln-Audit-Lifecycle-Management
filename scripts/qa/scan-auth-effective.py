#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""对每个端点提取方法体，判断是否在方法内做了数据级/角色校验，输出最终鉴权分类。"""
import json
import os
import re

_REPO = __import__('pathlib').Path(__file__).resolve().parents[2]
BASE = str(_REPO / 'backend' / 'src' / 'main' / 'java' / 'com' / 'vulnaudit' / 'controller')
_OUT = _REPO / '.qa-tmp'
rows = json.load(open(str(_OUT / 'auth.json'), encoding='utf-8'))

MAP_RE = re.compile(r'@(Get|Post|Put|Patch|Delete)Mapping')
GUARD_DATA = re.compile(r'accessGuard\.(canRead|canWrite|visibleAssetIds|isManager|isGlobalReader)|requireCurrentUserId|canWriteAsset|canReadAsset|canWriteVulnerability|canReadVulnerability')
GUARD_LOGIN = re.compile(r'getCurrentUserId|currentUserProvider|SecurityContextHolder|currentVisibleAssets')
GUARD_ROLE = re.compile(r'hasRole|hasAnyRole|isAdminOrLeader|ROLE_ADMIN')

by_ctrl = {}
for r in rows:
    by_ctrl.setdefault(r['controller'], []).append(r)

out = []
for ctrl, items in by_ctrl.items():
    path = os.path.join(BASE, ctrl + '.java')
    lines = open(path, encoding='utf-8').read().split('\n')
    # 定位每个 mapping 的行号
    idxs = [i for i, l in enumerate(lines) if MAP_RE.search(l.strip())]
    for n, (r, start) in enumerate(zip(items, idxs)):
        end = idxs[n + 1] if n + 1 < len(idxs) else len(lines)
        body = '\n'.join(lines[start:end])
        in_body_data = bool(GUARD_DATA.search(body))
        in_body_login = bool(GUARD_LOGIN.search(body))
        in_body_role = bool(GUARD_ROLE.search(body))
        kind = r['kind']
        if kind == '数据级守卫':
            final = '数据级守卫'
        elif kind == '角色注解':
            final = '角色注解'
        elif in_body_data:
            final = '方法内数据级'
        elif in_body_role:
            final = '方法内角色'
        elif in_body_login:
            final = '仅登录(取当前用户)'
        else:
            final = '无'
        out.append({**r, 'final': final, 'in_body_login': in_body_login})

WRITE = {'POST', 'PUT', 'PATCH', 'DELETE'}
print('总端点数:', len(out))
from collections import Counter
print('最终分类:', dict(Counter(o['final'] for o in out)))
print()
print('== 写端点：既无注解也无方法内数据级/角色校验 ==')
n = 0
for o in out:
    if o['verb'] in WRITE and o['final'] == '无':
        n += 1
        print(f"  {o['controller']:28} {o['verb']:6} {o['path']:56} {o['handler']}")
print('  小计:', n)
print()
print('== 写端点：仅登录（方法内只取当前用户，无角色/数据级） ==')
for o in out:
    if o['verb'] in WRITE and o['final'] == '仅登录(取当前用户)':
        print(f"  {o['controller']:28} {o['verb']:6} {o['path']:56} {o['handler']}")
print()
print('== 写端点：方法内数据级 ==')
for o in out:
    if o['verb'] in WRITE and o['final'] == '方法内数据级':
        print(f"  {o['controller']:28} {o['verb']:6} {o['path']:56} {o['handler']}")
print()
print('== 读端点：无任何校验（含公开接口） ==')
for o in out:
    if o['verb'] == 'GET' and o['final'] == '无':
        print(f"  {o['controller']:28} {o['verb']:6} {o['path']:56} {o['handler']}")
json.dump(out, open(str(_OUT / 'auth_final.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
