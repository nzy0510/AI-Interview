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

  it('does not infer PDF chunk or call counts from binary file size', () => {
    const wrapper = mount(QuestionBankBuildPanel, {
      props: {
        canBuild: true,
        llmStatus: { resolved: true, hasActiveConfig: true },
        position: { name: 'Spring Cloud 工程师' }
      },
      global: { plugins: [ElementPlus] }
    })

    expect(wrapper.text()).toContain('分块 / 调用规模')
    expect(wrapper.text()).toContain('提交解析后确定')
    expect(wrapper.text()).toContain('文件 / 上传大小')
    expect(wrapper.text()).not.toContain('预估分块 / 最低调用')
    expect(wrapper.text()).not.toContain('页数与字符量提交后返回')
    expect(wrapper.findComponent({ name: 'ElSelect' }).props('placeholder')).toBe('生成分类提示（可选）')
    expect(wrapper.text()).toContain('不是题库筛选条件')
    expect(wrapper.text()).not.toContain('已有 JSON 导入包')
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

  it('keeps a published build as an auditable source record', () => {
    const wrapper = mount(QuestionBankBuildPanel, {
      props: {
        canBuild: true,
        llmStatus: { resolved: true, hasActiveConfig: true },
        position: { name: 'Java 后端' },
        activeBuild: { id: 7, status: 'COMPLETED', stage: 'PUBLISHED', progress: 100, finalAtomIds: ['atom-1'] }
      },
      global: { plugins: [ElementPlus] }
    })

    const deleteButton = wrapper.findAll('button').find((button) => button.text().includes('删除批次'))
    expect(deleteButton.attributes('disabled')).toBeDefined()
    expect(deleteButton.attributes('title')).toContain('保留来源记录')
  })

  it('shows controlled pipeline stages and machine supervision counts', () => {
    const wrapper = mount(QuestionBankBuildPanel, {
      props: {
        canBuild: true,
        llmStatus: { resolved: true, hasActiveConfig: true },
        position: { name: 'Java 后端' },
        activeBuild: {
          id: 8,
          status: 'RUNNING',
          stage: 'SUPERVISING',
          progress: 76,
          candidateCount: 7,
          autoPassCount: 5,
          needsHumanCount: 1,
          autoRejectCount: 1
        }
      },
      global: { plugins: [ElementPlus] }
    })

    expect(wrapper.text()).toContain('正在审查处理结果')
    expect(wrapper.text()).toContain('监督通过 5')
    expect(wrapper.text()).toContain('需人工 1')
    expect(wrapper.text()).toContain('自动排除 1')
    expect(wrapper.text()).not.toContain('逐条确认')
  })
})
