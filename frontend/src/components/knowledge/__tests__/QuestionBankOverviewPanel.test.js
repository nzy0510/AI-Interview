import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import QuestionBankOverviewPanel from '../QuestionBankOverviewPanel.vue'

describe('QuestionBankOverviewPanel', () => {
  it('shows that an administrator can maintain a public question bank', () => {
    const wrapper = mount(QuestionBankOverviewPanel, {
      props: {
        position: { name: 'Java 后端开发', knowledgeBase: { name: 'Java 后端题库' } },
        isPublic: true,
        canMaintain: true
      },
      global: { plugins: [ElementPlus] }
    })

    expect(wrapper.text()).toContain('公共题库可维护')
    expect(wrapper.text()).not.toContain('只读岗位')
  })
})
