import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import DashboardHome from '../DashboardHome.vue'

const mocks = vi.hoisted(() => ({
  router: { push: vi.fn() },
  getHistory: vi.fn(),
  getPositions: vi.fn(),
  getInsight: vi.fn(),
  getCoverage: vi.fn()
}))

vi.mock('vue-router', () => ({ useRouter: () => mocks.router }))
vi.mock('@element-plus/icons-vue', () => ({
  ArrowRight: {}, Document: {}, Operation: {}, TrendCharts: {}, VideoCamera: {}
}))
vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn() } }))
vi.mock('@/utils/auth', () => ({
  getUsername: () => 'nzy333',
  getNickname: () => null,
  setNickname: vi.fn()
}))
vi.mock('@/api/llm', () => ({
  getLlmConfigStatusAPI: vi.fn().mockResolvedValue({ resolved: true, hasActiveConfig: true })
}))
vi.mock('@/api/interview', () => ({ getHistoryListAPI: mocks.getHistory }))
vi.mock('@/api/position', () => ({ getVisiblePositionsAPI: mocks.getPositions }))
vi.mock('@/api/user', () => ({
  getMentorInsightAPI: mocks.getInsight,
  getKnowledgeCoverageAPI: mocks.getCoverage,
  getPreferenceAPI: vi.fn().mockResolvedValue({}),
  getCurrentUserAPI: vi.fn().mockResolvedValue({ username: 'nzy333' })
}))

describe('Dashboard AI Mentor scope', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getPositions.mockResolvedValue([
      { id: 1, name: 'Java 后端开发', scope: 'PUBLIC', historyCount: 0 },
      { id: 2, name: 'AI 大模型应用开发', scope: 'PRIVATE', historyCount: 1 }
    ])
    mocks.getHistory.mockResolvedValue([
      { id: 11, positionId: null, position: '旧记录', score: 70 },
      { id: 10, positionId: 99, position: '已删除岗位', score: 75 },
      { id: 9, positionId: null, position: '另一条旧记录', score: 72 },
      { id: 8, positionId: 2, position: 'AI 大模型应用开发', score: 82 }
    ])
    mocks.getCoverage.mockResolvedValue({ knowledgeCoverage: { details: [{ category: 'RAG' }] } })
    mocks.getInsight.mockResolvedValue({ diagnosis: { overview: 'AI 岗位诊断' } })
  })

  it('loads the dashboard Mentor summary from the latest visible structured position', async () => {
    shallowMount(DashboardHome)
    await flushPromises()

    expect(mocks.getCoverage).toHaveBeenCalledWith(2)
    expect(mocks.getInsight).toHaveBeenCalledWith(2)
  })

  it('does not request a global Mentor report when no structured visible position exists', async () => {
    mocks.getHistory.mockResolvedValue([{ id: 11, positionId: null, position: '旧记录', score: 70 }])

    shallowMount(DashboardHome)
    await flushPromises()

    expect(mocks.getCoverage).not.toHaveBeenCalled()
    expect(mocks.getInsight).not.toHaveBeenCalled()
  })
})
