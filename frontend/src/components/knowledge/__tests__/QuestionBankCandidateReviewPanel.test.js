import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import QuestionBankCandidateReviewPanel from '../QuestionBankCandidateReviewPanel.vue'

describe('QuestionBankCandidateReviewPanel', () => {
  it('exposes accept/reject/save actions and only import-to-draft', () => {
    const wrapper = mount(QuestionBankCandidateReviewPanel, {
      props: {
        canReview: true,
        canImport: true,
        candidates: [{ id: 1, status: 'PENDING', subject: 'GC Roots', category: 'jvm', principles: 'answer', sourceRef: 'guide.pdf' }]
      }
    })
    expect(wrapper.text()).toContain('导入为草稿')
    expect(wrapper.text()).toContain('接受候选')
    expect(wrapper.text()).toContain('拒绝')
    expect(wrapper.text()).toContain('保存修改')
    expect(wrapper.text()).not.toContain('发布')
  })

  it('disables review actions while the build is still running', () => {
    const wrapper = mount(QuestionBankCandidateReviewPanel, {
      props: {
        canReview: false,
        candidates: [{ id: 1, status: 'PENDING', subject: 'GC Roots' }]
      }
    })

    expect(wrapper.text()).toContain('构建完成后才能审核候选')
    expect(wrapper.findAll('button').filter((button) => ['保存修改', '拒绝', '接受候选'].includes(button.text())).every((button) => button.attributes('disabled') !== undefined)).toBe(true)
  })
})
