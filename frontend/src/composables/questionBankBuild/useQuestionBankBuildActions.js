import {
  normalizeQuestionBankBuild,
  normalizeQuestionBankCandidate
} from '@/api/knowledgeWorkspace'

export function createQuestionBankBuildActions(context) {
  const {
    api,
    state,
    beginContext,
    isCurrentContext,
    runAction,
    loadBuild,
    loadBuilds,
    polling
  } = context
  const {
    builds,
    activeBuildId,
    activeBuild,
    candidates,
    error,
    canReview,
    canFinalize,
    finalizableCandidates
  } = state

  const startBuild = async (files = [], categories = []) => {
    const requestContext = beginContext()
    const { kbId } = requestContext
    if (!kbId || !files.length) throw new Error('请选择至少一个知识文档')
    error.value = null
    return runAction('start', async () => {
      try {
        const build = normalizeQuestionBankBuild(await api.createBuild(kbId, files, categories))
        if (!isCurrentContext(requestContext)) return build
        activeBuildId.value = build.id
        activeBuild.value = build
        builds.value = [build, ...builds.value.filter((item) => item.id !== build.id)]
        polling.schedule(0)
        return build
      } catch (cause) {
        if (isCurrentContext(requestContext)) error.value = cause
        throw cause
      }
    })
  }
  const retryBuild = async () => {
    const requestContext = beginContext()
    const { kbId } = requestContext
    const buildId = activeBuildId.value
    const jobId = activeBuild.value?.jobId
    if (!kbId || !buildId || !jobId) throw new Error('当前构建没有可重试的任务')
    return runAction('retry', async () => {
      await api.retryBuildJob(jobId)
      polling.armRetry()
      const build = isCurrentContext(requestContext) && activeBuildId.value === buildId
        ? await loadBuild(buildId)
        : null
      polling.schedule(0)
      return build
    })
  }
  const updateCandidate = async (candidateId, action, editable = {}) => {
    const requestContext = beginContext()
    const { kbId } = requestContext
    const buildId = activeBuildId.value
    if (!kbId || !buildId || !candidateId) return null
    if (!canReview.value) throw new Error('构建完成后才能审核候选')
    const expectedReviewRevision = Number(activeBuild.value?.reviewRevision)
    if (!Number.isSafeInteger(expectedReviewRevision) || expectedReviewRevision < 0) {
      throw new Error('构建审核版本缺失，请刷新后重试')
    }
    return runAction(`candidate:${candidateId}`, async () => {
      try {
        const updated = normalizeQuestionBankCandidate(
          await api.updateCandidate(kbId, buildId, candidateId, {
            action,
            ...editable,
            expectedReviewRevision
          }))
        if (!isCurrentContext(requestContext) || activeBuildId.value !== buildId) return updated
        candidates.value = candidates.value.map((item) => item.id === updated.id ? updated : item)
        await loadBuild(buildId)
        return updated
      } catch (cause) {
        if (isCurrentContext(requestContext) && activeBuildId.value === buildId) {
          await Promise.allSettled([context.loadBuild(buildId), context.loadCandidates(buildId)])
        }
        throw cause
      }
    })
  }
  const finalizeBuild = async () => {
    const requestContext = beginContext()
    const { kbId } = requestContext
    const buildId = activeBuildId.value
    if (!kbId || !buildId) throw new Error('请选择构建批次')
    if (!canFinalize.value) throw new Error('当前没有可发布的候选原子，或构建尚未进入终审阶段')
    const candidateIds = finalizableCandidates.value.map((candidate) => candidate.id)
    const expectedReviewRevision = Number(activeBuild.value?.reviewRevision)
    if (!Number.isSafeInteger(expectedReviewRevision) || expectedReviewRevision < 0) {
      throw new Error('构建审核版本缺失，请刷新后重试')
    }
    return runAction('finalize', async () => {
      let response
      try {
        response = await api.finalizeBuild(kbId, buildId, candidateIds, expectedReviewRevision)
      } catch (cause) {
        if (isCurrentContext(requestContext) && activeBuildId.value === buildId) {
          await Promise.allSettled([context.loadBuild(buildId), context.loadCandidates(buildId)])
        }
        throw cause
      }
      if (!isCurrentContext(requestContext) || activeBuildId.value !== buildId) return response
      const build = normalizeQuestionBankBuild({ ...activeBuild.value, ...response })
      activeBuild.value = build
      builds.value = builds.value.map((item) => item.id === buildId ? build : item)
      polling.schedule(0)
      return response
    })
  }
  const repairCandidates = async (candidateIds = [], instruction = '') => {
    const requestContext = beginContext()
    const { kbId } = requestContext
    const buildId = activeBuildId.value
    const ids = Array.from(new Set(candidateIds.map((id) => Number(id)).filter(Number.isSafeInteger)))
    if (!kbId || !buildId) throw new Error('请选择构建批次')
    if (!canReview.value) throw new Error('构建进入终审阶段后才能请求修复')
    if (!ids.length) throw new Error('请选择至少一个需关注的候选')
    const expectedReviewRevision = Number(activeBuild.value?.reviewRevision)
    if (!Number.isSafeInteger(expectedReviewRevision) || expectedReviewRevision < 0) {
      throw new Error('构建审核版本缺失，请刷新后重试')
    }
    return runAction('repair', async () => {
      let build
      try {
        build = normalizeQuestionBankBuild(await api.repairBuild(
          kbId,
          buildId,
          ids,
          String(instruction || '').trim(),
          expectedReviewRevision
        ))
      } catch (cause) {
        if (isCurrentContext(requestContext) && activeBuildId.value === buildId) {
          await Promise.allSettled([context.loadBuild(buildId), context.loadCandidates(buildId)])
        }
        throw cause
      }
      if (!isCurrentContext(requestContext) || activeBuildId.value !== buildId) return build
      activeBuild.value = build
      builds.value = builds.value.map((item) => item.id === buildId ? build : item)
      polling.schedule(0)
      return build
    })
  }
  const deleteBuild = async (buildId = activeBuildId.value) => {
    const requestContext = beginContext()
    const { kbId } = requestContext
    if (!kbId || !buildId) return null
    return runAction('delete', async () => {
      await api.deleteBuild(kbId, buildId)
      if (isCurrentContext(requestContext)) {
        candidates.value = []
        await loadBuilds()
      }
      return buildId
    })
  }

  return { startBuild, retryBuild, updateCandidate, repairCandidates, finalizeBuild, deleteBuild }
}
