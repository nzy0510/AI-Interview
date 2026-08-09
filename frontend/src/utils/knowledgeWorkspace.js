export const KNOWLEDGE_WORKSPACE_CAPABILITIES_KEY = Symbol('knowledge-workspace-capabilities')

export const QUESTION_BANK_TABS = [
  { key: 'overview', label: '概览' },
  { key: 'build', label: '智能构建', requiresBuildAccess: true },
  { key: 'candidates', label: '终审发布', requiresBuildAccess: true },
  { key: 'atoms', label: '题库原子' }
]

export function isPositionEditable(position) {
  return Boolean(position?.editable) && position?.status !== 'ARCHIVED' && position?.scope === 'PRIVATE'
}

export function getPositionScopeLabel(position) {
  if (position?.scope === 'PUBLIC') return '公共岗位'
  if (position?.scope === 'PRIVATE') return '我的岗位'
  return '未知作用域'
}

export function getPositionStatusType(status) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'ARCHIVED') return 'info'
  return 'warning'
}

export function canMaintainQuestionBank(position) {
  return Boolean(position?.knowledgeBase?.id)
    && position?.status !== 'ARCHIVED'
    && (Boolean(position?.canImportPackage) || Boolean(position?.canManageAtoms) || isPositionEditable(position))
}

export function canBuildQuestionBank(position) {
  if (!position?.knowledgeBase?.id || position?.status === 'ARCHIVED') return false
  if (typeof position?.canBuildQuestionBank === 'boolean') return position.canBuildQuestionBank
  if (position?.scope === 'PUBLIC') return Boolean(position?.canManageAtoms)
  return isPositionEditable(position)
}

export function isQuestionBankCandidateAccepted(candidate) {
  return ['ACCEPTED', 'PASS'].includes(String(candidate?.status || candidate?.reviewStatus || '').toUpperCase())
}

export function isQuestionBankAtomPublishEligible(atom) {
  const status = String(atom?.status || '').toUpperCase()
  const reviewStatus = String(atom?.reviewStatus || '').toUpperCase()
  return status === 'DRAFT' && reviewStatus === 'PASS'
}

export function isQuestionBankBuildInProgress(build) {
  const status = String(build?.status || build || '').toUpperCase()
  return ['PENDING', 'RUNNING'].includes(status)
}

export function getQuestionBankBuildStatusLabel(status) {
  const labels = {
    PENDING: '排队中',
    QUEUED: '排队中',
    RUNNING: '处理中',
    SUCCEEDED: '已完成',
    COMPLETED: '已完成',
    FAILED: '失败',
    ERROR: '失败',
    CANCELLED: '已取消'
  }
  return labels[String(status || '').toUpperCase()] || '未知状态'
}

export function getQuestionBankBuildStatusType(status) {
  const normalized = String(status || '').toUpperCase()
  if (['SUCCEEDED', 'COMPLETED'].includes(normalized)) return 'success'
  if (['FAILED', 'ERROR'].includes(normalized)) return 'danger'
  if (['RUNNING', 'PENDING', 'QUEUED'].includes(normalized)) return 'warning'
  return 'info'
}

export function getQuestionBankBuildStageLabel(stage) {
  const labels = {
    QUEUED: '等待处理',
    GENERATING: '正在处理文档',
    SUPERVISING: '正在审查处理结果',
    READY_FOR_FINAL_REVIEW: '等待人工终审',
    FINALIZING: '正在执行终审',
    IMPORTING: '正在写入草稿',
    PUBLISHING: '正在发布入库',
    INDEXING: '正在同步检索索引',
    PUBLISHED: '已发布入库',
    PUBLISHED_WITH_INDEX_ERRORS: '已发布，部分索引待重试',
    FAILED: '处理失败'
  }
  const normalized = String(stage || '').toUpperCase()
  return labels[normalized] || (normalized ? normalized : '等待后端返回')
}

export function canPublishQuestionBankAtoms(position) {
  return Boolean(position?.knowledgeBase?.id)
    && position?.status !== 'ARCHIVED'
    && Boolean(position?.canPublishAtoms)
}

export function canReindexQuestionBankAtoms(position) {
  return Boolean(position?.knowledgeBase?.id)
    && position?.status !== 'ARCHIVED'
    && Boolean(position?.canReindexAtoms)
}

export function canArchiveQuestionBankAtoms(position) {
  return Boolean(position?.knowledgeBase?.id)
    && position?.status !== 'ARCHIVED'
    && Boolean(position?.canArchiveAtoms)
}

export function parseImportPackageText(text) {
  let parsed
  try {
    parsed = JSON.parse(text)
  } catch {
    throw new Error('无法解析 JSON 导入包')
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('导入包必须是 JSON 对象')
  }
  return parsed
}

export function normalizeKnowledgeWorkspaceCapabilities(value) {
  return {
    userMaintenanceEnabled: value?.userMaintenanceEnabled === true,
    admin: value?.admin === true,
    canAccessWorkspace: value?.canAccessWorkspace === true
  }
}

export function shouldShowKnowledgeWorkspace(capabilities) {
  return capabilities?.canAccessWorkspace === true
}

export function isPublicOnlyMaintenanceMode(capabilities) {
  return capabilities?.admin === true && capabilities?.userMaintenanceEnabled !== true
}

export function canCreatePrivatePosition(capabilities) {
  return capabilities?.canAccessWorkspace === true
    && capabilities?.userMaintenanceEnabled === true
}

export function getKnowledgeWorkspaceNavLabel(capabilities) {
  return isPublicOnlyMaintenanceMode(capabilities) ? '公共题库维护' : '岗位 / 题库维护'
}
