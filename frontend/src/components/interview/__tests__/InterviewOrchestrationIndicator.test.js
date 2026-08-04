import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import InterviewOrchestrationIndicator from '../InterviewOrchestrationIndicator.vue'

const mountIndicator = (decision) => mount(InterviewOrchestrationIndicator, {
  props: { decision },
  global: {
    stubs: {
      ElTag: {
        template: '<span><slot /></span>'
      }
    }
  }
})

describe('InterviewOrchestrationIndicator', () => {
  it('shows a timeout fallback label alongside the backend summary', () => {
    const wrapper = mountIndicator({
      mode: 'RULE_FALLBACK',
      action: 'CONTINUE_PHASE',
      summary: '下一轮将自动重试 Agent 规划',
      tools: [],
      fallbackCategory: 'TIMEOUT'
    })

    expect(wrapper.get('.orchestration-indicator__fallback').text()).toBe('规划超时')
    expect(wrapper.get('.orchestration-indicator__summary').text()).toBe('下一轮将自动重试 Agent 规划')
  })

  it.each([
    ['RULE_FALLBACK', 'SocketTimeoutException'],
    ['AGENT', 'TIMEOUT'],
    ['RULE', 'TIMEOUT']
  ])('hides the fallback label for mode %s and category %s', (mode, fallbackCategory) => {
    const wrapper = mountIndicator({
      mode,
      action: 'CONTINUE_PHASE',
      summary: '继续当前阶段',
      tools: [],
      fallbackCategory
    })

    expect(wrapper.find('.orchestration-indicator__fallback').exists()).toBe(false)
    expect(wrapper.get('.orchestration-indicator__summary').text()).toBe('继续当前阶段')
  })
})
