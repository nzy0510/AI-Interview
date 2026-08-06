import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  default: vi.fn()
}))

import request from '@/utils/request'
import {
  createQuestionBankBuildAPI,
  getKnowledgeAtomAPI,
  getQuestionBankBuildCandidatesAPI,
  normalizeQuestionBankAtom,
  normalizeQuestionBankBuild,
  normalizeQuestionBankCandidate,
  updateKnowledgeAtomAPI
} from '../knowledgeWorkspace'

describe('question bank build API adapters', () => {
  beforeEach(() => {
    request.mockReset()
    request.mockResolvedValue({})
  })

  it('posts repeated files and categories as multipart form data', async () => {
    const first = new File(['one'], 'guide.pdf', { type: 'application/pdf' })
    const second = new File(['two'], 'notes.txt', { type: 'text/plain' })

    await createQuestionBankBuildAPI(42, [first, second], ['java', 'jvm'])

    const config = request.mock.calls[0][0]
    expect(config.url).toBe('/knowledge-workspace/knowledge-bases/42/builds')
    expect(config.method).toBe('post')
    expect(config.data).toBeInstanceOf(FormData)
    expect(config.data.getAll('files').map((file) => file.name)).toEqual(['guide.pdf', 'notes.txt'])
    expect(config.data.getAll('categories')).toEqual(['java', 'jvm'])
  })

  it('normalizes backend build and candidate fields for components', () => {
    const build = normalizeQuestionBankBuild({
      buildId: 9,
      state: 'RUNNING',
      currentStage: 'GENERATING',
      progressPercent: 41,
      llm: { configured: true, provider: 'glm', modelName: 'glm-4' },
      sourceFiles: [{ name: 'guide.pdf', pages: 4 }],
      estimatedChunkCount: 3,
      estimatedCallCount: 6
    })
    expect(build).toMatchObject({
      id: 9,
      status: 'RUNNING',
      stage: 'GENERATING',
      progress: 41,
      provider: 'glm',
      model: 'glm-4',
      llmConfigured: true,
      fileCount: 1,
      expectedChunks: 3,
      estimatedCalls: 6
    })

    const candidate = normalizeQuestionBankCandidate({
      candidateId: 11,
      reviewStatus: 'PENDING',
      atom: {
        id: 'java-001',
        subject: 'GC Roots',
        content: { principles: 'answer', pitfalls: 'trap', followUpPaths: ['why?'] }
      },
      sourceEvidence: { fileName: 'guide.pdf', page: 2, quote: 'evidence' }
    })
    expect(candidate).toMatchObject({
      id: 11,
      status: 'PENDING',
      subject: 'GC Roots',
      principles: 'answer',
      pitfalls: 'trap',
      followUpPaths: ['why?'],
      source: { fileName: 'guide.pdf', page: 2, quote: 'evidence' }
    })
  })

  it('uses knowledge base and build ids when loading candidates', async () => {
    await getQuestionBankBuildCandidatesAPI(42, 9)
    expect(request).toHaveBeenCalledWith(expect.objectContaining({
      url: '/knowledge-workspace/knowledge-bases/42/builds/9/candidates',
      method: 'get'
    }))
  })

  it('normalizes editable atom fields and uses the protected detail endpoints', async () => {
    expect(normalizeQuestionBankAtom({
      id: 7,
      tagsJson: '["jvm","gc"]',
      principles: '从一组根对象开始追踪',
      pitfalls: '可达不等于永远存活',
      followUpPathsJson: '["哪些对象属于 GC Roots？"]'
    })).toMatchObject({
      atomId: 7,
      tags: ['jvm', 'gc'],
      principles: '从一组根对象开始追踪',
      pitfalls: '可达不等于永远存活',
      followUpPaths: ['哪些对象属于 GC Roots？']
    })

    request.mockResolvedValueOnce({ id: 7, subject: 'GC Roots' })
    await getKnowledgeAtomAPI(7)
    expect(request).toHaveBeenLastCalledWith(expect.objectContaining({ url: '/knowledge-atoms/7', method: 'get' }))

    request.mockResolvedValueOnce({ id: 7, subject: 'GC Roots（已修订）' })
    await updateKnowledgeAtomAPI(7, { subject: 'GC Roots（已修订）' })
    expect(request).toHaveBeenLastCalledWith(expect.objectContaining({
      url: '/knowledge-atoms/7',
      method: 'put',
      data: { subject: 'GC Roots（已修订）' }
    }))
  })
})
