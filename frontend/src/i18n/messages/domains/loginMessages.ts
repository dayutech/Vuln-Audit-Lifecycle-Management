/**
 * 登录页文案（t95 角色主题 / t99 抽象极简主题 / t101 视觉重构）。
 *
 * 表单本身的 `login.title`、`login.username` 等既有词条仍在主词条表里，这里只补新增文案。
 * 主题相关文案（`login.theme.*`）被 `components/login/types.ts` 的
 * `LOGIN_THEME_LABEL_KEYS` / `LOGIN_THEME_SUBTITLE_KEYS` 以 `Record<LoginThemeId, MessageKey>`
 * 引用 —— 缺一条 tsc 直接报错，八套主题的文案不会漏。
 *
 * t101 起页面上只剩「主题 + 品牌区 + 登录框」：卖点（point.*）与鼠标提示（hint.*）已删除，
 * 品牌区只保留产品名与一句 slogan。
 */

export const loginZh = {
  'login.form.subtitle': '登录后继续',
  'login.form.error': '这次没成功，请检查账号或密码',
  'login.theme.label': '登录主题',
  // 分组标题（t97 的偏好弹窗按分组渲染）
  'login.theme.group.character': '角色',
  'login.theme.group.abstract': '极简',
  // t95：四套卡通角色
  'login.theme.pencil': '铅笔小人',
  'login.theme.pencil.subtitle': '手绘线条，敲密码时抬臂遮眼',
  'login.theme.geometric': '几何伙伴',
  'login.theme.geometric.subtitle': '圆头方身，敲密码时歪头让路',
  'login.theme.owl': '夜猫子',
  'login.theme.owl.subtitle': '大眼睛，敲密码时抬翅遮眼',
  'login.theme.pixelEyes': '像素脸',
  'login.theme.pixelEyes.subtitle': '像素方块，敲密码时把脸侧过去',
  // t99：四套抽象极简（无角色，铺满整页做背景层）
  'login.theme.aurora': '极简光晕',
  'login.theme.aurora.subtitle': '深墨底与缓慢漂移的柔光',
  'login.theme.monoGrid': '极简网格',
  'login.theme.monoGrid.subtitle': '低对比网格，光标处微微加深',
  'login.theme.orbit': '轨道',
  'login.theme.orbit.subtitle': '两个同心细环与一个缓慢运行的点',
  'login.theme.bigType': '大字排版',
  'login.theme.bigType.subtitle': '超大产品名，克制的文字视差',
  'login.theme.bigType.line1': '漏洞审计',
  'login.theme.bigType.line2': '平台',
} as const;

export const loginEn: Record<keyof typeof loginZh, string> = {
  'login.form.subtitle': 'Sign in to continue',
  'login.form.error': 'Sign-in failed — check your username or password',
  'login.theme.label': 'Login theme',
  'login.theme.group.character': 'Characters',
  'login.theme.group.abstract': 'Minimal',
  'login.theme.pencil': 'Pencil doodle',
  'login.theme.pencil.subtitle': 'Hand-drawn lines; raises an arm on password',
  'login.theme.geometric': 'Geometric buddy',
  'login.theme.geometric.subtitle': 'Square body, round head; tilts away on password',
  'login.theme.owl': 'Night owl',
  'login.theme.owl.subtitle': 'Big eyes; shields them with a wing on password',
  'login.theme.pixelEyes': 'Pixel face',
  'login.theme.pixelEyes.subtitle': 'Pixel blocks; turns its face away on password',
  'login.theme.aurora': 'Aurora',
  'login.theme.aurora.subtitle': 'Deep ink with slowly drifting soft light',
  'login.theme.monoGrid': 'Mono grid',
  'login.theme.monoGrid.subtitle': 'Low-contrast grid, gently deepened at the cursor',
  'login.theme.orbit': 'Orbit',
  'login.theme.orbit.subtitle': 'Two thin concentric rings and one slow dot',
  'login.theme.bigType': 'Big type',
  'login.theme.bigType.subtitle': 'Oversized wordmark with restrained parallax',
  'login.theme.bigType.line1': 'Vulnerability',
  'login.theme.bigType.line2': 'Audit',
};
