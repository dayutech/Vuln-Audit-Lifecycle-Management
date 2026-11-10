/**
 * 资产文件 / 交付物 上传弹框与列表筛选文案（t146）。
 *
 * 与其它域同一约定：中英各一份，缺键即 tsc 报错。
 */
export const assetFileZh = {
  // ---------- 上传弹框（资产文件）----------
  'assetFile.upload.title': '上传资产文件',
  'assetFile.upload.open': '上传文件',
  'assetFile.upload.submit': '上传',
  'assetFile.upload.fileLabel': '文件',
  'assetFile.upload.dragHint': '点击或拖拽文件到此区域上传',
  'assetFile.upload.dragSubHint': '单次只上传一个文件；文件名取自文件本身',
  'assetFile.upload.version': '版本',
  'assetFile.upload.versionRequired': '请选择文件所属版本',
  'assetFile.upload.versionPlaceholder': '选择版本',
  'assetFile.upload.category': '分类',
  'assetFile.upload.categoryRequired': '请选择文件分类',
  'assetFile.upload.description': '描述',
  'assetFile.upload.remark': '备注（可选）',
  'assetFile.upload.remarkHint': '备注是独立字段，会单独展示在列表的「备注」列',
  'assetFile.upload.noFile': '请先选择要上传的文件',
  'assetFile.upload.uploading': '上传中',
  'assetFile.upload.acceptedHint': '上传成功后列表会自动刷新；失败时弹框保留已填内容。',
  'assetFile.upload.success': '文件上传成功',
  'assetFile.upload.err400': '上传参数不合法：{message}',
  'assetFile.upload.err413': '文件过大，超过服务端允许的上限',
  'assetFile.upload.err5xx': '服务端错误（HTTP {status}），文件未上传',
  'assetFile.upload.errGeneric': '上传失败：{message}',

  // ---------- 筛选条（资产文件 / 交付物共用）----------
  'assetFile.filter.categoryPlaceholder': '分类',
  'assetFile.filter.versionPlaceholder': '版本',
  'assetFile.filter.keywordPlaceholder': '文件名关键字',
  'assetFile.filter.uploaderPlaceholder': '上传人',
  'assetFile.filter.uploaderUnavailable': '上传人（数据未提供）',
  'assetFile.filter.reset': '重置筛选',
  'assetFile.filter.serverHint': '筛选/排序/分页由服务端处理',
  'assetFile.filter.localHint': '当前为降级模式：筛选仅作用于当前页已加载的数据',
  'assetFile.col.uploaderSortHint': '按账号（用户 ID）排序，不是姓名拼音序',
  'assetFile.col.noVersion': '无版本',
  'assetFile.filter.resultCount': '共 {count} 个文件',
  'assetFile.filter.empty': '没有符合筛选条件的文件',
  'assetFile.filter.localFallback': '本地筛选（后端未返回分页信息）',
  'assetFile.pagination.total': '共 {count} 条',
  'assetFile.col.uploader': '上传人',
  'assetFile.col.remark': '备注',

  // ---------- 排序 ----------
  'assetFile.sort.hint': '点击列头可升序 / 降序 / 取消排序',

  // ---------- 交付物 ----------
  'deliverable.upload.title': '上传交付物',
  'deliverable.upload.open': '上传交付物',
  'deliverable.upload.type': '交付物类型',
  'deliverable.upload.success': '交付物上传成功',
} as const;

export const assetFileEn: Record<keyof typeof assetFileZh, string> = {
  'assetFile.upload.title': 'Upload asset file',
  'assetFile.upload.open': 'Upload file',
  'assetFile.upload.submit': 'Upload',
  'assetFile.upload.fileLabel': 'File',
  'assetFile.upload.dragHint': 'Click or drag a file into this area',
  'assetFile.upload.dragSubHint': 'One file at a time; the name comes from the file itself',
  'assetFile.upload.version': 'Version',
  'assetFile.upload.versionRequired': 'Select the version this file belongs to',
  'assetFile.upload.versionPlaceholder': 'Select a version',
  'assetFile.upload.category': 'Category',
  'assetFile.upload.categoryRequired': 'Select a file category',
  'assetFile.upload.description': 'Description',
  'assetFile.upload.remark': 'Remark (optional)',
  'assetFile.upload.remarkHint': 'The remark is a separate field and shows in its own list column',
  'assetFile.upload.noFile': 'Please choose a file first',
  'assetFile.upload.uploading': 'Uploading',
  'assetFile.upload.acceptedHint': 'The list refreshes after a successful upload; on failure the form keeps what you filled in.',
  'assetFile.upload.success': 'File uploaded',
  'assetFile.upload.err400': 'Invalid upload parameters: {message}',
  'assetFile.upload.err413': 'The file is larger than the server allows',
  'assetFile.upload.err5xx': 'Server error (HTTP {status}); the file was not uploaded',
  'assetFile.upload.errGeneric': 'Upload failed: {message}',

  'assetFile.filter.categoryPlaceholder': 'Category',
  'assetFile.filter.versionPlaceholder': 'Version',
  'assetFile.filter.keywordPlaceholder': 'File name keyword',
  'assetFile.filter.uploaderPlaceholder': 'Uploader',
  'assetFile.filter.uploaderUnavailable': 'Uploader (not provided by the API)',
  'assetFile.filter.reset': 'Reset filters',
  'assetFile.filter.serverHint': 'Filtering, sorting and paging are handled by the server',
  'assetFile.filter.localHint': 'Fallback mode: filters apply only to the rows already loaded on this page',
  'assetFile.col.uploaderSortHint': 'Sorts by account (user ID), not by name',
  'assetFile.col.noVersion': 'No version',
  'assetFile.filter.resultCount': '{count} file(s)',
  'assetFile.filter.empty': 'No files match the filters',
  'assetFile.filter.localFallback': 'local filtering (no pagination info from the server)',
  'assetFile.pagination.total': '{count} items',
  'assetFile.col.uploader': 'Uploader',
  'assetFile.col.remark': 'Remark',

  'assetFile.sort.hint': 'Click a column header to sort ascending / descending / clear',

  'deliverable.upload.title': 'Upload deliverable',
  'deliverable.upload.open': 'Upload deliverable',
  'deliverable.upload.type': 'Deliverable type',
  'deliverable.upload.success': 'Deliverable uploaded',
};


