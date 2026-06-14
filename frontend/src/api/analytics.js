import request from '@/utils/request'

export const trackEventAPI = (data) => {
  return request({ url: '/analytics/event', method: 'post', data, silent: true })
}

export const getAnalyticsSummaryAPI = (days) => {
  return request({
    url: '/analytics/summary',
    method: 'get',
    params: { days }
  })
}

export const submitFeedbackAPI = (data) => {
  return request({ url: '/feedback', method: 'post', data })
}

export const getLatestFeedbackAPI = (limit = 20) => {
  return request({
    url: '/admin/feedback',
    method: 'get',
    params: { limit }
  })
}
