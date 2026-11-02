/**
 * 项目 / 期次 前端类型（t72，对接后端 t68 的 ProjectController / ProjectPhaseController）。
 *
 * 说明：`ProjectVO` / `PhaseVO` / `ProjectDetailVO` 的**唯一定义**在 `types/customer.ts`
 * （t71 客户页先落地，字段与后端 ProjectResponse / PhaseResponse / ProjectDetailResponse 一致），
 * 这里只做 re-export，避免两处定义漂移；本文件承载项目页专用的查询/请求/挂靠类型与常量。
 */
import type { MessageKey } from '@/i18n/messages/zh-CN';

export type { ProjectVO, PhaseVO, ProjectDetailVO } from './customer';
import type { ProjectVO } from './customer';

/** 项目状态（后端 ProjectServiceImpl.TRANSITIONS 的键集合） */
export type ProjectStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'CLOSED';

/** 项目类型（后端 PROJECT_TYPES） */
export type ProjectType = 'PENTEST' | 'REAUDIT' | 'CONSULTING' | 'TRAINING';

/** 期次状态（后端只做大写归一，默认 PLANNED；前端给出常用四项） */
export type PhaseStatus = 'PLANNED' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED';

export interface ProjectQuery {
  /** 页码从 1 开始（api 层转 0 基） */
  page?: number;
  page_size?: number;
  customerId?: string;
  status?: string;
  type?: string;
  keyword?: string;
}

export interface ProjectRequest {
  code: string;
  customerId: string;
  name: string;
  type?: string;
  managerId?: string | null;
  /** yyyy-MM-dd */
  startDate?: string | null;
  endDate?: string | null;
  isPhased?: boolean;
  contractNo?: string | null;
  description?: string | null;
}

export interface PhaseRequest {
  phaseNo?: number | null;
  name: string;
  startDate?: string | null;
  endDate?: string | null;
  status?: string;
}

/**
 * 资产挂靠行（后端 t69 `GET /projects/{id}/assets` 的 ProjectAssetItem，分页返回）。
 * phaseId / phaseName 为空表示「项目级挂靠 = 不分期」。
 */
export interface ProjectAssetLinkVO {
  linkId?: string | null;
  assetId: string;
  assetCode?: string | null;
  assetName?: string | null;
  assetStatus?: string | null;
  assetPriority?: string | null;
  targetType?: string | null;
  ownerId?: string | null;
  remark?: string | null;
  /** 兼容旧字段名 */
  status?: string | null;
  /** 挂靠类型：PHASE（按期次）/ PROJECT（不分期） */
  linkType?: string | null;
  phaseId?: string | null;
  phaseNo?: number | null;
  phaseName?: string | null;
  linkedAt?: string | null;
  /** 兼容后端可能直接返回资产对象 */
  asset?: {
    id?: string;
    code?: string | null;
    name?: string | null;
    targetType?: string | null;
    target_type?: string | null;
    status?: string | null;
  } | null;
}

/**
 * 前端状态机镜像（后端 ProjectServiceImpl.TRANSITIONS）：
 * DRAFT → ACTIVE；ACTIVE → {PAUSED, CLOSED}；PAUSED → {ACTIVE, CLOSED}；CLOSED 为终态。
 */
export const PROJECT_TRANSITIONS: Record<ProjectStatus, ProjectStatus[]> = {
  DRAFT: ['ACTIVE'],
  ACTIVE: ['PAUSED', 'CLOSED'],
  PAUSED: ['ACTIVE', 'CLOSED'],
  CLOSED: [],
};

/** 状态流转 → 按钮文案 key */
export const PROJECT_TRANSITION_LABEL_KEYS: Record<string, MessageKey> = {
  'DRAFT->ACTIVE': 'project.action.activate',
  'ACTIVE->PAUSED': 'project.action.pause',
  'PAUSED->ACTIVE': 'project.action.resume',
  'ACTIVE->CLOSED': 'project.action.close',
  'PAUSED->CLOSED': 'project.action.close',
};

export const PROJECT_STATUS_ORDER: ProjectStatus[] = ['DRAFT', 'ACTIVE', 'PAUSED', 'CLOSED'];

export const PROJECT_TYPE_ORDER: ProjectType[] = ['PENTEST', 'REAUDIT', 'CONSULTING', 'TRAINING'];

export const PHASE_STATUS_ORDER: PhaseStatus[] = ['PLANNED', 'IN_PROGRESS', 'DONE', 'CANCELLED'];

export const projectStatusKey = (status?: string | null): MessageKey =>
  `project.status.${String(status ?? 'DRAFT').toUpperCase()}` as MessageKey;

export const projectTypeKey = (type?: string | null): MessageKey =>
  `project.type.${String(type ?? 'PENTEST').toUpperCase()}` as MessageKey;

export const phaseStatusKey = (status?: string | null): MessageKey =>
  `phase.status.${String(status ?? 'PLANNED').toUpperCase()}` as MessageKey;

/** 状态配色（与 antd Tag 的预设色一致） */
export const PROJECT_STATUS_COLORS: Record<string, string> = {
  DRAFT: 'default',
  ACTIVE: 'processing',
  PAUSED: 'warning',
  CLOSED: 'success',
};

export const PHASE_STATUS_COLORS: Record<string, string> = {
  PLANNED: 'default',
  IN_PROGRESS: 'processing',
  DONE: 'success',
  CANCELLED: 'default',
};

/** 该项目在给定状态下允许流转到的目标状态（非法流转不渲染按钮） */
export const nextProjectStatuses = (status?: string | null): ProjectStatus[] => {
  const current = String(status ?? 'DRAFT').toUpperCase() as ProjectStatus;
  return PROJECT_TRANSITIONS[current] ?? [];
};

export const isProjectClosed = (project?: Pick<ProjectVO, 'status'> | null): boolean =>
  String(project?.status ?? '').toUpperCase() === 'CLOSED';

export const transitionLabelKey = (from?: string | null, to?: string | null): MessageKey =>
  PROJECT_TRANSITION_LABEL_KEYS[`${String(from ?? '').toUpperCase()}->${String(to ?? '').toUpperCase()}`] ??
  'project.confirm.transition';

/** 期次状态自定义值（后端允许任意大写字符串）：未知值原样展示 */
export const phaseStatusFallback = (status?: string | null): string => String(status ?? '').toUpperCase() || 'PLANNED';
