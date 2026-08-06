import { computed, ref, unref } from 'vue'
import { createQuestionBankBuildAPI, deleteQuestionBankBuildAPI, getQuestionBankBuildAPI, getQuestionBankBuildCandidatesAPI, importQuestionBankBuildAPI, listQuestionBankBuildsAPI, normalizeQuestionBankBuild, normalizeQuestionBankCandidate, normalizeQuestionBankCandidateList, retryQuestionBankBuildAPI, updateQuestionBankBuildCandidateAPI } from '@/api/knowledgeWorkspace'
import { isQuestionBankCandidateAccepted } from '@/utils/knowledgeWorkspace'

export function useQuestionBankBuild(knowledgeBaseId, apiOverrides = {}) {
  const api = { createBuild: createQuestionBankBuildAPI, listBuilds: listQuestionBankBuildsAPI, getBuild: getQuestionBankBuildAPI, getCandidates: getQuestionBankBuildCandidatesAPI, updateCandidate: updateQuestionBankBuildCandidateAPI, importBuild: importQuestionBankBuildAPI, retryBuildJob: retryQuestionBankBuildAPI, deleteBuild: deleteQuestionBankBuildAPI, ...apiOverrides }
  const builds = ref([])
  const activeBuildId = ref(null)
  const activeBuild = ref(null)
  const candidates = ref([])
  const loading = ref(false)
  const candidatesLoading = ref(false)
  const actionLoading = ref('')
  const error = ref(null)
  const activeCandidates = computed(() => candidates.value)
  const acceptedCandidates = computed(() => candidates.value.filter(isQuestionBankCandidateAccepted))
  const buildCompleted = computed(() => ['COMPLETED', 'SUCCEEDED'].includes(String(activeBuild.value?.status || '').toUpperCase()))
  const canReview = computed(() => Boolean(activeBuildId.value && buildCompleted.value))
  const canImport = computed(() => Boolean(canReview.value && acceptedCandidates.value.length))
  const resolveKnowledgeBaseId = () => unref(knowledgeBaseId)
  let contextKnowledgeBaseId = resolveKnowledgeBaseId()
  let contextGeneration = 0
  let buildListRequest = 0
  let buildDetailRequest = 0
  let candidateRequest = 0
  let actionRequest = 0

  const clearBuildState = () => {
    builds.value = []
    activeBuild.value = null
    activeBuildId.value = null
    candidates.value = []
    loading.value = false
    candidatesLoading.value = false
    error.value = null
  }
  const beginContext = () => {
    const kbId = resolveKnowledgeBaseId()
    if (kbId !== contextKnowledgeBaseId) {
      contextKnowledgeBaseId = kbId
      contextGeneration += 1
      actionRequest += 1
      actionLoading.value = ''
      clearBuildState()
    }
    return { kbId, generation: contextGeneration }
  }
  const isCurrentContext = ({ kbId, generation }) => (
    generation === contextGeneration && kbId === resolveKnowledgeBaseId()
  )

  const runAction = async (action, callback) => {
    const requestId = ++actionRequest
    actionLoading.value = action
    try { return await callback() } finally { if (requestId === actionRequest) actionLoading.value = '' }
  }

  const loadBuild = async (buildId = activeBuildId.value) => {
    const context = beginContext()
    const { kbId } = context
    const requestId = ++buildDetailRequest
    if (!kbId || !buildId) return null
    activeBuildId.value = buildId
    try {
      const build = normalizeQuestionBankBuild(await api.getBuild(kbId, buildId))
      if (!isCurrentContext(context) || requestId !== buildDetailRequest) return null
      activeBuild.value = build
      builds.value = builds.value.map((item) => item.id === build.id ? build : item)
      return build
    } catch (cause) {
      if (!isCurrentContext(context) || requestId !== buildDetailRequest) return null
      throw cause
    }
  }
  const loadBuilds = async () => {
    const context = beginContext()
    const { kbId } = context
    const requestId = ++buildListRequest
    if (!kbId) { clearBuildState(); return [] }
    loading.value = true; error.value = null
    try {
      const loadedBuilds = (await api.listBuilds(kbId)).map(normalizeQuestionBankBuild)
      if (!isCurrentContext(context) || requestId !== buildListRequest) return []
      builds.value = loadedBuilds
      if (!builds.value.some((item) => item.id === activeBuildId.value)) activeBuildId.value = builds.value[0]?.id || null
      if (activeBuildId.value) await loadBuild(activeBuildId.value)
      else activeBuild.value = null
      if (!isCurrentContext(context) || requestId !== buildListRequest) return []
      return builds.value
    } catch (cause) {
      if (!isCurrentContext(context) || requestId !== buildListRequest) return []
      error.value = cause
      throw cause
    } finally {
      if (isCurrentContext(context) && requestId === buildListRequest) loading.value = false
    }
  }
  const loadCandidates = async (buildId = activeBuildId.value) => {
    const context = beginContext()
    const { kbId } = context
    const requestId = ++candidateRequest
    if (!kbId || !buildId) { candidates.value = []; return [] }
    candidatesLoading.value = true
    try {
      const loadedCandidates = normalizeQuestionBankCandidateList(await api.getCandidates(kbId, buildId))
      if (!isCurrentContext(context) || requestId !== candidateRequest) return []
      candidates.value = loadedCandidates
      return candidates.value
    } catch (cause) {
      if (!isCurrentContext(context) || requestId !== candidateRequest) return []
      throw cause
    } finally {
      if (isCurrentContext(context) && requestId === candidateRequest) candidatesLoading.value = false
    }
  }
  const startBuild = async (files = [], categories = []) => {
    const context = beginContext()
    const { kbId } = context
    if (!kbId || !files.length) throw new Error('请选择至少一个知识文档')
    error.value = null
    return runAction('start', async () => {
      try {
        const build = normalizeQuestionBankBuild(await api.createBuild(kbId, files, categories))
        if (!isCurrentContext(context)) return build
        activeBuildId.value = build.id; activeBuild.value = build
        builds.value = [build, ...builds.value.filter((item) => item.id !== build.id)]
        return build
      } catch (cause) {
        if (isCurrentContext(context)) error.value = cause
        throw cause
      }
    })
  }
  const retryBuild = async () => {
    const context = beginContext()
    const { kbId } = context
    const buildId = activeBuildId.value
    const jobId = activeBuild.value?.jobId
    if (!kbId || !buildId || !jobId) throw new Error('当前构建没有可重试的任务')
    return runAction('retry', async () => {
      await api.retryBuildJob(jobId)
      return isCurrentContext(context) && activeBuildId.value === buildId ? loadBuild(buildId) : null
    })
  }
  const updateCandidate = async (candidateId, action, editable = {}) => {
    const context = beginContext()
    const { kbId } = context
    const buildId = activeBuildId.value
    if (!kbId || !buildId || !candidateId) return null
    if (!canReview.value) throw new Error('构建完成后才能审核候选')
    return runAction(`candidate:${candidateId}`, async () => {
      const updated = normalizeQuestionBankCandidate(await api.updateCandidate(kbId, buildId, candidateId, { action, ...editable }))
      if (!isCurrentContext(context) || activeBuildId.value !== buildId) return updated
      const previous = candidates.value.find((item) => item.id === updated.id)
      candidates.value = candidates.value.map((item) => item.id === updated.id ? updated : item)
      const wasAccepted = isQuestionBankCandidateAccepted(previous)
      const isAccepted = isQuestionBankCandidateAccepted(updated)
      const wasRejected = previous?.status === 'REJECTED'
      const isRejected = updated.status === 'REJECTED'
      const adjustCounts = (build) => build
        ? {
          ...build,
          acceptedCount: Math.max(0, Number(build.acceptedCount || 0) + Number(isAccepted) - Number(wasAccepted)),
          rejectedCount: Math.max(0, Number(build.rejectedCount || 0) + Number(isRejected) - Number(wasRejected))
        }
        : build
      activeBuild.value = adjustCounts(activeBuild.value)
      builds.value = builds.value.map((item) => item.id === updated.buildId || item.id === buildId ? adjustCounts(item) : item)
      return updated
    })
  }
  const importBuild = async () => {
    const { kbId } = beginContext()
    const buildId = activeBuildId.value
    if (!kbId || !buildId) throw new Error('请选择构建批次')
    if (!canImport.value) throw new Error('没有可导入的已接受候选')
    return runAction('import', () => api.importBuild(kbId, buildId))
  }
  const deleteBuild = async (buildId = activeBuildId.value) => {
    const context = beginContext()
    const { kbId } = context
    if (!kbId || !buildId) return null
    return runAction('delete', async () => {
      await api.deleteBuild(kbId, buildId)
      if (isCurrentContext(context)) {
        candidates.value = []
        await loadBuilds()
      }
      return buildId
    })
  }
  return { builds, activeBuildId, activeBuild, candidates, activeCandidates, acceptedCandidates, buildCompleted, canReview, canImport, loading, candidatesLoading, actionLoading, error, loadBuild, loadBuilds, loadCandidates, startBuild, retryBuild, updateCandidate, importBuild, deleteBuild, runAction }
}
