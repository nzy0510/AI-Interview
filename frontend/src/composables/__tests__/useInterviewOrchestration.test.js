import { describe, expect, it } from 'vitest'
import { useInterviewOrchestration } from '../useInterviewOrchestration'

describe('useInterviewOrchestration', () => {
  it('keeps only the latest normalized decision and can reset between turns', () => {
    const { orchestrationDecision, setOrchestrationDecision, resetOrchestrationDecision } = useInterviewOrchestration()
    const decision = {
      mode: 'AGENT',
      action: 'DEEPEN',
      summary: '继续深挖',
      tools: ['searchPositionKnowledge']
    }

    setOrchestrationDecision(decision)
    expect(orchestrationDecision.value).toEqual(decision)
    expect(orchestrationDecision.value).not.toBe(decision)

    resetOrchestrationDecision()
    expect(orchestrationDecision.value).toBeNull()
  })
})
