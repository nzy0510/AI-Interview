import { describe, expect, it } from 'vitest'
import {
  canUploadToPosition,
  findLatestJobForSourceFile,
  getFileStatusLabel,
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
})
