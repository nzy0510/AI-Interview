import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'

import { useQuestionBankBuild } from '../useQuestionBankBuild'

const deferred = () => {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}

describe('useQuestionBankBuild', () => {
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

  it('retries the same job and never imports before accepted candidates', async () => {
    const api = {
      createBuild: vi.fn().mockResolvedValue({ buildId: 4, jobId: 99, status: 'QUEUED' }),
      getBuild: vi.fn()
        .mockResolvedValueOnce({ buildId: 4, jobId: 99, status: 'FAILED', errorMessage: 'parse failed' })
        .mockResolvedValueOnce({ buildId: 4, jobId: 99, status: 'RUNNING' }),
      getCandidates: vi.fn().mockResolvedValue([]),
      importBuild: vi.fn(),
      retryBuildJob: vi.fn().mockResolvedValue({})
    }
    const state = useQuestionBankBuild(8, api)
    const file = new File(['notes'], 'notes.txt', { type: 'text/plain' })

    await state.startBuild([file], ['java'])
    expect(api.createBuild).toHaveBeenCalledWith(8, [file], ['java'])
    await state.retryBuild()
    expect(api.createBuild).toHaveBeenCalledTimes(1)
    expect(api.retryBuildJob).toHaveBeenCalledWith(99)
    await expect(state.importBuild()).rejects.toThrow('没有可导入的已接受候选')
    expect(api.importBuild).not.toHaveBeenCalled()
  })

  it('keeps review counts in sync after accept, reject, and save actions', async () => {
    const api = {
      updateCandidate: vi.fn(async (_kbId, _buildId, _candidateId, { action }) => ({
        candidateId: 1,
        buildId: 5,
        reviewStatus: action === 'ACCEPT' ? 'ACCEPTED' : action === 'REJECT' ? 'REJECTED' : 'PENDING'
      }))
    }
    const state = useQuestionBankBuild(8, api)
    state.activeBuildId.value = 5
    state.activeBuild.value = { id: 5, status: 'COMPLETED', candidateCount: 2, acceptedCount: 0, rejectedCount: 0 }
    state.builds.value = [{ id: 5, candidateCount: 2, acceptedCount: 0, rejectedCount: 0 }]
    state.candidates.value = [{ id: 1, status: 'PENDING' }, { id: 2, status: 'PENDING' }]

    await state.updateCandidate(1, 'ACCEPT')
    expect(state.activeBuild.value).toMatchObject({ acceptedCount: 1, rejectedCount: 0 })
    expect(state.builds.value[0]).toMatchObject({ acceptedCount: 1, rejectedCount: 0 })

    await state.updateCandidate(1, 'REJECT')
    expect(state.activeBuild.value).toMatchObject({ acceptedCount: 0, rejectedCount: 1 })
    expect(state.builds.value[0]).toMatchObject({ acceptedCount: 0, rejectedCount: 1 })

    await state.updateCandidate(1, 'SAVE')
    expect(state.activeBuild.value).toMatchObject({ acceptedCount: 0, rejectedCount: 0 })
    expect(state.builds.value[0]).toMatchObject({ acceptedCount: 0, rejectedCount: 0 })
  })

  it('allows viewing generated candidates but blocks review and import until completion', async () => {
    const api = {
      getCandidates: vi.fn().mockResolvedValue([{ candidateId: 1, reviewStatus: 'PENDING' }]),
      updateCandidate: vi.fn(),
      importBuild: vi.fn()
    }
    const state = useQuestionBankBuild(8, api)
    state.activeBuildId.value = 5
    state.activeBuild.value = { id: 5, status: 'RUNNING' }

    await expect(state.loadCandidates()).resolves.toHaveLength(1)
    await expect(state.updateCandidate(1, 'ACCEPT')).rejects.toThrow('构建完成后才能审核候选')
    expect(state.canReview.value).toBe(false)
    expect(state.canImport.value).toBe(false)
    expect(api.updateCandidate).not.toHaveBeenCalled()
    expect(api.importBuild).not.toHaveBeenCalled()
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
})
