import { defineComponent, nextTick, ref, unref } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { KNOWLEDGE_WORKSPACE_CAPABILITIES_KEY } from '@/utils/knowledgeWorkspace'
import KnowledgeWorkspace from '../KnowledgeWorkspace.vue'

const mocks = vi.hoisted(() => ({
  getWorkspace: vi.fn(),
  getCoverage: vi.fn(),
  searchAtoms: vi.fn(),
  getAtom: vi.fn(),
  updateAtom: vi.fn(),
  validateImport: vi.fn(),
  importPackage: vi.fn(),
  listBuilds: vi.fn()
}))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@element-plus/icons-vue', () => ({ ArrowLeft: {}, Plus: {}, RefreshRight: {} }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))
vi.mock('@/api/llm', () => ({ getLlmConfigStatusAPI: vi.fn().mockResolvedValue({ resolved: true, hasActiveConfig: true }) }))
vi.mock('@/api/knowledgeWorkspace', () => ({
  archiveAllAtomsAPI: vi.fn(),
  archiveKnowledgeBaseAtomsAPI: vi.fn(),
  createPrivatePositionAPI: vi.fn(),
  deletePrivatePositionAPI: vi.fn(),
  getKnowledgeWorkspaceAPI: mocks.getWorkspace,
  getKnowledgeAtomAPI: mocks.getAtom,
  getPositionCoverageAPI: mocks.getCoverage,
  getQuestionBankBuildPackageAPI: vi.fn(),
  importKnowledgeBasePackageAPI: mocks.importPackage,
  publishAllDraftAtomsAPI: vi.fn(),
  publishKnowledgeBaseAtomsAPI: vi.fn(),
  reindexKnowledgeBaseAtomsAPI: vi.fn(),
  searchKnowledgeBaseAtomsAPI: mocks.searchAtoms,
  updateKnowledgeAtomAPI: mocks.updateAtom,
  validateKnowledgeBaseImportAPI: mocks.validateImport,
  normalizeQuestionBankAtom: (value) => value,
  normalizeQuestionBankAtomPage: (value) => value
}))
vi.mock('@/composables/useQuestionBankBuild', async () => {
  const { computed, ref } = await import('vue')
  return {
    useQuestionBankBuild: (knowledgeBaseId) => ({
      builds: ref([]),
      activeBuildId: ref(null),
      activeBuild: ref(null),
      candidates: ref([]),
      loading: ref(false),
      candidatesLoading: ref(false),
      actionLoading: ref(''),
      canReview: computed(() => false),
      canImport: computed(() => false),
      loadBuilds: async () => unref(knowledgeBaseId) ? mocks.listBuilds(unref(knowledgeBaseId)) : [],
      loadBuild: vi.fn(),
      loadCandidates: vi.fn(),
      startBuild: vi.fn(),
      retryBuild: vi.fn(),
      updateCandidate: vi.fn(),
      importBuild: vi.fn(),
      deleteBuild: vi.fn(),
      runAction: vi.fn()
    })
  }
})

const publicPosition = {
  id: 1,
  name: 'Java 后端开发',
  scope: 'PUBLIC',
  status: 'ACTIVE',
  editable: false,
  canManageAtoms: true,
  canImportPackage: true,
  knowledgeBase: { id: 1, name: 'Java 后端题库' }
}
const privatePosition = {
  id: 15,
  name: '运维工程师',
  scope: 'PRIVATE',
  status: 'ACTIVE',
  editable: true,
  canManageAtoms: true,
  canImportPackage: true,
  knowledgeBase: { id: 15, name: '运维工程师题库' }
}

const deferred = () => {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}

const OverviewStub = defineComponent({
  props: { coverageDetails: { type: Array, default: () => [] } },
  template: '<div data-test="coverage">{{ coverageDetails.map(item => item.category).join(",") }}</div>'
})

const mountWorkspace = () => shallowMount(KnowledgeWorkspace, {
  global: {
    provide: {
      [KNOWLEDGE_WORKSPACE_CAPABILITIES_KEY]: ref({ userMaintenanceEnabled: true, admin: true, canAccessWorkspace: true })
    },
    stubs: { QuestionBankOverviewPanel: OverviewStub }
  }
})

describe('KnowledgeWorkspace position loading', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.listBuilds.mockResolvedValue([])
    mocks.searchAtoms.mockResolvedValue({ items: [], total: 0, page: 1, size: 20 })
    mocks.validateImport.mockResolvedValue({ errors: [] })
    mocks.importPackage.mockResolvedValue({ imported: 1 })
  })

  it('does not request private build history for a public position', async () => {
    mocks.getWorkspace.mockResolvedValue({ positions: [publicPosition] })
    mocks.getCoverage.mockResolvedValue({ details: [] })

    mountWorkspace()
    await flushPromises()

    expect(mocks.listBuilds).not.toHaveBeenCalled()
  })

  it('ignores late coverage responses from the previously selected position', async () => {
    let resolvePrivateCoverage
    const privateCoverage = new Promise((resolve) => { resolvePrivateCoverage = resolve })
    mocks.getWorkspace.mockResolvedValue({ positions: [privatePosition, publicPosition] })
    mocks.getCoverage.mockImplementation((positionId) => positionId === 15
      ? privateCoverage
      : Promise.resolve({ details: [{ category: 'java' }] }))

    const wrapper = mountWorkspace()
    await flushPromises()
    await wrapper.findComponent({ name: 'KnowledgeWorkspaceSidebar' }).vm.$emit('select', publicPosition)
    await nextTick()
    await flushPromises()
    expect(wrapper.get('[data-test="coverage"]').text()).toBe('java')

    resolvePrivateCoverage({ details: [{ category: 'linux' }] })
    await flushPromises()

    expect(wrapper.get('[data-test="coverage"]').text()).toBe('java')
  })

  it('keeps JSON import and atom editing available for a public question bank', async () => {
    mocks.getWorkspace.mockResolvedValue({ positions: [publicPosition] })
    mocks.getCoverage.mockResolvedValue({ details: [] })
    mocks.getAtom.mockResolvedValue({ atomId: 7, subject: 'GC Roots', category: 'jvm' })

    const wrapper = mountWorkspace()
    await flushPromises()
    await wrapper.findComponent({ name: 'KnowledgeWorkspaceTabs' }).vm.$emit('update:modelValue', 'atoms')
    await nextTick()
    await wrapper.findComponent({ name: 'QuestionBankAtomPanel' }).vm.$emit('edit', 7)
    await flushPromises()

    expect(wrapper.findComponent({ name: 'QuestionBankJsonImportCard' }).exists()).toBe(true)
    expect(mocks.getAtom).toHaveBeenCalledWith(7)
    expect(wrapper.findComponent({ name: 'QuestionBankAtomEditDialog' }).props('modelValue')).toBe(true)
  })

  it('does not open an atom from the previously selected knowledge base after switching positions', async () => {
    const oldAtom = deferred()
    mocks.getWorkspace.mockResolvedValue({ positions: [privatePosition, publicPosition] })
    mocks.getCoverage.mockResolvedValue({ details: [] })
    mocks.getAtom.mockReturnValue(oldAtom.promise)

    const wrapper = mountWorkspace()
    await flushPromises()
    await wrapper.findComponent({ name: 'KnowledgeWorkspaceTabs' }).vm.$emit('update:modelValue', 'atoms')
    await nextTick()
    await wrapper.findComponent({ name: 'QuestionBankAtomPanel' }).vm.$emit('edit', 7)
    await wrapper.findComponent({ name: 'KnowledgeWorkspaceSidebar' }).vm.$emit('select', publicPosition)
    await nextTick()
    oldAtom.resolve({ id: 7, subject: '旧岗位原子', category: 'linux' })
    await flushPromises()

    expect(wrapper.findComponent({ name: 'QuestionBankAtomEditDialog' }).props('modelValue')).toBe(false)
  })

  it('does not attach an old JSON validation preview to a newly selected knowledge base', async () => {
    const oldPreview = deferred()
    mocks.getWorkspace.mockResolvedValue({ positions: [privatePosition, publicPosition] })
    mocks.getCoverage.mockResolvedValue({ details: [] })
    mocks.validateImport.mockReturnValue(oldPreview.promise)

    const wrapper = mountWorkspace()
    await flushPromises()
    wrapper.vm.receiveJsonPackage({ schemaVersion: '1.0', atoms: [] }, 'old.json')
    const validation = wrapper.vm.validateJsonPackage()
    await wrapper.findComponent({ name: 'KnowledgeWorkspaceSidebar' }).vm.$emit('select', publicPosition)
    await nextTick()
    oldPreview.resolve({ errors: [], atomCount: 1 })
    await validation

    expect(wrapper.vm.jsonPreview).toBeNull()
  })
})
