# QA 鉴权矩阵扫描脚本（t14 验收报告 §2 的依据）
#
# 用法（仓库根目录执行）：
#   python3 scripts/qa/scan-auth-annotations.py    # 输出 backend/.tmp auth.json（端点 + 注解级校验）
#   python3 scripts/qa/scan-auth-effective.py      # 打印：角色注解 / 数据级守卫 / 仅登录 / 无 的最终分类与清单
#
# 说明：脚本按字符串规则解析 controller 源码，输出仅用于人工复核；不参与构建。
