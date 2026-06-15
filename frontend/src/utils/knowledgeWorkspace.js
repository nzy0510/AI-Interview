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
