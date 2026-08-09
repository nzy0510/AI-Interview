import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import QuestionBankCandidateReviewPanel from '../QuestionBankCandidateReviewPanel.vue'

describe('QuestionBankCandidateReviewPanel', () => {
  it('defaults to supervision exceptions and uses one explicit final-review action', () => {
    const wrapper = mount(QuestionBankCandidateReviewPanel, {
      props: {
        canReview: true,
        canFinalize: false,
        exceptionCount: 1,
        finalizableCount: 1,
        candidates: [
          { id: 1, status: 'PENDING', machineReviewStatus: 'AUTO_PASS', subject: '自动通过项' },
          { id: 2, status: 'PENDING', machineReviewStatus: 'NEEDS_HUMAN', machineReviewIssues: ['证据不足'], subject: '需要人工项', category: 'jvm', principles: 'answer', sourceRef: 'guide.pdf' }
        ]
      }
    })
    expect(wrapper.text()).toContain('监督异常 1')
    expect(wrapper.text()).toContain('需要人工项')
    expect(wrapper.text()).not.toContain('自动通过项')
    expect(wrapper.text()).toContain('终审并发布 1 条')
    expect(wrapper.text()).not.toContain('导入为草稿')
    expect(wrapper.text()).toContain('接受候选')
    expect(wrapper.text()).toContain('拒绝')
    expect(wrapper.text()).toContain('保存修改')
    expect(wrapper.find('[data-testid="finalize-build"]').attributes('disabled')).toBeDefined()
  })

  it('emits finalization only when all supervision exceptions are resolved', async () => {
    const wrapper = mount(QuestionBankCandidateReviewPanel, {
      props: {
        canReview: true,
        canFinalize: true,
        exceptionCount: 0,
        finalizableCount: 3,
        candidates: [{ id: 1, status: 'PENDING', machineReviewStatus: 'AUTO_PASS', subject: 'GC Roots' }]
      }
    })

    await wrapper.find('[data-testid="finalize-build"]').trigger('click')

    expect(wrapper.emitted('finalize')).toHaveLength(1)
  })

  it('disables review actions while the build is still running', () => {
    const wrapper = mount(QuestionBankCandidateReviewPanel, {
      props: {
        canReview: false,
        candidates: [{ id: 1, status: 'PENDING', machineReviewStatus: 'NEEDS_HUMAN', subject: 'GC Roots' }]
      }
    })

    expect(wrapper.text()).toContain('构建完成后才能审核候选')
    expect(wrapper.findAll('button').filter((button) => ['保存修改', '拒绝', '接受候选'].includes(button.text())).every((button) => button.attributes('disabled') !== undefined)).toBe(true)
  })
})
