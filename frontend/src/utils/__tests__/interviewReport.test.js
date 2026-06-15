import { describe, expect, it } from 'vitest'
import { parseInterviewFinishPayload } from '../interviewReport'

describe('interviewReport finish payload', () => {
  it('parses preliminary report fields from FinishInterviewResponse.record', () => {
    const payload = parseInterviewFinishPayload({
      reportJobId: 900,
      reportStatus: 'PENDING',
      record: {
        abilityJson: '{"problemSolving":"B"}',
        recommendations: '[{"action":"复盘故障处理流程"}]',
        emotionJson: '{"dominantEmotion":"neutral"}'
      }
    })

    expect(payload.ability.problemSolving).toBe('B')
    expect(payload.recommendations).toHaveLength(1)
    expect(payload.emotion.dominantEmotion).toBe('neutral')
  })
})
