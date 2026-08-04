import { describe, expect, it } from 'vitest'
import {
  normalizeVisibleInterviewPositions,
  resolveVisibleInterviewPosition
} from '../interviewEntry'

describe('interview entry utils', () => {
  it('normalizes the read-only visible-position response used by interview setup', () => {
    expect(normalizeVisibleInterviewPositions([
      { id: 7, name: ' Java 后端 ', scope: 'PUBLIC', historyCount: 3 },
      { id: '8', name: 'AI 大模型', scope: 'PRIVATE', hasResumeProfile: true },
      { id: null, name: '无效岗位', scope: 'PUBLIC' },
      { id: 9, name: '   ', scope: 'PUBLIC' }
    ])).toEqual([
      { id: 7, name: 'Java 后端', scope: 'PUBLIC' },
      { id: 8, name: 'AI 大模型', scope: 'PRIVATE' }
    ])
  })

  it('returns an empty list for the former workspace-shaped payload', () => {
    expect(normalizeVisibleInterviewPositions({ positions: [{ id: 7, name: 'Java 后端' }] })).toEqual([])
    expect(normalizeVisibleInterviewPositions(null)).toEqual([])
  })

  it('accepts a query position only when it exists in the visible-position list', () => {
    const positions = [
      { id: 7, name: 'Java 后端', scope: 'PUBLIC' },
      { id: 8, name: 'AI 大模型', scope: 'PUBLIC' }
    ]

    expect(resolveVisibleInterviewPosition(positions, '8', 'Java 后端')).toEqual(positions[1])
    expect(resolveVisibleInterviewPosition(positions, '99', 'Java 后端')).toEqual(positions[0])
    expect(resolveVisibleInterviewPosition(positions, '99', '隐藏私有岗位')).toEqual(positions[0])
  })

  it('returns null instead of inventing a position when no visible position exists', () => {
    expect(resolveVisibleInterviewPosition([], '99', 'Java 后端')).toBeNull()
    expect(resolveVisibleInterviewPosition(null, null, 'Java 后端')).toBeNull()
  })
})
