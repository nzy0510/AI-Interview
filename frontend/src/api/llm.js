import request from '@/utils/request'

export const getLlmProviderPresetsAPI = (options = {}) => {
  return request({
    url: '/llm/providers/presets',
    method: 'get',
    ...options
  })
}

export const getLlmConfigsAPI = (options = {}) => {
  return request({
    url: '/llm/configs',
    method: 'get',
    ...options
  })
}

export const createLlmConfigAPI = (data, options = {}) => {
  return request({
    url: '/llm/configs',
    method: 'post',
    data,
    ...options
  })
}

export const updateLlmConfigAPI = (id, data, options = {}) => {
  return request({
    url: `/llm/configs/${id}`,
    method: 'put',
    data,
    ...options
  })
}

export const deleteLlmConfigAPI = (id, options = {}) => {
  return request({
    url: `/llm/configs/${id}`,
    method: 'delete',
    ...options
  })
}

export const activateLlmConfigAPI = (id, options = {}) => {
  return request({
    url: `/llm/configs/${id}/activate`,
    method: 'post',
    ...options
  })
}

export const testLlmConfigAPI = (data, options = {}) => {
  return request({
    url: '/llm/configs/test',
    method: 'post',
    data,
    ...options
  })
}

export const getLlmConfigStatusAPI = (options = {}) => {
  return request({
    url: '/llm/configs/status',
    method: 'get',
    ...options
  })
}
