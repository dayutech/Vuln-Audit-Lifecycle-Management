/**
 * 应用级文案（t89 品牌区 / t108 更名「猫头鹰」）。
 *
 * 与 assetMessages / vulnMessages / projectMessages 同一约定：按「域.语义」命名，
 * 中英各一份，由 zh-CN.ts / en-US.ts 展开合并。
 *
 * t108 起平台更名为「猫头鹰漏洞审计生命周期管理系统」：
 * - `name`：全名（登录页品牌区、文档标题、折叠态 Tooltip 的兜底）；
 * - `nameShort`：窄空间用（侧边栏折叠 Tooltip、移动端品牌区、面包屑）；
 * - `slogan`：与「洞察」呼应的一句话（登录页品牌区）。
 */

export const appZh = {
  'app.brand.name': '猫头鹰漏洞审计生命周期管理系统',
  'app.brand.nameShort': '猫头鹰审计',
  'app.brand.nameFull': '猫头鹰漏洞审计生命周期管理系统',
  /** t122：侧边栏品牌两行（第一行产品名 / 第二行系统定位） */
  'app.brand.line1': '猫头鹰漏洞审计',
  'app.brand.line2': '生命周期管理系统',
  'app.brand.slogan': '洞察入微 · 审计可溯',
} as const;

export const appEn: Record<keyof typeof appZh, string> = {
  'app.brand.name': 'Owl Vulnerability Audit Lifecycle Management System',
  'app.brand.nameShort': 'Owl Audit',
  'app.brand.nameFull': 'Owl Vulnerability Audit Lifecycle Management System',
  'app.brand.line1': 'Owl Vulnerability Audit',
  'app.brand.line2': 'Lifecycle Management System',
  'app.brand.slogan': 'See deeper. Audit further.',
};
