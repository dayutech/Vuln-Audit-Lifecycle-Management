#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""扫描所有 Controller，输出「端点 × 鉴权」矩阵（t14 验收报告用）。

判定口径：
- 角色注解：@PreAuthorize 里出现 hasRole / hasAnyRole
- 数据级守卫：@PreAuthorize 里出现 @accessGuard.*（资产/漏洞/文档/任务等归属校验）
- 无：既无角色注解也无数据级守卫（写/敏感读端点需在报告中单独列出）
"""
import json
import os
import re
import sys

_REPO = __import__('pathlib').Path(__file__).resolve().parents[2]
CTRL_DIR = str(_REPO / 'backend' / 'src' / 'main' / 'java' / 'com' / 'vulnaudit' / 'controller')
_OUT = _REPO / '.qa-tmp'
_OUT.mkdir(exist_ok=True)

MAP_RE = re.compile(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\((.*?)\))?\s*$')
CLS_PRE = re.compile(r'@PreAuthorize\((.*)\)\s*$')
PRE_METHOD = re.compile(r'@PreAuthorize\((.*)\)')
MAPPING_ANY = re.compile(r'@(Get|Post|Put|Patch|Delete)Mapping')


def split_pre(pre: str):
    """把 @PreAuthorize 表达式归类。"""
    if not pre:
        return "无", ""
    if "accessGuard" in pre:
        return "数据级守卫", pre.strip()
    if "hasRole" in pre or "hasAnyRole" in pre or "hasAuthority" in pre:
        return "角色注解", pre.strip()
    return "自定义/无", pre.strip()


def parse_file(path):
    with open(path, encoding="utf-8") as f:
        lines = f.read().split("\n")
    class_pre = ""
    class_base = ""
    for ln in lines:
        s = ln.strip()
        if s.startswith("@RequestMapping(") and class_base == "":
            m = re.search(r'"([^"]*)"', s)
            class_base = m.group(1) if m else ""
        if s.startswith("@PreAuthorize(") and class_pre == "":
            # 类级注解通常出现在 class 声明之前
            class_pre = s
    # 类级注解只取第一次出现且位于 class 声明之前的那一处
    class_idx = next((i for i, l in enumerate(lines) if re.search(r'\bclass\s+\w+', l)), len(lines))
    class_pre = ""
    for ln in lines[:class_idx]:
        s = ln.strip()
        if s.startswith("@PreAuthorize("):
            class_pre = s
    rows = []
    for i, ln in enumerate(lines):
        s = ln.strip()
        m = MAP_RE.match(s)
        if not m:
            continue
        verb = m.group(1)
        arg = m.group(2) or ""
        pm = re.search(r'"([^"]*)"', arg)
        sub = pm.group(1) if pm else ""
        # 找方法签名（下一个含 public/private/protected 且含 '(' 的行）
        handler = ""
        j = i + 1
        body_pre = ""
        while j < len(lines):
            t = lines[j].strip()
            if t.startswith("@PreAuthorize("):
                body_pre = t
            mm = re.search(r'\b(?:public|private|protected)\s+[\w<>\[\], .]+\s+(\w+)\s*\(', t)
            if mm:
                handler = mm.group(1)
                break
            if t.startswith("@") and not t.startswith("@Operation") and not t.startswith("@Parameter"):
                pass
            j += 1
        pre = body_pre or class_pre
        kind, expr = split_pre(pre)
        rows.append({
            "controller": os.path.basename(path).replace(".java", ""),
            "verb": verb.upper(),
            "path": (class_base or "") + sub,
            "handler": handler,
            "class_pre": class_pre,
            "method_pre": body_pre,
            "kind": kind,
            "expr": expr,
        })
    return rows


def main():
    all_rows = []
    for name in sorted(os.listdir(CTRL_DIR)):
        if not name.endswith("Controller.java"):
            continue
        all_rows.extend(parse_file(os.path.join(CTRL_DIR, name)))
    with open(str(_OUT / "auth.json"), "w", encoding="utf-8") as f:
        json.dump(all_rows, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
