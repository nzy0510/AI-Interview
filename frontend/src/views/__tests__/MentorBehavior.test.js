import { flushPromises, shallowMount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import Mentor from '../Mentor.vue'

const mocks = vi.hoisted(() => ({
  route: { query: {} },
  router: { push: vi.fn(), replace: vi.fn() },
  getPositions: vi.fn(),
  getHistory: vi.fn(),
  getInsight: vi.fn(),
  refreshInsight: vi.fn()
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => mocks.router
}))
vi.mock('@element-plus/icons-vue', () => ({ ArrowLeft: {}, RefreshRight: {} }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }))
vi.mock('@/api/llm', () => ({
  getLlmConfigStatusAPI: vi.fn().mockResolvedValue({ resolved: true, hasActiveConfig: true })
}))
vi.mock('@/api/position', () => ({ getVisiblePositionsAPI: mocks.getPositions }))
vi.mock('@/api/interview', () => ({ getHistoryListAPI: mocks.getHistory }))
vi.mock('@/api/user', () => ({
  getMentorInsightAPI: mocks.getInsight,
  refreshMentorInsightAPI: mocks.refreshInsight
}))

const positions = [
  { id: 1, name: 'Java 后端开发', scope: 'PUBLIC', historyCount: 0 },
  { id: 2, name: 'AI 大模型应用开发', scope: 'PRIVATE', historyCount: 3 }
]

const deferred = () => {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

describe('AI Mentor position isolation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.router.replace.mockReset().mockResolvedValue()
    mocks.route = reactive({ query: {} })
    mocks.getPositions.mockResolvedValue(positions)
    mocks.getHistory.mockResolvedValue([{ id: 10, positionId: 2 }])
    mocks.getInsight.mockResolvedValue({ diagnosis: { overview: 'AI 岗位诊断' } })
    mocks.refreshInsight.mockResolvedValue({ diagnosis: { overview: '刷新结果' } })
  })

  it('selects the latest visible interview position and keeps zero-history positions available', async () => {
    const wrapper = shallowMount(Mentor)
    await flushPromises()

    expect(wrapper.vm.selectedPositionId).toBe(2)
    expect(wrapper.vm.positionOptions).toEqual(positions)
    expect(mocks.getInsight).toHaveBeenCalledWith(2)
    expect(mocks.router.replace).toHaveBeenCalledWith({ path: '/mentor', query: { positionId: 2 } })
    expect(wrapper.text()).not.toContain('全部岗位')
  })

  it('prefers a visible URL position over recent history', async () => {
    mocks.route.query = { positionId: '1' }

    const wrapper = shallowMount(Mentor)
    await flushPromises()

    expect(wrapper.vm.selectedPositionId).toBe(1)
    expect(mocks.getInsight).toHaveBeenCalledWith(1)
  })

  it('ignores a late response from the previously selected position', async () => {
    const oldInsight = deferred()
    mocks.getInsight.mockImplementation(positionId => positionId === 2
      ? oldInsight.promise
      : Promise.resolve({ diagnosis: { overview: 'Java 岗位诊断' } }))
    const wrapper = shallowMount(Mentor)
    await flushPromises()

    await wrapper.vm.onPositionChange(1)
    await flushPromises()
    oldInsight.resolve({ diagnosis: { overview: '过期 AI 岗位诊断' } })
    await flushPromises()

    expect(wrapper.vm.selectedPositionId).toBe(1)
    expect(wrapper.vm.mentorInsight.diagnosis.overview).toBe('Java 岗位诊断')
  })

  it('refreshes only the currently selected position', async () => {
    const wrapper = shallowMount(Mentor)
    await flushPromises()

    await wrapper.vm.refreshMentor()

    expect(mocks.refreshInsight).toHaveBeenCalledWith(2)
  })

  it('reloads the matching position when browser history changes the URL', async () => {
    const wrapper = shallowMount(Mentor)
    await flushPromises()
    mocks.getInsight.mockClear()

    mocks.route.query.positionId = '1'
    await flushPromises()

    expect(wrapper.vm.selectedPositionId).toBe(1)
    expect(mocks.getInsight).toHaveBeenCalledWith(1)
    expect(wrapper.vm.mentorInsight.diagnosis.overview).toBe('AI 岗位诊断')
  })

  it('does not start an obsolete request when route updates resolve out of order', async () => {
    mocks.route.query = { positionId: '2' }
    const wrapper = shallowMount(Mentor)
    await flushPromises()
    mocks.getInsight.mockClear()
    const firstNavigation = deferred()
    const secondNavigation = deferred()
    mocks.router.replace.mockImplementation(({ query }) => query.positionId === 1
      ? firstNavigation.promise
      : secondNavigation.promise)

    const firstChange = wrapper.vm.onPositionChange(1)
    const secondChange = wrapper.vm.onPositionChange(2)
    secondNavigation.resolve()
    await secondChange
    firstNavigation.resolve()
    await firstChange
    await flushPromises()

    expect(mocks.getInsight).toHaveBeenCalledTimes(1)
    expect(mocks.getInsight).toHaveBeenCalledWith(2)
    expect(wrapper.vm.selectedPositionId).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
  })

  it('shows a load failure instead of reporting no positions when position loading fails', async () => {
    mocks.getPositions.mockRejectedValue(new Error('network down'))

    const wrapper = shallowMount(Mentor)
    await flushPromises()

    expect(wrapper.text()).toContain('岗位列表加载失败')
    expect(wrapper.text()).not.toContain('暂无可用岗位')
    expect(mocks.getInsight).not.toHaveBeenCalled()
  })
})
