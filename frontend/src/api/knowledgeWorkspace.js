import request from '@/utils/request'

const buildPath = (knowledgeBaseId, suffix = '') =>
  `/knowledge-workspace/knowledge-bases/${knowledgeBaseId}/builds${suffix}`

const unwrapCollection = (payload, keys = []) => {
  if (Array.isArray(payload)) return payload
  for (const key of keys) {
    if (Array.isArray(payload?.[key])) return payload[key]
  }
  return []
}

const asNumber = (value, fallback = 0) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

const asText = (value, fallback = '') => value == null ? fallback : String(value)

const asStringList = (value) => {
  if (Array.isArray(value)) return value.map((item) => String(item).trim()).filter(Boolean)
  if (typeof value !== 'string' || !value.trim()) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.map((item) => String(item).trim()).filter(Boolean) : []
  } catch {
    return value.split(/[,，\n]/).map((item) => item.trim()).filter(Boolean)
  }
}

export function normalizeQuestionBankBuild(raw = {}) {
  const source = raw?.build || raw?.data || raw || {}
  const files = Array.isArray(source.sourceFiles)
    ? source.sourceFiles
    : (Array.isArray(source.files) ? source.files : [])
  const llm = source.llm || {}
  const progressValue = source.progress ?? source.progressPercent ?? source.progressRate
  const progress = Math.max(0, Math.min(100, asNumber(progressValue, 0)))
  return {
    ...source,
    id: source.buildId ?? source.id,
    buildId: source.buildId ?? source.id,
    jobId: source.jobId ?? source.job?.id ?? null,
    status: asText(source.status || source.state, 'UNKNOWN').toUpperCase(),
    stage: asText(source.stage || source.currentStage, '').toUpperCase(),
    progress,
    provider: asText(source.provider || llm.provider),
    model: asText(source.model || source.modelName || llm.model || llm.modelName),
    llmConfigured: Boolean(source.llmConfigured ?? source.hasLlmConfig ?? llm.configured),
    sourceFiles: files,
    fileCount: asNumber(source.fileCount, files.length),
    chunkCount: asNumber(source.chunkCount ?? source.expectedChunkCount ?? source.estimatedChunkCount),
    completedChunkCount: asNumber(source.completedChunkCount),
    candidateCount: asNumber(source.candidateCount),
    acceptedCount: asNumber(source.acceptedCount),
    rejectedCount: asNumber(source.rejectedCount),
    expectedChunks: asNumber(source.expectedChunks ?? source.expectedChunkCount ?? source.estimatedChunkCount ?? source.chunkCount),
    estimatedCalls: asNumber(source.estimatedCalls ?? source.estimatedCallCount),
    errorMessage: asText(source.errorMessage || source.error),
    canRetry: Boolean(source.canRetry ?? ['FAILED', 'ERROR'].includes(asText(source.status || source.state).toUpperCase())),
    createdAt: source.createTime ?? source.createdAt,
    updatedAt: source.updateTime ?? source.updatedAt
  }
}

export function normalizeQuestionBankBuildList(payload) {
  return unwrapCollection(payload, ['builds', 'items', 'content']).map(normalizeQuestionBankBuild)
}

export function normalizeQuestionBankCandidate(raw = {}) {
  const source = raw?.candidate || raw?.data || raw || {}
  const atom = source.atom || source.knowledgeAtom || {}
  const content = source.content || atom.content || {}
  const sourceEvidenceValue = source.sourceEvidence || source.evidence || []
  const sourceEvidence = Array.isArray(sourceEvidenceValue)
    ? sourceEvidenceValue
    : (sourceEvidenceValue && typeof sourceEvidenceValue === 'object' ? [sourceEvidenceValue] : [])
  const firstEvidence = sourceEvidence[0] || {}
  const sourceRef = source.sourceRef || atom.sourceRef || ''
  return {
    ...source,
    id: source.candidateId ?? source.id,
    candidateId: source.candidateId ?? source.id,
    stableAtomId: source.stableAtomId || atom.id || '',
    status: asText(source.reviewStatus || source.candidateStatus || source.status, 'PENDING').toUpperCase(),
    subject: asText(source.subject || atom.subject),
    category: asText(source.category || atom.category),
    difficulty: asText(source.difficulty || atom.difficulty),
    tags: source.tags || atom.tags || [],
    principles: asText(source.principles ?? content.principles),
    pitfalls: Array.isArray(source.pitfalls ?? content.pitfalls)
      ? (source.pitfalls ?? content.pitfalls).join('\n')
      : asText(source.pitfalls ?? content.pitfalls),
    followUpPaths: source.followUpPaths || source.follow_up_paths || content.followUpPaths || content.follow_up_paths || [],
    sourceRef,
    source: source.source || {
      sourceRef,
      evidence: sourceEvidence,
      fileName: source.fileName || source.originalFilename || firstEvidence.fileName || '',
      page: source.page || source.pageOrSection || firstEvidence.page || firstEvidence.pageOrSection || '',
      quote: source.quote || firstEvidence.quote || ''
    },
    sourceEvidence,
    selfCheck: source.selfCheck || { passed: true, reason: '', confidence: null },
    duplicateHint: source.duplicateHint || source.duplicate || '',
    validationIssues: source.validationIssues || source.errors || []
  }
}

export function normalizeQuestionBankCandidateList(payload) {
  return unwrapCollection(payload, ['candidates', 'items', 'content']).map(normalizeQuestionBankCandidate)
}

export function normalizeQuestionBankAtom(raw = {}) {
  const source = raw?.atom || raw || {}
  return {
    ...source,
    atomId: source.atomId || source.id,
    subject: source.subject || '',
    category: source.category || '',
    difficulty: source.difficulty || '',
    status: String(source.status || source.lifecycleStatus || '').toUpperCase(),
    reviewStatus: String(source.reviewStatus || '').toUpperCase(),
    vectorStatus: String(source.vectorStatus || '').toUpperCase(),
    tags: asStringList(source.tags ?? source.tagsJson),
    principles: source.principles || source.content?.principles || '',
    pitfalls: source.pitfalls || source.content?.pitfalls || '',
    followUpPaths: asStringList(source.followUpPaths ?? source.followUpPathsJson ?? source.content?.followUpPaths),
    sourceRef: source.sourceRef || source.source?.sourceRef || '',
    source: source.source || { sourceRef: source.sourceRef || '' }
  }
}

export function normalizeQuestionBankAtomPage(payload) {
  const source = payload || {}
  const items = Array.isArray(source) ? source : (source.items || source.content || [])
  return {
    ...source,
    items: items.map(normalizeQuestionBankAtom),
    total: asNumber(source.total, items.length),
    page: asNumber(source.page || source.current, 1),
    size: asNumber(source.size || source.pageSize, items.length || 20)
  }
}

export const createQuestionBankBuildAPI = (knowledgeBaseId, files = [], categories = [], options = {}) => {
  const formData = new FormData()
  for (const file of Array.from(files || [])) {
    const value = file?.raw || file
    if (value) formData.append('files', value)
  }
  for (const category of Array.from(categories || [])) {
    const value = String(category || '').trim()
    if (value) formData.append('categories', value)
  }
  return request({
    url: buildPath(knowledgeBaseId),
    method: 'post',
    data: formData,
    ...options
  }).then(normalizeQuestionBankBuild)
}

export const listQuestionBankBuildsAPI = (knowledgeBaseId, options = {}) => request({
  url: buildPath(knowledgeBaseId),
  method: 'get',
  ...options
}).then(normalizeQuestionBankBuildList)

export const getQuestionBankBuildAPI = (knowledgeBaseId, buildId, options = {}) => request({
  url: buildPath(knowledgeBaseId, `/${buildId}`),
  method: 'get',
  ...options
}).then(normalizeQuestionBankBuild)

export const deleteQuestionBankBuildAPI = (knowledgeBaseId, buildId, options = {}) => request({
  url: buildPath(knowledgeBaseId, `/${buildId}`),
  method: 'delete',
  ...options
})

export const getQuestionBankBuildCandidatesAPI = (knowledgeBaseId, buildId, options = {}) => request({
  url: buildPath(knowledgeBaseId, `/${buildId}/candidates`),
  method: 'get',
  ...options
}).then(normalizeQuestionBankCandidateList)

export const updateQuestionBankBuildCandidateAPI = (knowledgeBaseId, buildId, candidateId, data, options = {}) => request({
  url: buildPath(knowledgeBaseId, `/${buildId}/candidates/${candidateId}`),
  method: 'put',
  data,
  ...options
}).then(normalizeQuestionBankCandidate)

export const getQuestionBankBuildPackageAPI = (knowledgeBaseId, buildId, options = {}) => request({
  url: buildPath(knowledgeBaseId, `/${buildId}/package`),
  method: 'get',
  ...options
})

export const importQuestionBankBuildAPI = (knowledgeBaseId, buildId, options = {}) => request({
  url: buildPath(knowledgeBaseId, `/${buildId}/import`),
  method: 'post',
  ...options
})

export const retryQuestionBankBuildAPI = (jobId, options = {}) => request({
  url: `/jobs/${jobId}/retry`,
  method: 'post',
  ...options
})

// Compatibility aliases keep naming close to the existing knowledge-base APIs.
export const createKnowledgeBaseBuildAPI = createQuestionBankBuildAPI
export const getKnowledgeBaseBuildsAPI = listQuestionBankBuildsAPI
export const getKnowledgeBaseBuildAPI = getQuestionBankBuildAPI
export const getKnowledgeBaseBuildCandidatesAPI = getQuestionBankBuildCandidatesAPI

export const getKnowledgeWorkspaceCapabilitiesAPI = (options = {}) => {
  return request({
    url: '/knowledge-workspace/capabilities',
    method: 'get',
    ...options
  })
}

export const getKnowledgeWorkspaceAPI = (options = {}) => {
  return request({
    url: '/knowledge-workspace/positions',
    method: 'get',
    ...options
  })
}

export const createPrivatePositionAPI = (data) => {
  return request({
    url: '/knowledge-workspace/positions',
    method: 'post',
    data
  })
}

export const deletePrivatePositionAPI = (positionId) => {
  return request({
    url: `/knowledge-workspace/positions/${positionId}`,
    method: 'delete'
  })
}

export const validateKnowledgeBaseImportAPI = (knowledgeBaseId, data) => {
  return request({
    url: `/knowledge-workspace/knowledge-bases/${knowledgeBaseId}/import/validate`,
    method: 'post',
    data
  })
}

export const importKnowledgeBasePackageAPI = (knowledgeBaseId, data) => {
  return request({
    url: `/knowledge-workspace/knowledge-bases/${knowledgeBaseId}/import`,
    method: 'post',
    data
  })
}

export const searchKnowledgeBaseAtomsAPI = (knowledgeBaseId, data) => {
  return request({
    url: `/knowledge-workspace/knowledge-bases/${knowledgeBaseId}/atoms/search`,
    method: 'post',
    data
  }).then(normalizeQuestionBankAtomPage)
}

export const publishKnowledgeBaseAtomsAPI = (knowledgeBaseId, atomIds) => {
  return request({
    url: `/knowledge-workspace/knowledge-bases/${knowledgeBaseId}/atoms/publish`,
    method: 'post',
    data: { atomIds }
  })
}

export const publishAllDraftAtomsAPI = (knowledgeBaseId) => {
  return request({
    url: `/knowledge-workspace/knowledge-bases/${knowledgeBaseId}/atoms/publish-drafts`,
    method: 'post'
  })
}

export const archiveKnowledgeBaseAtomsAPI = (knowledgeBaseId, atomIds) => {
  return request({
    url: `/knowledge-workspace/knowledge-bases/${knowledgeBaseId}/atoms/archive`,
    method: 'post',
    data: { atomIds }
  })
}

export const archiveAllAtomsAPI = (knowledgeBaseId) => {
  return request({
    url: `/knowledge-workspace/knowledge-bases/${knowledgeBaseId}/atoms/archive-all`,
    method: 'post'
  })
}

export const reindexKnowledgeBaseAtomsAPI = (knowledgeBaseId, atomIds) => {
  return request({
    url: `/knowledge-workspace/knowledge-bases/${knowledgeBaseId}/atoms/reindex`,
    method: 'post',
    data: { atomIds }
  })
}

export const getKnowledgeAtomAPI = (atomId) => request({
  url: `/knowledge-atoms/${atomId}`,
  method: 'get'
}).then(normalizeQuestionBankAtom)

export const updateKnowledgeAtomAPI = (atomId, data) => request({
  url: `/knowledge-atoms/${atomId}`,
  method: 'put',
  data
}).then(normalizeQuestionBankAtom)

export const getPositionCoverageAPI = (positionId) => {
  return request({
    url: `/knowledge-workspace/positions/${positionId}/coverage`,
    method: 'get'
  })
}
