import { unref } from 'vue'
import {
  createQuestionBankBuildAPI,
  deleteQuestionBankBuildAPI,
  finalizeQuestionBankBuildAPI,
  getQuestionBankBuildAPI,
  getQuestionBankBuildCandidatesAPI,
  listQuestionBankBuildsAPI,
  retryQuestionBankBuildAPI,
  updateQuestionBankBuildCandidateAPI
} from '@/api/knowledgeWorkspace'
import { createQuestionBankBuildActions } from './questionBankBuild/useQuestionBankBuildActions'
import { createQuestionBankBuildLoaders } from './questionBankBuild/useQuestionBankBuildLoaders'
import { createQuestionBankBuildPolling } from './questionBankBuild/useQuestionBankBuildPolling'
import { createQuestionBankBuildState } from './questionBankBuild/useQuestionBankBuildState'

export function useQuestionBankBuild(knowledgeBaseId, apiOverrides = {}) {
  const api = {
    createBuild: createQuestionBankBuildAPI,
    listBuilds: listQuestionBankBuildsAPI,
    getBuild: getQuestionBankBuildAPI,
    getCandidates: getQuestionBankBuildCandidatesAPI,
    updateCandidate: updateQuestionBankBuildCandidateAPI,
    finalizeBuild: finalizeQuestionBankBuildAPI,
    retryBuildJob: retryQuestionBankBuildAPI,
    deleteBuild: deleteQuestionBankBuildAPI,
    ...apiOverrides
  }
  const state = createQuestionBankBuildState()
  let polling = { reset: () => {} }
  const loaders = createQuestionBankBuildLoaders({
    api,
    state,
    resolveKnowledgeBaseId: () => unref(knowledgeBaseId),
    onContextChange: () => polling.reset()
  })
  const { beginContext, isCurrentContext, runAction, loadBuild, loadBuilds, loadCandidates } = loaders

  polling = createQuestionBankBuildPolling({
    activeBuildId: state.activeBuildId,
    activeBuild: state.activeBuild,
    candidates: state.candidates,
    error: state.error,
    loadBuild,
    loadCandidates
  })
  const actions = createQuestionBankBuildActions({
    api,
    state,
    beginContext,
    isCurrentContext,
    runAction,
    loadBuild,
    loadBuilds,
    loadCandidates,
    polling
  })

  return {
    ...state,
    ...actions,
    loadBuild,
    loadBuilds,
    loadCandidates,
    startPolling: polling.start,
    stopPolling: polling.stop,
    runAction
  }
}
