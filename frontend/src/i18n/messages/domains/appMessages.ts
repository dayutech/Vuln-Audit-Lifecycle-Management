/**
 * 应用级文案（t89）：侧边栏品牌区等全局壳层文案。
 *
 * 与 assetMessages / vulnMessages / projectMessages 同一约定：按「域.语义」命名，
 * 中英各一份，由 zh-CN.ts / en-US.ts 展开合并。
 */

export const appZh = {
  'app.brand.name': '漏洞审计平台',
  'app.brand.nameFull': '漏洞审计生命周期管理系统',
} as const;

export const appEn: Record<keyof typeof appZh, string> = {
  'app.brand.name': 'Vuln Audit',
  'app.brand.nameFull': 'Vulnerability Audit Lifecycle Management',
};
