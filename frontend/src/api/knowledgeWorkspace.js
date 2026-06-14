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

export const archivePrivatePositionAPI = (positionId) => {
  return request({
    url: `/knowledge-workspace/positions/${positionId}/archive`,
    method: 'post'
  })
}

export const uploadKnowledgeFileAPI = (knowledgeBaseId, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: `/knowledge-bases/${knowledgeBaseId}/files`,
    method: 'post',
    data: formData
  })
}

export const getAppJobsAPI = (options = {}) => {
  return request({
    url: '/jobs',
    method: 'get',
    ...options
  })
}

export const retryAppJobAPI = (jobId) => {
  return request({
    url: `/jobs/${jobId}/retry`,
    method: 'post'
  })
}

export const getKnowledgeFileAtomsAPI = (sourceFileId, options = {}) => {
  return request({
    url: `/knowledge-files/${sourceFileId}/atoms`,
    method: 'get',
    ...options
  })
}

export const generateKnowledgeAtomsAPI = (sourceFileId) => {
  return request({
    url: `/knowledge-files/${sourceFileId}/atoms/generate`,
    method: 'post'
  })
}

export const createManualKnowledgeAtomAPI = (sourceFileId, data) => {
  return request({
    url: `/knowledge-files/${sourceFileId}/atoms`,
    method: 'post',
    data
  })
}

export const acceptKnowledgeAtomPatchAPI = (atomId) => {
  return request({
    url: `/knowledge-atoms/${atomId}/accept-patch`,
    method: 'post'
  })
}

export const updateKnowledgeAtomAPI = (atomId, data) => {
  return request({
    url: `/knowledge-atoms/${atomId}`,
    method: 'put',
    data
  })
}

export const publishKnowledgeAtomAPI = (atomId) => {
  return request({
    url: `/knowledge-atoms/${atomId}/publish`,
    method: 'post'
  })
}

export const publishKnowledgeFileAtomsAPI = (sourceFileId) => {
  return request({
    url: `/knowledge-files/${sourceFileId}/atoms/publish`,
    method: 'post'
  })
}
