import { describe, expect, it } from 'vitest'
import { normalizeKnowledgePoints } from '../reportMetrics'

describe('normalizeKnowledgePoints', () => {
  it('parses interview knowledge points into sorted bar values', () => {
    const result = normalizeKnowledgePoints(JSON.stringify([
      { concept: 'Redis持久化', mastery: 0.6, category: '缓存技术' },
      { concept: 'Spring Boot自动配置', mastery: 0.9, category: '框架原理' },
    ]))

    expect(result).toEqual([
      { concept: 'Spring Boot自动配置', category: '框架原理', percent: 90 },
      { concept: 'Redis持久化', category: '缓存技术', percent: 60 },
    ])
  })

  it('returns empty points for malformed knowledge json', () => {
    expect(normalizeKnowledgePoints('{oops')).toEqual([])
  })

  it('clamps mastery into a display-safe percentage', () => {
    const result = normalizeKnowledgePoints([
      { concept: '过高', mastery: 1.6 },
      { concept: '过低', mastery: -0.2 },
      { concept: '缺失' },
    ])

    expect(result).toEqual([
      { concept: '过高', category: '综合', percent: 100 },
      { concept: '过低', category: '综合', percent: 0 },
      { concept: '缺失', category: '综合', percent: 0 },
    ])
  })
})
