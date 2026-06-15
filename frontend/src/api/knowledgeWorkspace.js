import request from '@/utils/request'

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
  })
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
