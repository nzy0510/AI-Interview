import request from '@/utils/request'

export const trackEventAPI = (data) => {
  return request({ url: '/analytics/event', method: 'post', data, silent: true })
}

export const getMyQuotaAPI = () => {
  return request({ url: '/analytics/quota/me', method: 'get' })
}

export const getAnalyticsSummaryAPI = (days, adminToken) => {
  return request({
    url: '/analytics/summary',
    method: 'get',
    params: { days },
    headers: { 'X-Admin-Token': adminToken }
  })
}

export const submitFeedbackAPI = (data) => {
  return request({ url: '/feedback', method: 'post', data })
}

export const getLatestFeedbackAPI = (adminToken, limit = 20) => {
  return request({
    url: '/admin/feedback',
    method: 'get',
    params: { limit },
    headers: { 'X-Admin-Token': adminToken }
  })
}
