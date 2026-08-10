export function createQuestionBankBuildPolling({
  activeBuildId,
  activeBuild,
  candidates,
  error,
  loadBuild,
  loadCandidates
}) {
  let pollingEnabled = false
  let pollingInterval = 2000
  let pollingTimer = null
  let pollingPromise = null
  let retryPollBudget = 0

  const isPollingStage = (build = activeBuild.value) => {
    const status = String(build?.status || '').toUpperCase()
    const stage = String(build?.stage || '').toUpperCase()
    return ['PENDING', 'RUNNING'].includes(status)
      || ['QUEUED', 'PARSING', 'CLASSIFYING', 'GENERATING', 'SUPERVISING', 'REPAIRING', 'RESUPERVISING', 'FINALIZING', 'IMPORTING', 'PUBLISHING', 'INDEXING'].includes(stage)
  }
  const schedule = (delay = pollingInterval) => {
    if (pollingTimer) clearTimeout(pollingTimer)
    pollingTimer = null
    if (!pollingEnabled || !activeBuildId.value || (!isPollingStage() && retryPollBudget <= 0)) return
    if (!isPollingStage() && retryPollBudget > 0) retryPollBudget -= 1
    pollingTimer = setTimeout(() => {
      pollingTimer = null
      void pollOnce()
    }, delay)
  }
  const pollOnce = () => {
    if (!pollingEnabled || !activeBuildId.value) return Promise.resolve(null)
    if (pollingPromise) return pollingPromise
    const buildId = activeBuildId.value
    const previousStage = String(activeBuild.value?.stage || '').toUpperCase()
    pollingPromise = (async () => {
      try {
        const build = await loadBuild(buildId)
        if (build
          && String(build.stage || '').toUpperCase() === 'READY_FOR_FINAL_REVIEW'
          && (previousStage !== 'READY_FOR_FINAL_REVIEW' || candidates.value.length === 0)) {
          await loadCandidates(buildId)
        }
        if (build && (isPollingStage(build)
          || ['COMPLETED', 'SUCCEEDED'].includes(String(build.status || '').toUpperCase()))) {
          retryPollBudget = 0
        }
        return build
      } catch (cause) {
        error.value = cause
        return null
      } finally {
        pollingPromise = null
        schedule()
      }
    })()
    return pollingPromise
  }
  const start = (interval = 2000) => {
    pollingEnabled = true
    pollingInterval = Math.max(50, Number(interval) || 2000)
    return pollOnce()
  }
  const stop = () => {
    pollingEnabled = false
    reset()
  }
  const reset = () => {
    retryPollBudget = 0
    if (pollingTimer) clearTimeout(pollingTimer)
    pollingTimer = null
  }
  const armRetry = () => { retryPollBudget = 15 }

  return { start, stop, reset, schedule, armRetry }
}
