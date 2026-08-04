import { describe, expect, it } from 'vitest'
import {
  getFriendlyToolLabels,
  getOrchestrationFallbackLabel,
  getOrchestrationModePresentation,
  normalizeOrchestrationEvent,
  parseInterviewSseData
} from '../interviewOrchestration'

describe('interview orchestration utils', () => {
  it('normalizes an auditable orchestration event without exposing private fields', () => {
    expect(normalizeOrchestrationEvent({
      type: 'orchestration',
      mode: 'AGENT',
      action: 'DEEPEN',
      summary: '  继续深挖分布式锁的异常场景  ',
      tools: ['searchPositionKnowledge', 'searchPositionKnowledge', '', 42],
      prompt: 'private prompt',
      reasoning: 'private reasoning'
    })).toEqual({
      mode: 'AGENT',
      action: 'DEEPEN',
      summary: '继续深挖分布式锁的异常场景',
      tools: ['searchPositionKnowledge']
    })
  })

  it('keeps a timeout fallback category for a rule fallback decision', () => {
    expect(normalizeOrchestrationEvent({
      type: 'orchestration',
      mode: 'RULE_FALLBACK',
      action: 'CONTINUE_PHASE',
      summary: '下一轮将自动重试 Agent 规划',
      tools: [],
      fallbackCategory: 'TIMEOUT'
    })).toMatchObject({
      mode: 'RULE_FALLBACK',
      fallbackCategory: 'TIMEOUT'
    })
  })

  it('keeps only whitelisted categories from rule fallback decisions', () => {
    const allowedCategories = ['TIMEOUT', 'PROVIDER', 'TOOL', 'OUTPUT', 'MODEL', 'SYSTEM']

    allowedCategories.forEach((fallbackCategory) => {
      expect(normalizeOrchestrationEvent({
        type: 'orchestration',
        mode: 'RULE_FALLBACK',
        action: 'CONTINUE_PHASE',
        fallbackCategory
      })).toHaveProperty('fallbackCategory', fallbackCategory)
    })

    expect(normalizeOrchestrationEvent({
      type: 'orchestration',
      mode: 'RULE_FALLBACK',
      action: 'CONTINUE_PHASE',
      fallbackCategory: 'java.net.SocketTimeoutException'
    })).not.toHaveProperty('fallbackCategory')

    for (const mode of ['AGENT', 'RULE']) {
      expect(normalizeOrchestrationEvent({
        type: 'orchestration',
        mode,
        action: 'CONTINUE_PHASE',
        fallbackCategory: 'TIMEOUT'
      })).not.toHaveProperty('fallbackCategory')
    }
  })

  it('rejects malformed orchestration events', () => {
    expect(normalizeOrchestrationEvent(null)).toBeNull()
    expect(normalizeOrchestrationEvent({ type: 'orchestration', mode: 'UNKNOWN', action: 'DEEPEN' })).toBeNull()
    expect(normalizeOrchestrationEvent({ type: 'orchestration', mode: 'AGENT', action: 'UNKNOWN' })).toBeNull()
    expect(normalizeOrchestrationEvent({ type: 'content', mode: 'AGENT', action: 'DEEPEN' })).toBeNull()
  })

  it('bounds public summaries and tool badges to keep the indicator compact', () => {
    const normalized = normalizeOrchestrationEvent({
      type: 'orchestration',
      mode: 'AGENT',
      action: 'DEEPEN',
      summary: '策'.repeat(220),
      tools: ['one', 'two', 'three', 'four']
    })

    expect(normalized.summary).toHaveLength(180)
    expect(normalized.tools).toEqual(['one', 'two', 'three'])
  })

  it('parses orchestration without changing existing SSE event semantics', () => {
    expect(parseInterviewSseData(JSON.stringify({
      type: 'orchestration',
      mode: 'RULE_FALLBACK',
      action: 'SWITCH_TOPIC',
      summary: '自动切换到稳定规则',
      tools: [],
      fallbackCategory: 'TIMEOUT'
    }))).toMatchObject({
      kind: 'orchestration',
      data: { mode: 'RULE_FALLBACK', fallbackCategory: 'TIMEOUT' }
    })

    expect(parseInterviewSseData(JSON.stringify({ phase: 'HR', ignored: 'value' })))
      .toEqual({ kind: 'phase', data: 'HR' })
    expect(parseInterviewSseData(JSON.stringify({ done: true, ignored: 'value' })))
      .toEqual({ kind: 'done', data: true })
    expect(parseInterviewSseData(JSON.stringify({ content: '下一问', ignored: 'value' })))
      .toEqual({ kind: 'content', data: '下一问' })
    expect(parseInterviewSseData('{bad json')).toBeNull()
    expect(parseInterviewSseData(JSON.stringify({ unknown: true }))).toEqual({ kind: 'unknown', data: null })
  })

  it('provides the three user-facing modes and friendly tool labels', () => {
    expect(getOrchestrationModePresentation('AGENT').label).toBe('Agent')
    expect(getOrchestrationModePresentation('RULE').label).toBe('稳定规则')
    expect(getOrchestrationModePresentation('RULE_FALLBACK').label).toBe('自动回退')
    expect(getFriendlyToolLabels([
      'searchPositionKnowledge',
      'getCurrentResumeEvidence',
      'getPositionLearningCoverage',
      'futureReadOnlyTool'
    ])).toEqual(['岗位知识', '简历证据', '学习覆盖', '只读工具'])
  })

  it('provides concise labels only for known rule fallback categories', () => {
    expect([
      'TIMEOUT',
      'PROVIDER',
      'TOOL',
      'OUTPUT',
      'MODEL',
      'SYSTEM'
    ].map((category) => getOrchestrationFallbackLabel('RULE_FALLBACK', category))).toEqual([
      '规划超时',
      '模型服务异常',
      '工具调用失败',
      '规划输出异常',
      '模型规划失败',
      '编排异常'
    ])

    expect(getOrchestrationFallbackLabel('AGENT', 'TIMEOUT')).toBe('')
    expect(getOrchestrationFallbackLabel('RULE_FALLBACK', 'SocketTimeoutException')).toBe('')
  })
})
