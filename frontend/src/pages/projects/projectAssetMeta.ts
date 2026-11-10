import type { MessageKey } from '@/i18n/messages/zh-CN';
import type { ProjectAssetLinkType } from '@/api/project';

/**
 * 项目资产挂靠类型 → i18n key（t155）。
 *
 * 后端 `linkType` 取值固定为 TARGET / SHARED / REFERENCE（t154 批量接口整批生效），
 * 未知值回落 TARGET 的展示文案，避免脏数据把标签渲染成空。
 */
export const LINK_TYPE_ORDER: ProjectAssetLinkType[] = ['TARGET', 'SHARED', 'REFERENCE'];

export const linkTypeKey = (type?: string | null): MessageKey => {
  switch ((type || '').toUpperCase()) {
    case 'SHARED':
      return 'projectAsset.linkTypeSHARED';
    case 'REFERENCE':
      return 'projectAsset.linkTypeREFERENCE';
    default:
      return 'projectAsset.linkTypeTARGET';
  }
};
