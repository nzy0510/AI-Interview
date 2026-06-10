import { describe, expect, it } from 'vitest'
import {
  applyProviderPreset,
  buildLlmConfigPayload,
  createLlmConfigDraft,
  createUnknownLlmConfigStatus,
  deriveLlmConfigStatus,
  getLlmProviderLabel,
  isLlmConfigActive,
  isLlmTestSuccess,
  isMissingLlmConfigError,
  sanitizeLlmMessage
} from '../llmConfig'

describe('llm config utils', () => {
  it('creates a provider draft from preset defaults', () => {
    const draft = createLlmConfigDraft('moonshot')

    expect(draft.provider).toBe('moonshot')
    expect(draft.displayName).toBe('Kimi / Moonshot')
    expect(draft.baseUrl).toBe('https://api.moonshot.cn/v1')
  })

  it('applies provider preset without clobbering custom display name', () => {
    const updated = applyProviderPreset({
      provider: 'deepseek',
      displayName: '我的 DeepSeek',
      baseUrl: 'https://api.deepseek.com',
      modelName: 'deepseek-v4-flash'
    }, 'qwen', 'deepseek')

    expect(updated.provider).toBe('qwen')
    expect(updated.displayName).toBe('我的 DeepSeek')
    expect(updated.baseUrl).toBe('https://dashscope.aliyuncs.com/compatible-mode/v1')
    expect(updated.modelName).toBe('qwen-plus')
  })

  it('builds submission payload without leaking empty api key', () => {
    const payload = buildLlmConfigPayload({
      provider: 'deepseek',
      displayName: '主账号',
      baseUrl: 'https://api.deepseek.com',
      modelName: 'deepseek-v4-flash',
      apiKey: '   ',
      temperature: '0.6'
    })

    expect(payload).toEqual({
      provider: 'deepseek',
      displayName: '主账号',
      baseUrl: 'https://api.deepseek.com',
      modelName: 'deepseek-v4-flash',
      temperature: 0.6
    })
  })

  it('derives active status from config list', () => {
    const status = deriveLlmConfigStatus([
      { id: 1, provider: 'deepseek', active: false },
      { id: 2, provider: 'glm', modelName: 'glm-5.1', displayName: '主线路', active: true }
    ])

    expect(status.hasAnyConfig).toBe(true)
    expect(status.hasActiveConfig).toBe(true)
    expect(status.activeProvider).toBe('glm')
    expect(status.activeDisplayName).toBe('主线路')
  })

  it('keeps backward compatibility for enabled field and provider aliases', () => {
    expect(isLlmConfigActive({ enabled: true })).toBe(true)
    expect(getLlmProviderLabel('kimi')).toBe('Kimi / Moonshot')
    expect(getLlmProviderLabel('glm')).toBe('GLM / 智谱')
  })

  it('normalizes saved LLM test statuses from backend', () => {
    expect(isLlmTestSuccess('SUCCESS')).toBe(true)
    expect(isLlmTestSuccess('success')).toBe(true)
    expect(isLlmTestSuccess('FAILED')).toBe(false)
  })

  it('recognizes missing-config backend errors', () => {
    expect(isMissingLlmConfigError(new Error('请先配置并启用 LLM Provider'))).toBe(true)
    expect(isMissingLlmConfigError(new Error('network timeout'))).toBe(false)
  })

  it('sanitizes credentials from backend messages', () => {
    const text = sanitizeLlmMessage('Authorization: Bearer abcdefgh apiKey=sk-1234567890123456')
    expect(text).not.toContain('abcdefgh')
    expect(text).not.toContain('sk-1234567890123456')
  })

  it('exposes provider labels and unknown status defaults', () => {
    expect(getLlmProviderLabel('custom')).toBe('自定义兼容源')
    expect(createUnknownLlmConfigStatus().resolved).toBe(false)
  })
})
