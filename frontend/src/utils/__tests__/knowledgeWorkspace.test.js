import { describe, expect, it } from 'vitest'
import {
  canUploadToPosition,
  findLatestJobForSourceFile,
  canApplySuggestedPatch,
  canPublishAtom,
  canRetryJob,
  countPublishableAtoms,
  generationCompletionMessage,
  isCompletedAtomGenerationJob,
  getFileStatusLabel,
  getAtomReviewLabel,
  getAtomReviewType,
  getPublicationStatusLabel,
  isPositionEditable
} from '../knowledgeWorkspace'

describe('knowledge workspace utils', () => {
  it('keeps public positions read-only', () => {
    expect(isPositionEditable({ scope: 'PUBLIC', editable: false, status: 'ACTIVE' })).toBe(false)
    expect(canUploadToPosition({ scope: 'PUBLIC', editable: false, status: 'ACTIVE', knowledgeBase: { id: 1 } })).toBe(false)
  })

  it('allows upload only for active private positions with a default knowledge base', () => {
    expect(canUploadToPosition({ scope: 'PRIVATE', editable: true, status: 'ACTIVE', knowledgeBase: { id: 9 } })).toBe(true)
    expect(canUploadToPosition({ scope: 'PRIVATE', editable: true, status: 'ARCHIVED', knowledgeBase: { id: 9 } })).toBe(false)
    expect(canUploadToPosition({ scope: 'PRIVATE', editable: true, status: 'ACTIVE', knowledgeBase: null })).toBe(false)
  })

  it('finds the latest job for a source file', () => {
    const job = findLatestJobForSourceFile([
      { id: 1, sourceFileId: 10, status: 'FAILED' },
      { id: 3, sourceFileId: 10, status: 'COMPLETED' },
      { id: 2, sourceFileId: 11, status: 'RUNNING' }
    ], 10)

    expect(job.status).toBe('COMPLETED')
    expect(getFileStatusLabel('CONVERTED')).toBe('已转换')
  })

  it('labels atom review and publication states', () => {
    expect(getAtomReviewLabel('PASS')).toBe('通过')
    expect(getAtomReviewLabel('NEEDS_REVIEW')).toBe('需处理')
    expect(getAtomReviewType('REJECT')).toBe('danger')
    expect(getPublicationStatusLabel('PUBLISHED')).toBe('已发布')
  })

  it('allows publishing only reviewed non-rejected draft atoms', () => {
    expect(canPublishAtom({ reviewStatus: 'PASS', publicationStatus: 'DRAFT' })).toBe(true)
    expect(canPublishAtom({ reviewStatus: 'NEEDS_REVIEW', publicationStatus: 'DRAFT' })).toBe(false)
    expect(canPublishAtom({ reviewStatus: 'REJECT', publicationStatus: 'DRAFT' })).toBe(false)
    expect(canPublishAtom({ reviewStatus: 'PASS', publicationStatus: 'PUBLISHED' })).toBe(false)
    expect(canApplySuggestedPatch({ reviewStatus: 'NEEDS_REVIEW', suggestedPatchJson: '{"subject":"x"}' })).toBe(true)
  })

  it('allows retrying only failed retryable jobs', () => {
    expect(canRetryJob({ status: 'FAILED', retryable: true })).toBe(true)
    expect(canRetryJob({ status: 'FAILED', retryable: false })).toBe(false)
    expect(canRetryJob({ status: 'RUNNING', retryable: true })).toBe(false)
    expect(canRetryJob(null)).toBe(false)
  })

  it('counts publishable atoms for bulk publish', () => {
    expect(countPublishableAtoms([
      { reviewStatus: 'PASS', publicationStatus: 'DRAFT' },
      { reviewStatus: 'PASS', publicationStatus: 'PUBLISHED' },
      { reviewStatus: 'NEEDS_REVIEW', publicationStatus: 'DRAFT' }
    ])).toBe(1)
  })

  it('describes completed generation jobs from result json', () => {
    const job = {
      jobType: 'GENERATE_ATOMS',
      status: 'COMPLETED',
      resultJson: '{"imported":60,"received":60,"atomLimitReached":false}'
    }

    expect(isCompletedAtomGenerationJob(job)).toBe(true)
    expect(generationCompletionMessage(job)).toContain('已生成 60 条')
    expect(generationCompletionMessage({
      ...job,
      resultJson: '{"imported":100,"received":120,"atomLimitReached":true}'
    })).toContain('已达到单次上限 100 条')
  })
})
