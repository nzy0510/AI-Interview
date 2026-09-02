import { describe, expect, it } from 'vitest'

import { resolveMentorPosition } from '../mentor'

const positions = [
  { id: 1, name: 'Java 后端开发', scope: 'PUBLIC', historyCount: 0 },
  { id: 2, name: 'AI 大模型应用开发', scope: 'PRIVATE', historyCount: 3 }
]

describe('AI Mentor position resolution', () => {
  it('prefers a visible position explicitly requested by URL', () => {
    expect(resolveMentorPosition(positions, '1', [{ positionId: 2 }])).toEqual(positions[0])
  })

  it('falls back to the latest visible structured interview position', () => {
    const history = [
      { id: 30, positionId: null },
      { id: 29, positionId: 99 },
      { id: 28, positionId: 2 }
    ]

    expect(resolveMentorPosition(positions, '99', history)).toEqual(positions[1])
  })

  it('falls back to the first visible position without filtering zero-history positions', () => {
    expect(resolveMentorPosition(positions, null, [])).toEqual(positions[0])
  })

  it('returns null when no visible position exists', () => {
    expect(resolveMentorPosition([], '1', [{ positionId: 1 }])).toBeNull()
  })

  it('can require a recent visible structured interview without first-position fallback', () => {
    expect(resolveMentorPosition(positions, null, [{ positionId: null }], false)).toBeNull()
  })
})
