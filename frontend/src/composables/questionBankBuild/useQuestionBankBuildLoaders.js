import {
  normalizeQuestionBankBuild,
  normalizeQuestionBankCandidateList
} from '@/api/knowledgeWorkspace'

export function createQuestionBankBuildLoaders({ api, state, resolveKnowledgeBaseId, onContextChange }) {
  const {
    builds,
    activeBuildId,
    activeBuild,
    candidates,
    loading,
    candidatesLoading,
    actionLoading,
    error
  } = state
  let contextKnowledgeBaseId = resolveKnowledgeBaseId()
  let contextGeneration = 0
  let buildListRequest = 0
  let buildDetailRequest = 0
  let candidateRequest = 0
  let actionRequest = 0

  const clearBuildState = () => {
    onContextChange()
    state.clear()
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
    try { return await callback() } finally {
      if (requestId === actionRequest) actionLoading.value = ''
    }
  }
  const loadBuild = async (buildId = activeBuildId.value) => {
    const requestContext = beginContext()
    const { kbId } = requestContext
    const requestId = ++buildDetailRequest
    if (!kbId || !buildId) return null
    activeBuildId.value = buildId
    try {
      const build = normalizeQuestionBankBuild(await api.getBuild(kbId, buildId))
      if (!isCurrentContext(requestContext) || requestId !== buildDetailRequest) return null
      activeBuild.value = build
      builds.value = builds.value.map((item) => item.id === build.id ? build : item)
      return build
    } catch (cause) {
      if (!isCurrentContext(requestContext) || requestId !== buildDetailRequest) return null
      throw cause
    }
  }
  const loadBuilds = async () => {
    const requestContext = beginContext()
    const { kbId } = requestContext
    const requestId = ++buildListRequest
    if (!kbId) { clearBuildState(); return [] }
    loading.value = true
    error.value = null
    try {
      const loadedBuilds = (await api.listBuilds(kbId)).map(normalizeQuestionBankBuild)
      if (!isCurrentContext(requestContext) || requestId !== buildListRequest) return []
      builds.value = loadedBuilds
      if (!builds.value.some((item) => item.id === activeBuildId.value)) {
        activeBuildId.value = builds.value[0]?.id || null
      }
      if (activeBuildId.value) await loadBuild(activeBuildId.value)
      else activeBuild.value = null
      if (!isCurrentContext(requestContext) || requestId !== buildListRequest) return []
      return builds.value
    } catch (cause) {
      if (!isCurrentContext(requestContext) || requestId !== buildListRequest) return []
      error.value = cause
      throw cause
    } finally {
      if (isCurrentContext(requestContext) && requestId === buildListRequest) loading.value = false
    }
  }
  const loadCandidates = async (buildId = activeBuildId.value) => {
    const requestContext = beginContext()
    const { kbId } = requestContext
    const requestId = ++candidateRequest
    if (!kbId || !buildId) { candidates.value = []; return [] }
    candidatesLoading.value = true
    try {
      const loaded = normalizeQuestionBankCandidateList(await api.getCandidates(kbId, buildId))
      if (!isCurrentContext(requestContext) || requestId !== candidateRequest) return []
      candidates.value = loaded
      return candidates.value
    } catch (cause) {
      if (!isCurrentContext(requestContext) || requestId !== candidateRequest) return []
      throw cause
    } finally {
      if (isCurrentContext(requestContext) && requestId === candidateRequest) {
        candidatesLoading.value = false
      }
    }
  }

  return { beginContext, isCurrentContext, runAction, loadBuild, loadBuilds, loadCandidates }
}
