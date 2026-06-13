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
