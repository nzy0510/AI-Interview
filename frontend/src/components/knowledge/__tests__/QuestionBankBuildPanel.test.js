import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import QuestionBankBuildPanel from '../QuestionBankBuildPanel.vue'

describe('QuestionBankBuildPanel', () => {
  it('blocks generation when no active LLM configuration exists', () => {
    const wrapper = mount(QuestionBankBuildPanel, {
      props: {
        canBuild: true,
        llmStatus: { resolved: true, hasActiveConfig: false },
        position: { name: 'Java 后端' }
      },
      global: { plugins: [ElementPlus] }
    })
    expect(wrapper.text()).toContain('尚未配置可用的大模型')
    expect(wrapper.findAll('button').some((button) => button.text().includes('去配置'))).toBe(true)
  })

  it('disables deletion while a build is pending or running', () => {
    const wrapper = mount(QuestionBankBuildPanel, {
      props: {
        canBuild: true,
        llmStatus: { resolved: true, hasActiveConfig: true },
        position: { name: 'Java 后端' },
        activeBuild: { id: 7, status: 'RUNNING', stage: 'GENERATING', progress: 32 }
      },
      global: { plugins: [ElementPlus] }
    })
    const deleteButton = wrapper.findAll('button').find((button) => button.text().includes('删除批次'))
    expect(deleteButton.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('构建运行中，暂不能删除')
  })
})
