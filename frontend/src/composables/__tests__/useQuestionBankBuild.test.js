import { afterEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import { useQuestionBankBuild } from '../useQuestionBankBuild'

const deferred = () => {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}

describe('useQuestionBankBuild', () => {
  afterEach(() => vi.useRealTimers())

  it('loads normalized build state and restores the selected build', async () => {
    const api = {
      listBuilds: vi.fn().mockResolvedValue([{ buildId: 3, status: 'RUNNING', progress: 20 }]),
      getBuild: vi.fn().mockResolvedValue({ buildId: 3, status: 'SUCCEEDED', progress: 100 }),
      getCandidates: vi.fn().mockResolvedValue([])
    }
    const state = useQuestionBankBuild(ref(8), api)

    await state.loadBuilds()
    expect(state.builds.value[0]).toMatchObject({ id: 3, progress: 100 })
    expect(state.activeBuild.value).toMatchObject({ id: 3, status: 'SUCCEEDED' })
    expect(api.getBuild).toHaveBeenCalledWith(8, 3)
  })

  it('retries the same build job without creating a second build', async () => {
    const api = {
      createBuild: vi.fn().mockResolvedValue({ buildId: 4, jobId: 99, status: 'QUEUED' }),
      getBuild: vi.fn()
        .mockResolvedValueOnce({ buildId: 4, jobId: 99, status: 'FAILED', errorMessage: 'parse failed' })
        .mockResolvedValueOnce({ buildId: 4, jobId: 99, status: 'RUNNING' }),
      getCandidates: vi.fn().mockResolvedValue([]),
      retryBuildJob: vi.fn().mockResolvedValue({})
    }
    const state = useQuestionBankBuild(8, api)
    const file = new File(['notes'], 'notes.txt', { type: 'text/plain' })

    await state.startBuild([file], ['java'])
    expect(api.createBuild).toHaveBeenCalledWith(8, [file], ['java'])
    await state.retryBuild()
    expect(api.createBuild).toHaveBeenCalledTimes(1)
    expect(api.retryBuildJob).toHaveBeenCalledWith(99)
  })

  it('sends the expected review revision and reloads authoritative counts after candidate review', async () => {
    const api = {
      updateCandidate: vi.fn(async (_kbId, _buildId, _candidateId, { action }) => ({
        candidateId: 1,
        buildId: 5,
        reviewStatus: action === 'ACCEPT' ? 'ACCEPTED' : action === 'REJECT' ? 'REJECTED' : 'PENDING'
      })),
      getBuild: vi.fn()
        .mockResolvedValueOnce({ buildId: 5, status: 'COMPLETED', stage: 'READY_FOR_FINAL_REVIEW', reviewRevision: 5, acceptedCount: 1, rejectedCount: 0 })
        .mockResolvedValueOnce({ buildId: 5, status: 'COMPLETED', stage: 'READY_FOR_FINAL_REVIEW', reviewRevision: 6, acceptedCount: 0, rejectedCount: 1 })
        .mockResolvedValueOnce({ buildId: 5, status: 'COMPLETED', stage: 'READY_FOR_FINAL_REVIEW', reviewRevision: 7, acceptedCount: 0, rejectedCount: 0 })
    }
    const state = useQuestionBankBuild(8, api)
    state.activeBuildId.value = 5
    state.activeBuild.value = { id: 5, status: 'COMPLETED', stage: 'READY_FOR_FINAL_REVIEW', reviewRevision: 4, candidateCount: 2, acceptedCount: 0, rejectedCount: 0 }
    state.builds.value = [{ id: 5, candidateCount: 2, acceptedCount: 0, rejectedCount: 0 }]
    state.candidates.value = [{ id: 1, status: 'PENDING' }, { id: 2, status: 'PENDING' }]

    await state.updateCandidate(1, 'ACCEPT')
    expect(api.updateCandidate).toHaveBeenLastCalledWith(8, 5, 1, { action: 'ACCEPT', expectedReviewRevision: 4 })
    expect(state.activeBuild.value).toMatchObject({ reviewRevision: 5, acceptedCount: 1, rejectedCount: 0 })
    expect(state.builds.value[0]).toMatchObject({ acceptedCount: 1, rejectedCount: 0 })

    await state.updateCandidate(1, 'REJECT')
    expect(api.updateCandidate).toHaveBeenLastCalledWith(8, 5, 1, { action: 'REJECT', expectedReviewRevision: 5 })
    expect(state.activeBuild.value).toMatchObject({ reviewRevision: 6, acceptedCount: 0, rejectedCount: 1 })
    expect(state.builds.value[0]).toMatchObject({ acceptedCount: 0, rejectedCount: 1 })

    await state.updateCandidate(1, 'SAVE')
    expect(api.updateCandidate).toHaveBeenLastCalledWith(8, 5, 1, { action: 'SAVE', expectedReviewRevision: 6 })
    expect(state.activeBuild.value).toMatchObject({ reviewRevision: 7, acceptedCount: 0, rejectedCount: 0 })
    expect(state.builds.value[0]).toMatchObject({ acceptedCount: 0, rejectedCount: 0 })
  })

  it('refreshes build revision and candidates after a conflicting candidate review', async () => {
    const api = {
      updateCandidate: vi.fn().mockRejectedValue(new Error('构建内容已变化')),
      getBuild: vi.fn().mockResolvedValue({ buildId: 5, status: 'RUNNING', stage: 'REPAIRING', reviewRevision: 6 }),
      getCandidates: vi.fn().mockResolvedValue([
        { candidateId: 1, buildId: 5, reviewStatus: 'PENDING', machineReviewStatus: 'NEEDS_HUMAN' }
      ])
    }
    const state = useQuestionBankBuild(8, api)
    state.activeBuildId.value = 5
    state.activeBuild.value = { id: 5, status: 'COMPLETED', stage: 'READY_FOR_FINAL_REVIEW', reviewRevision: 5 }
    state.candidates.value = [{ id: 1, status: 'PENDING', machineReviewStatus: 'AUTO_PASS' }]

    await expect(state.updateCandidate(1, 'ACCEPT')).rejects.toThrow('构建内容已变化')

    expect(api.updateCandidate).toHaveBeenCalledWith(8, 5, 1, { action: 'ACCEPT', expectedReviewRevision: 5 })
    expect(api.getBuild).toHaveBeenCalledWith(8, 5)
    expect(api.getCandidates).toHaveBeenCalledWith(8, 5)
    expect(state.activeBuild.value).toMatchObject({ stage: 'REPAIRING', reviewRevision: 6 })
    expect(state.candidates.value[0]).toMatchObject({ machineReviewStatus: 'NEEDS_HUMAN' })
  })

  it('allows viewing generated candidates but blocks review until completion', async () => {
    const api = {
      getCandidates: vi.fn().mockResolvedValue([{ candidateId: 1, reviewStatus: 'PENDING' }]),
      updateCandidate: vi.fn()
    }
    const state = useQuestionBankBuild(8, api)
    state.activeBuildId.value = 5
    state.activeBuild.value = { id: 5, status: 'RUNNING' }

    await expect(state.loadCandidates()).resolves.toHaveLength(1)
    await expect(state.updateCandidate(1, 'ACCEPT')).rejects.toThrow('构建完成后才能审核候选')
    expect(state.canReview.value).toBe(false)
    expect(api.updateCandidate).not.toHaveBeenCalled()
  })

  it('marks build deletion as an action and refreshes the selected build list', async () => {
    const api = {
      listBuilds: vi.fn().mockResolvedValue([{ buildId: 5, status: 'COMPLETED' }]),
      getBuild: vi.fn().mockResolvedValue({ buildId: 5, status: 'COMPLETED' }),
      deleteBuild: vi.fn().mockResolvedValue({})
    }
    const state = useQuestionBankBuild(8, api)
    await state.loadBuilds()

    await state.deleteBuild()

    expect(api.deleteBuild).toHaveBeenCalledWith(8, 5)
    expect(api.listBuilds).toHaveBeenCalledTimes(2)
    expect(state.actionLoading.value).toBe('')
  })

  it('restores action loading after a download-style action fails', async () => {
    const state = useQuestionBankBuild(8, {})

    await expect(state.runAction('download', async () => { throw new Error('download failed') })).rejects.toThrow('download failed')
    expect(state.actionLoading.value).toBe('')
  })

  it('clears build state without requesting an inaccessible knowledge base', async () => {
    const api = {
      listBuilds: vi.fn().mockResolvedValue([{ buildId: 5, status: 'COMPLETED' }]),
      getBuild: vi.fn().mockResolvedValue({ buildId: 5, status: 'COMPLETED' })
    }
    const knowledgeBaseId = ref(8)
    const state = useQuestionBankBuild(knowledgeBaseId, api)
    await state.loadBuilds()
    state.candidates.value = [{ id: 10, status: 'PENDING' }]
    state.loading.value = true
    state.candidatesLoading.value = true

    knowledgeBaseId.value = null
    await state.loadBuilds()

    expect(state.builds.value).toEqual([])
    expect(state.activeBuildId.value).toBeNull()
    expect(state.activeBuild.value).toBeNull()
    expect(state.candidates.value).toEqual([])
    expect(state.loading.value).toBe(false)
    expect(state.candidatesLoading.value).toBe(false)
    expect(api.listBuilds).toHaveBeenCalledTimes(1)
  })

  it('ignores build and candidate responses from a previously selected private knowledge base', async () => {
    const oldBuilds = deferred()
    const oldCandidates = deferred()
    const knowledgeBaseId = ref(8)
    const api = {
      listBuilds: vi.fn((kbId) => kbId === 8 ? oldBuilds.promise : Promise.resolve([{ buildId: 91, status: 'COMPLETED' }])),
      getBuild: vi.fn((kbId, buildId) => Promise.resolve({ buildId, status: 'COMPLETED', knowledgeBaseId: kbId })),
      getCandidates: vi.fn((kbId) => kbId === 8 ? oldCandidates.promise : Promise.resolve([{ candidateId: 902, buildId: 91, reviewStatus: 'PENDING' }]))
    }
    const state = useQuestionBankBuild(knowledgeBaseId, api)

    const firstBuildLoad = state.loadBuilds()
    knowledgeBaseId.value = 9
    await state.loadBuilds()
    oldBuilds.resolve([{ buildId: 81, status: 'COMPLETED' }])
    await firstBuildLoad

    expect(state.builds.value.map((build) => build.id)).toEqual([91])
    expect(state.activeBuild.value).toMatchObject({ id: 91, knowledgeBaseId: 9 })

    knowledgeBaseId.value = 8
    state.activeBuildId.value = 81
    const firstCandidateLoad = state.loadCandidates(81)
    knowledgeBaseId.value = 9
    state.activeBuildId.value = 91
    await state.loadCandidates(91)
    oldCandidates.resolve([{ candidateId: 801, buildId: 81, reviewStatus: 'PENDING' }])
    await firstCandidateLoad

    expect(state.candidates.value.map((candidate) => candidate.id)).toEqual([902])
  })

  it('publishes finalizable candidates without requiring unresolved attention items to be discarded', async () => {
    const api = {
      getBuild: vi.fn().mockResolvedValue({
        buildId: 5,
        status: 'COMPLETED',
        stage: 'READY_FOR_FINAL_REVIEW',
        reviewRevision: 5
      }),
      finalizeBuild: vi.fn().mockResolvedValue({
        buildId: 5,
        jobId: 77,
        status: 'PENDING',
        stage: 'FINALIZING',
        finalizationStatus: 'NOT_STARTED'
      })
    }
    const state = useQuestionBankBuild(8, api)
    state.activeBuildId.value = 5
    state.activeBuild.value = {
      id: 5,
      buildId: 5,
      status: 'COMPLETED',
      stage: 'READY_FOR_FINAL_REVIEW',
      reviewRevision: 4
    }
    state.candidates.value = [
      { id: 1, candidateId: 1, status: 'PENDING', machineReviewStatus: 'AUTO_PASS' },
      { id: 2, candidateId: 2, status: 'PENDING', machineReviewStatus: 'NEEDS_HUMAN' },
      { id: 3, candidateId: 3, status: 'PENDING', machineReviewStatus: 'AUTO_REJECT' },
      { id: 4, candidateId: 4, status: 'PENDING', machineReviewStatus: 'UNRECOGNIZED' }
    ]

    expect(state.exceptionCandidates.value.map((item) => item.id)).toEqual([2, 4])
    expect(state.finalizableCandidates.value.map((item) => item.id)).toEqual([1])
    expect(state.canFinalize.value).toBe(true)

    await state.finalizeBuild()

    expect(api.finalizeBuild).toHaveBeenCalledWith(
      8,
      5,
      [1],
      4
    )
    expect(api.getBuild).not.toHaveBeenCalled()
    expect(state.activeBuild.value).toMatchObject({ jobId: 77, stage: 'FINALIZING', status: 'PENDING' })
  })

  it('starts assistant repair with selected candidates and the current review revision', async () => {
    const api = {
      repairBuild: vi.fn().mockResolvedValue({
        buildId: 5,
        status: 'RUNNING',
        stage: 'REPAIRING',
        reviewRevision: 4,
        repairRound: 1
      })
    }
    const state = useQuestionBankBuild(8, api)
    state.activeBuildId.value = 5
    state.activeBuild.value = {
      id: 5,
      buildId: 5,
      status: 'COMPLETED',
      stage: 'READY_FOR_FINAL_REVIEW',
      reviewRevision: 4
    }
    state.builds.value = [state.activeBuild.value]
    state.candidates.value = [
      { id: 2, candidateId: 2, status: 'PENDING', machineReviewStatus: 'NEEDS_HUMAN' }
    ]

    await state.repairCandidates([2], '只依据原文修正')

    expect(api.repairBuild).toHaveBeenCalledWith(8, 5, [2], '只依据原文修正', 4)
    expect(state.activeBuild.value).toMatchObject({ stage: 'REPAIRING', repairRound: 1 })
    expect(state.canReview.value).toBe(false)
  })

  it('polls active stages and automatically loads exceptions when supervision finishes', async () => {
    vi.useFakeTimers()
    const api = {
      getBuild: vi.fn()
        .mockResolvedValueOnce({ buildId: 5, status: 'RUNNING', stage: 'SUPERVISING', progress: 70 })
        .mockResolvedValueOnce({ buildId: 5, status: 'COMPLETED', stage: 'READY_FOR_FINAL_REVIEW', progress: 100 }),
      getCandidates: vi.fn().mockResolvedValue([
        { candidateId: 2, reviewStatus: 'PENDING', machineReviewStatus: 'NEEDS_HUMAN' }
      ])
    }
    const state = useQuestionBankBuild(8, api)
    state.activeBuildId.value = 5
    state.activeBuild.value = { id: 5, status: 'PENDING', stage: 'QUEUED' }

    await state.startPolling(100)
    expect(state.activeBuild.value).toMatchObject({ stage: 'SUPERVISING', progress: 70 })

    await vi.advanceTimersByTimeAsync(100)

    expect(state.activeBuild.value).toMatchObject({ stage: 'READY_FOR_FINAL_REVIEW', progress: 100 })
    expect(api.getCandidates).toHaveBeenCalledWith(8, 5)
    expect(state.exceptionCandidates.value.map((item) => item.id)).toEqual([2])
    expect(vi.getTimerCount()).toBe(0)
    state.stopPolling()
  })

  it('keeps polling through assistant repair and re-supervision, then refreshes candidates', async () => {
    vi.useFakeTimers()
    const api = {
      getBuild: vi.fn()
        .mockResolvedValueOnce({ buildId: 5, status: 'RUNNING', stage: 'REPAIRING', progress: 78 })
        .mockResolvedValueOnce({ buildId: 5, status: 'RUNNING', stage: 'RESUPERVISING', progress: 88 })
        .mockResolvedValueOnce({ buildId: 5, status: 'COMPLETED', stage: 'READY_FOR_FINAL_REVIEW', progress: 100 }),
      getCandidates: vi.fn().mockResolvedValue([
        { candidateId: 2, reviewStatus: 'PENDING', machineReviewStatus: 'AUTO_PASS', repairStatus: 'SUCCEEDED', repairAttempts: 1 }
      ])
    }
    const state = useQuestionBankBuild(8, api)
    state.activeBuildId.value = 5
    state.activeBuild.value = { id: 5, status: 'RUNNING', stage: 'REPAIRING' }

    await state.startPolling(100)
    expect(state.activeBuild.value.stage).toBe('REPAIRING')
    await vi.advanceTimersToNextTimerAsync()
    expect(state.activeBuild.value.stage).toBe('RESUPERVISING')
    await vi.advanceTimersToNextTimerAsync()

    expect(state.activeBuild.value.stage).toBe('READY_FOR_FINAL_REVIEW')
    expect(api.getCandidates).toHaveBeenCalledWith(8, 5)
    expect(state.repairedCandidates.value.map((candidate) => candidate.id)).toEqual([2])
    expect(vi.getTimerCount()).toBe(0)
  })

  it('keeps polling when retry dispatch has not updated the failed build yet', async () => {
    vi.useFakeTimers()
    const api = {
      retryBuildJob: vi.fn().mockResolvedValue({}),
      getBuild: vi.fn()
        .mockResolvedValueOnce({ buildId: 5, jobId: 90, status: 'FAILED', stage: 'FAILED', updateTime: '2026-08-10T01:00:00' })
        .mockResolvedValueOnce({ buildId: 5, jobId: 90, status: 'FAILED', stage: 'FAILED', updateTime: '2026-08-10T01:00:00' })
        .mockResolvedValueOnce({ buildId: 5, jobId: 90, status: 'RUNNING', stage: 'GENERATING', progress: 20 })
        .mockResolvedValueOnce({ buildId: 5, jobId: 90, status: 'COMPLETED', stage: 'READY_FOR_FINAL_REVIEW', progress: 100 }),
      getCandidates: vi.fn().mockResolvedValue([])
    }
    const state = useQuestionBankBuild(8, api)
    state.activeBuildId.value = 5
    state.activeBuild.value = { id: 5, jobId: 90, status: 'FAILED', stage: 'FAILED', canRetry: true }
    await state.startPolling(100)

    await state.retryBuild()
    expect(state.activeBuild.value.status).toBe('FAILED')
    expect(vi.getTimerCount()).toBe(1)

    await vi.advanceTimersToNextTimerAsync()
    expect(state.activeBuild.value.status).toBe('RUNNING')
    await vi.advanceTimersToNextTimerAsync()
    expect(state.activeBuild.value.stage).toBe('READY_FOR_FINAL_REVIEW')
    expect(vi.getTimerCount()).toBe(0)
  })
})
