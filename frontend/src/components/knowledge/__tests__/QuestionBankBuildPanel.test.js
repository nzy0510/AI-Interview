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
    expect(wrapper.find('details.advanced-settings').attributes('open')).toBeUndefined()
    expect(wrapper.find('details.advanced-settings').text()).toContain('高级设置')
    expect(wrapper.findComponent({ name: 'ElSelect' }).props('placeholder')).toBe('留空自动识别（推荐）')
    expect(wrapper.text()).toContain('默认先分析整批文档并自动规划知识领域')
    expect(wrapper.text()).toContain('手动选择后，候选只能使用这些分类')
    expect(wrapper.text()).not.toContain('已有 JSON 导入包')
    expect(wrapper.findAll('.pipeline-step')).toHaveLength(6)
  })

  it('shows category planning as part of atom generation and exposes the resolved catalog', () => {
    const wrapper = mount(QuestionBankBuildPanel, {
      props: {
        canBuild: true,
        llmStatus: { resolved: true, hasActiveConfig: true },
        position: { name: '云原生工程师' },
        activeBuild: {
          id: 10,
          status: 'RUNNING',
          stage: 'CLASSIFYING',
          progress: 12,
          categories: []
        }
      },
      global: { plugins: [ElementPlus] }
    })

    expect(wrapper.text()).toContain('正在规划知识领域')
    expect(wrapper.text()).toContain('分类目录 自动规划中')
    expect(wrapper.findAll('.pipeline-step')[1].classes()).toContain('is-active')

    return wrapper.setProps({
      activeBuild: {
        id: 10,
        status: 'RUNNING',
        stage: 'GENERATING',
        progress: 24,
        categories: ['服务治理', '配置管理']
      }
    }).then(() => {
      expect(wrapper.text()).toContain('分类目录 服务治理、配置管理')
    })
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
          autoRejectCount: 1,
          repairedCount: 2,
          repairRound: 1
        }
      },
      global: { plugins: [ElementPlus] }
    })

    expect(wrapper.text()).toContain('正在审查处理结果')
    expect(wrapper.text()).toContain('监督通过 5')
    expect(wrapper.text()).toContain('需关注 1')
    expect(wrapper.text()).toContain('自动排除 1')
    expect(wrapper.text()).toContain('修复后通过 2')
    expect(wrapper.text()).toContain('修复轮次 1')
    expect(wrapper.findAll('.pipeline-step')[2].classes()).toContain('is-active')
    expect(wrapper.text()).not.toContain('逐条确认')
  })

  it('shows repair and re-supervision as the same user-facing automatic repair step', () => {
    const wrapper = mount(QuestionBankBuildPanel, {
      props: {
        canBuild: true,
        llmStatus: { resolved: true, hasActiveConfig: true },
        position: { name: 'Java 后端' },
        activeBuild: { id: 9, status: 'RUNNING', stage: 'RESUPERVISING', progress: 84 }
      },
      global: { plugins: [ElementPlus] }
    })

    expect(wrapper.text()).toContain('正在复查修复结果')
    expect(wrapper.findAll('.pipeline-step')[3].classes()).toContain('is-active')
  })

  it('explains a repair failure at final review without also claiming normal completion', () => {
    const errorMessage = '第 2 轮修复调用失败：上游模型超时'
    const wrapper = mount(QuestionBankBuildPanel, {
      props: {
        canBuild: true,
        llmStatus: { resolved: true, hasActiveConfig: true },
        position: { name: 'Java 后端' },
        activeBuild: {
          id: 9,
          status: 'COMPLETED',
          stage: 'READY_FOR_FINAL_REVIEW',
          progress: 100,
          errorMessage
        }
      },
      global: { plugins: [ElementPlus] }
    })

    const warning = wrapper.get('[data-testid="final-review-repair-warning"]')
    expect(warning.text()).toContain('本次修复助手处理失败')
    expect(warning.text()).toContain(errorMessage)
    expect(warning.text()).toContain('原有可发布项未受影响')
    expect(warning.text()).toContain('可到“终审发布”重试修复')
    expect(wrapper.text()).not.toContain('自动处理已完成')
    expect(wrapper.find('.error-text').exists()).toBe(false)
  })

  it('shows partial index failure as a warning instead of a fully completed pipeline', () => {
    const wrapper = mount(QuestionBankBuildPanel, {
      props: {
        canBuild: true,
        llmStatus: { resolved: true, hasActiveConfig: true },
        position: { name: 'Java 后端' },
        activeBuild: {
          id: 9,
          status: 'FAILED',
          stage: 'PUBLISHED_WITH_INDEX_ERRORS',
          progress: 100,
          errorMessage: '1 条索引同步失败'
        }
      },
      global: { plugins: [ElementPlus] }
    })

    const steps = wrapper.findAll('.pipeline-step')
    expect(steps.slice(0, 5).every((step) => step.classes().includes('is-complete'))).toBe(true)
    expect(steps[5].classes()).toContain('is-warning')
    expect(steps[5].classes()).not.toContain('is-complete')
    expect(wrapper.text()).toContain('数据库已发布，但部分检索索引同步失败')
  })

  it('does not disguise a failed build as active document parsing', () => {
    const wrapper = mount(QuestionBankBuildPanel, {
      props: {
        canBuild: true,
        llmStatus: { resolved: true, hasActiveConfig: true },
        position: { name: 'Java 后端' },
        activeBuild: { id: 9, status: 'FAILED', stage: 'FAILED', progress: 84, errorMessage: '监督失败' }
      },
      global: { plugins: [ElementPlus] }
    })

    expect(wrapper.findAll('.pipeline-step').some((step) => step.classes().includes('is-active'))).toBe(false)
    expect(wrapper.text()).toContain('监督失败')
  })
})
