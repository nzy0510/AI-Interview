export const KNOWLEDGE_WORKSPACE_CAPABILITIES_KEY = Symbol('knowledge-workspace-capabilities')

export const QUESTION_BANK_TABS = [
  { key: 'overview', label: '概览' },
  { key: 'build', label: '智能构建', privateOnly: true },
  { key: 'candidates', label: '候选审核', privateOnly: true },
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
  return Boolean(position?.knowledgeBase?.id)
    && position?.scope === 'PRIVATE'
    && position?.status !== 'ARCHIVED'
    && isPositionEditable(position)
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
