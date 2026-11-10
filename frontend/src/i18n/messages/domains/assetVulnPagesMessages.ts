/**
 * 资产 / 漏洞整页化（t168，需求 9 第 4 条）词条。
 *
 * 只补资产/漏洞「新建页 + 编辑页 + 详情页」新增用到的键，避免动共享大文件里他人的段落。
 * 既有 `asset.form.*`（title/required 等）仍在 zh-CN.ts / en-US.ts 内联段，本文件不重复。
 */
export const assetVulnPagesZh = {
  // 资产表单页
  'asset.form.loadFailed': '加载资产失败',
  'asset.form.saved': '资产已保存',
  'asset.form.created': '资产已创建',
  'asset.form.forbidden': '没有权限执行该操作',
  'asset.form.saveFailed': '保存资产失败',
  'asset.form.createSubtitle': '新建资产需同时选择客户与项目（后端 t69 口径）',
  'asset.form.editHint': '编码与归属关系不在此页修改；如需调整归属请在项目管理中操作',
  'asset.form.basicGroup': '基本信息',
  'asset.form.attributeGroup': '属性与描述',
  'asset.form.createNote': '创建后可在详情页维护产品概览、资产文件、交付物与挂靠关系',
  'asset.form.pageTitleNew': '新建资产',
  'asset.form.pageTitleEdit': '编辑资产',

  // 漏洞表单页 / 详情页
  'vuln.form.pageTitleNew': '新建漏洞',
  'vuln.form.pageTitleEdit': '编辑漏洞',
  'vuln.form.pageSubtitle': '填写基础信息；状态变更请在详情页走流程（只走 PATCH）',
  'vuln.form.basicGroup': '基本信息',
  'vuln.form.analysisGroup': '影响与验证',
  'vuln.form.assetRequired': '请选择资产',
  'vuln.form.versionHint': '选择资产版本后，漏洞会记录到该版本（t34）',
  'vuln.form.assetForbidden': '没有权限在该资产下创建漏洞',
  'vuln.form.loadFailed': '加载漏洞失败',
  'vuln.form.saved': '漏洞已保存',
  'vuln.form.created': '漏洞已创建',
  'vuln.form.saveFailed': '保存漏洞失败',
  'vuln.form.editHint': '状态（工作流/客户/披露）请在详情页操作，本页只更新内容',
  'vuln.detail.pageTitle': '漏洞详情',
  'vuln.detail.edit': '编辑',
  'vuln.detail.backToList': '返回列表',
  'vuln.detail.loadFailed': '加载漏洞详情失败',
  'vuln.list.redirectOpen': '已跳转到漏洞详情页',
  'vuln.list.redirectNew': '已跳转到新建漏洞页',
};

export const assetVulnPagesEn: Record<keyof typeof assetVulnPagesZh, string> = {
  'asset.form.loadFailed': 'Failed to load the asset',
  'asset.form.saved': 'Asset saved',
  'asset.form.created': 'Asset created',
  'asset.form.forbidden': 'You do not have permission for this action',
  'asset.form.saveFailed': 'Failed to save the asset',
  'asset.form.createSubtitle': 'Creating an asset requires both a customer and a project (t69)',
  'asset.form.editHint': 'Code and ownership are managed elsewhere; adjust ownership in Project management',
  'asset.form.basicGroup': 'Basic information',
  'asset.form.attributeGroup': 'Attributes and description',
  'asset.form.createNote': 'After creation, maintain the overview, files, deliverables and project links on the detail page',
  'asset.form.pageTitleNew': 'New asset',
  'asset.form.pageTitleEdit': 'Edit asset',

  'vuln.form.pageTitleNew': 'New vulnerability',
  'vuln.form.pageTitleEdit': 'Edit vulnerability',
  'vuln.form.pageSubtitle': 'Fill in the basics; status changes happen on the detail page (PATCH only)',
  'vuln.form.basicGroup': 'Basic information',
  'vuln.form.analysisGroup': 'Impact and validation',
  'vuln.form.assetRequired': 'Select an asset',
  'vuln.form.versionHint': 'Pick an asset version so the vulnerability is recorded against it (t34)',
  'vuln.form.assetForbidden': 'You cannot create vulnerabilities under this asset',
  'vuln.form.loadFailed': 'Failed to load the vulnerability',
  'vuln.form.saved': 'Vulnerability saved',
  'vuln.form.created': 'Vulnerability created',
  'vuln.form.saveFailed': 'Failed to save the vulnerability',
  'vuln.form.editHint': 'Workflow/customer/disclosure status is handled on the detail page; this page only updates content',
  'vuln.detail.pageTitle': 'Vulnerability details',
  'vuln.detail.edit': 'Edit',
  'vuln.detail.backToList': 'Back to list',
  'vuln.detail.loadFailed': 'Failed to load the vulnerability',
  'vuln.list.redirectOpen': 'Opening the vulnerability detail page',
  'vuln.list.redirectNew': 'Opening the new vulnerability page',
};

