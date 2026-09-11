import { describe, expect, it } from 'vitest'
import { parseInterviewSseData } from '../interviewSse'

describe('parseInterviewSseData', () => {
  it('parses phase, content and both completion formats', () => {
    expect(parseInterviewSseData(JSON.stringify({ phase: 'HR', ignored: 'value' })))
      .toEqual({ kind: 'phase', data: 'HR' })
    expect(parseInterviewSseData(JSON.stringify({ content: '下一问', ignored: 'value' })))
      .toEqual({ kind: 'content', data: '下一问' })
    expect(parseInterviewSseData(JSON.stringify({ content: '' })))
      .toEqual({ kind: 'content', data: '' })

    for (const done of [true, 'true']) {
      expect(parseInterviewSseData(JSON.stringify({ done, ignored: 'value' })))
        .toEqual({ kind: 'done', data: true })
    }
  })

  it('preserves error and event precedence', () => {
    expect(parseInterviewSseData(JSON.stringify({ error: '模型服务异常', phase: 'HR', done: true, content: '下一问' })))
      .toEqual({ kind: 'error', data: '模型服务异常' })
    expect(parseInterviewSseData(JSON.stringify({ error: 503 })))
      .toEqual({ kind: 'error', data: '503' })
    expect(parseInterviewSseData(JSON.stringify({ phase: 'HR', done: true, content: '下一问' })))
      .toEqual({ kind: 'phase', data: 'HR' })
    expect(parseInterviewSseData(JSON.stringify({ done: true, content: '下一问' })))
      .toEqual({ kind: 'done', data: true })
  })

  it('ignores unknown events, including retired orchestration events', () => {
    for (const payload of [
      {},
      { unknown: true },
      { done: false, content: null },
      { type: 'orchestration', mode: 'AGENT', action: 'DEEPEN', summary: '继续深挖' }
    ]) {
      expect(parseInterviewSseData(JSON.stringify(payload)))
        .toEqual({ kind: 'unknown', data: null })
    }
  })

  it('rejects empty, malformed and non-object data', () => {
    for (const rawData of [undefined, null, 42, '', '  ', '{bad json', 'null', '[]', 'true', '42', '"text"']) {
      expect(parseInterviewSseData(rawData)).toBeNull()
    }
  })
})
