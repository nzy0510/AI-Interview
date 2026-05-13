import { getAnonymousId } from '@/utils/visitor'

export const trackEvent = (eventType, metadata = {}, category = 'product') => {
  const payload = {
    eventType,
    category,
    pageUrl: window.location.pathname,
    metadata: {
      ...metadata,
      title: document.title || ''
    }
  }

  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  const token = localStorage.getItem('token')
  fetch(`${baseUrl}/analytics/event`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Anonymous-Id': getAnonymousId(),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(payload),
    keepalive: true
  }).catch(() => {
    // 埋点不能影响主流程
  })
}

export const trackPageView = (to) => {
  trackEvent('PAGE_VIEW', {
    path: to.fullPath,
    routeName: to.name || '',
    title: to.meta?.title || ''
  })
}
