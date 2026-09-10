import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminAnalytics from '../AdminAnalytics.vue'
import { getAnalyticsSummaryAPI } from '@/api/analytics'

vi.mock('@/api/analytics', () => ({
  getAnalyticsSummaryAPI: vi.fn()
}))

let wrapper

beforeEach(() => {
  const storage = new Map()
  vi.stubGlobal('localStorage', {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: key => storage.delete(key)
  })
})

afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
  vi.clearAllMocks()
})

describe('AdminAnalytics initial loading', () => {
  it('首次打开时展示近 7 天运营数据，并清除旧管理 Token', async () => {
    localStorage.setItem('interwise_admin_token', 'retired-test-token')
    getAnalyticsSummaryAPI.mockResolvedValue({
      pageViews: 34,
      uniqueVisitors: 8,
      interviewCompletionRate: 75
    })

    wrapper = mount(AdminAnalytics, {
      global: {
        stubs: {
          ElSelect: { template: '<div><slot /></div>' },
          ElOption: true,
          ElButton: { template: '<button><slot /></button>' },
          ElEmpty: { props: ['description'], template: '<p>{{ description }}</p>' },
          ElTable: { template: '<div><slot /></div>' },
          ElTableColumn: true
        }
      }
    })
    await flushPromises()

    expect(getAnalyticsSummaryAPI).toHaveBeenCalledWith(7)
    expect(wrapper.findAll('.metric-cell').map(cell => cell.text()))
      .toEqual(expect.arrayContaining(['PV34', 'UV8', '完成率75%']))
    expect(wrapper.text()).not.toContain('管理员账号登录后可加载运营数据')
    expect(localStorage.getItem('interwise_admin_token')).toBeNull()
  })
})
