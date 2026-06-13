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

export function getFileStatusType(status) {
  if (status === 'CONVERTED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'CONVERTING') return 'warning'
  return 'info'
}

export function getFileStatusLabel(status) {
  const labels = {
    UPLOADED: '已上传',
    CONVERTING: '转换中',
    CONVERTED: '已转换',
    FAILED: '失败',
    ARCHIVED: '已归档'
  }
  return labels[status] || status || '未知'
}

export function findLatestJobForSourceFile(jobs, sourceFileId) {
  if (!sourceFileId) return null
  return [...(jobs || [])]
    .filter((job) => job.sourceFileId === sourceFileId)
    .sort((a, b) => (b.id || 0) - (a.id || 0))[0] || null
}

export function canUploadToPosition(position) {
  return isPositionEditable(position) && Boolean(position?.knowledgeBase?.id)
}
