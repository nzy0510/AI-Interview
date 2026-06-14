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
    ATOMS_GENERATED: '已生成原子',
    FAILED: '失败',
    ARCHIVED: '已归档'
  }
  return labels[status] || status || '未知'
}

export function getAtomReviewLabel(status) {
  const labels = {
    PASS: '通过',
    NEEDS_REVIEW: '需处理',
    REJECT: '拒绝',
    UNREVIEWED: '未审查'
  }
  return labels[status] || status || '未知'
}

export function getAtomReviewType(status) {
  if (status === 'PASS') return 'success'
  if (status === 'NEEDS_REVIEW') return 'warning'
  if (status === 'REJECT') return 'danger'
  return 'info'
}

export function getPublicationStatusLabel(status) {
  const labels = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档'
  }
  return labels[status] || status || '未知'
}

export function getPublicationStatusType(status) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'info'
  return 'warning'
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

export function canApplySuggestedPatch(atom) {
  return atom?.reviewStatus === 'NEEDS_REVIEW' && Boolean(atom?.suggestedPatchJson)
}

export function canPublishAtom(atom) {
  return atom?.reviewStatus === 'PASS' && atom?.publicationStatus !== 'PUBLISHED'
}

export function countPublishableAtoms(atoms) {
  return (atoms || []).filter((atom) => canPublishAtom(atom)).length
}

export function canRetryJob(job) {
  return job?.status === 'FAILED' && job?.retryable === true
}

export function isCompletedAtomGenerationJob(job) {
  return job?.jobType === 'GENERATE_ATOMS' && job?.status === 'COMPLETED'
}

export function parseGenerationResult(job) {
  if (!job?.resultJson) return null
  try {
    const parsed = JSON.parse(job.resultJson)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

export function generationCompletionMessage(job) {
  const result = parseGenerationResult(job)
  if (!result) {
    return '原子生成已完成。若仍有遗漏，可追加生成；如需重做，请先清理旧草稿后重新生成 / 二审。'
  }
  const imported = Number(result.imported || 0)
  if (result.atomLimitReached) {
    return `原子生成已完成，已生成 ${imported} 条，已达到单次上限 100 条。若仍有遗漏，可继续追加生成。`
  }
  return `原子生成已完成，已生成 ${imported} 条。若仍有遗漏，可追加生成；如需重做，请先清理旧草稿后重新生成 / 二审。`
}
