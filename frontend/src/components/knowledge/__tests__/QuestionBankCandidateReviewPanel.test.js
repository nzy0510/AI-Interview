import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import QuestionBankCandidateReviewPanel from '../QuestionBankCandidateReviewPanel.vue'

const mountPanel = (props) => mount(QuestionBankCandidateReviewPanel, {
  props,
  global: {
    plugins: [ElementPlus],
    stubs: {
      teleport: true,
      ElSelect: { template: '<select><slot /></select>' },
      ElOption: { template: '<option><slot /></option>' }
    }
  }
})

describe('QuestionBankCandidateReviewPanel', () => {
  it('defaults to the exact publish scope and keeps attention items outside it', () => {
    const wrapper = mountPanel({
      canReview: true,
      canFinalize: true,
      exceptionCount: 1,
      finalizableCount: 1,
      candidates: [
        { id: 1, status: 'PENDING', machineReviewStatus: 'AUTO_PASS', subject: '自动通过项' },
        { id: 2, status: 'PENDING', machineReviewStatus: 'NEEDS_HUMAN', machineReviewIssues: ['证据不足'], subject: '需要关注项', category: 'jvm', principles: 'answer', sourceRef: 'guide.pdf' }
      ]
    })

    expect(wrapper.text()).toContain('最终审核')
    expect(wrapper.text()).toContain('可直接发布1')
    expect(wrapper.text()).toContain('仍需关注1')
    expect(wrapper.text()).toContain('自动通过项')
    expect(wrapper.text()).not.toContain('需要关注项')
    expect(wrapper.text()).toContain('待发布 1')
    expect(wrapper.text()).toContain('需关注 1')
    expect(wrapper.text()).toContain('助手处理 0')
    expect(wrapper.text()).toContain('全部 2')
    expect(wrapper.text()).toContain('一键发布全部 1 条')
    expect(wrapper.find('[data-testid="finalize-build"]').attributes('disabled')).toBeUndefined()
  })

  it('offers one-click batch publishing without requiring per-candidate confirmation', async () => {
    const wrapper = mountPanel({
      canReview: true,
      canFinalize: true,
      exceptionCount: 1,
      finalizableCount: 2,
      candidates: [
        { id: 1, status: 'PENDING', machineReviewStatus: 'AUTO_PASS', subject: 'GC Roots' },
        { id: 3, status: 'ACCEPTED', machineReviewStatus: 'NEEDS_HUMAN', subject: '人工确认项' },
        { id: 2, status: 'PENDING', machineReviewStatus: 'NEEDS_HUMAN', subject: '需关注项' }
      ]
    })

    expect(wrapper.text()).toContain('无需逐条确认')
    expect(wrapper.text()).toContain('一键发布全部 2 条')
    expect(wrapper.text()).toContain('已纳入本次批量发布')
    expect(wrapper.findAll('button').some((button) => button.text() === '确认可发布')).toBe(false)

    await wrapper.find('[data-testid="finalize-build"]').trigger('click')

    expect(wrapper.emitted('finalize')).toHaveLength(1)
    expect(wrapper.text()).toContain('不会阻断其他通过项发布')
  })

  it('opens the repair assistant dialog and emits selected ids with the instruction', async () => {
    const wrapper = mountPanel({
      canReview: true,
      canFinalize: false,
      exceptionCount: 2,
      finalizableCount: 0,
      candidates: [
        { id: 2, status: 'PENDING', machineReviewStatus: 'NEEDS_HUMAN', subject: '候选一' },
        { id: 3, status: 'PENDING', machineReviewStatus: 'FAILED', subject: '候选二' }
      ]
    })

    await wrapper.find('[data-testid="repair-all"]').trigger('click')
    const textarea = wrapper.find('#repair-instruction')
    expect(textarea.exists()).toBe(true)
    await textarea.setValue('只依据原文修正')
    await wrapper.find('[data-testid="repair-confirm"]').trigger('click')

    expect(wrapper.emitted('repair')).toEqual([[[2, 3], '只依据原文修正']])
  })

  it('separates assistant attempts from candidates that passed after repair', () => {
    const wrapper = mountPanel({
      canReview: true,
      canFinalize: true,
      exceptionCount: 1,
      finalizableCount: 1,
      candidates: [
        { id: 1, status: 'PENDING', machineReviewStatus: 'AUTO_PASS', repairStatus: 'VERIFIED', repairAttempts: 1, subject: '已修复项' },
        { id: 2, status: 'PENDING', machineReviewStatus: 'NEEDS_HUMAN', repairStatus: 'EXHAUSTED', repairAttempts: 2, subject: '修复耗尽项' }
      ]
    })

    expect(wrapper.text()).toContain('助手处理过2')
    expect(wrapper.text()).toContain('修复后通过1')
    expect(wrapper.text()).toContain('助手处理 2')
    expect(wrapper.text()).not.toContain('助手已修改')
  })

  it('renders field-level before and after values instead of raw patch JSON', () => {
    const wrapper = mountPanel({
      canReview: true,
      canFinalize: true,
      exceptionCount: 0,
      finalizableCount: 1,
      candidates: [{
        id: 2,
        status: 'PENDING',
        machineReviewStatus: 'AUTO_PASS',
        subject: 'GC Roots',
        principles: '修复后的解释',
        repairStatus: 'SUCCEEDED',
        repairAttempts: 1,
        repairSummary: '补充了来源限定',
        repairHistory: [{ round: 1, before: { principles: '旧解释' }, after: { principles: '修复后的解释' } }],
        machineSuggestedPatch: { pitfalls: '不要混淆强引用与可达性' }
      }]
    })

    expect(wrapper.text()).toContain('修复助手说明')
    expect(wrapper.text()).toContain('核心原则')
    expect(wrapper.text()).toContain('修改前')
    expect(wrapper.text()).toContain('旧解释')
    expect(wrapper.text()).toContain('修改后')
    expect(wrapper.text()).toContain('修复后的解释')
    expect(wrapper.text()).toContain('质量监督建议')
    expect(wrapper.find('pre').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('{"')
  })

  it('shows the latest repair changes and collapses older rounds by default', () => {
    const wrapper = mountPanel({
      canReview: true,
      canFinalize: true,
      exceptionCount: 0,
      finalizableCount: 1,
      candidates: [{
        id: 2,
        status: 'PENDING',
        machineReviewStatus: 'AUTO_PASS',
        subject: 'GC Roots',
        principles: '最终解释',
        repairStatus: 'VERIFIED',
        repairAttempts: 2,
        repairHistory: [
          { round: 1, before: { principles: '初始解释' }, after: { principles: '第一轮解释' } },
          { round: 2, before: { principles: '第一轮解释' }, after: { principles: '最终解释' } }
        ]
      }]
    })

    expect(wrapper.find('[data-testid="latest-repair-changes"]').text()).toContain('最终解释')
    const older = wrapper.find('[data-testid="older-repair-changes"]')
    expect(older.exists()).toBe(true)
    expect(older.attributes('open')).toBeUndefined()
    expect(older.text()).toContain('较早的 1 条修改')
    expect(older.text()).toContain('初始解释')
  })

  it('blocks finalization and navigation until unsaved edits are explicitly discarded', async () => {
    const wrapper = mountPanel({
      canReview: true,
      canFinalize: true,
      exceptionCount: 0,
      finalizableCount: 2,
      candidates: [
        { id: 1, status: 'PENDING', machineReviewStatus: 'AUTO_PASS', subject: '候选一', category: 'java', difficulty: 'mid', principles: '原内容', followUpPaths: ['追问一', '追问二'] },
        { id: 2, status: 'PENDING', machineReviewStatus: 'AUTO_PASS', subject: '候选二', category: 'jvm', difficulty: 'mid', principles: '另一条', followUpPaths: ['追问一', '追问二'] }
      ]
    })

    await wrapper.findAll('textarea')[0].setValue('尚未保存的新内容')

    expect(wrapper.find('[data-testid="dirty-warning"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="finalize-build"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="refresh-results"]').attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('.candidate-item')[1].attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('.candidate-filter button').every((button) => button.attributes('disabled') !== undefined)).toBe(true)

    await wrapper.find('[data-testid="discard-editor"]').trigger('click')

    expect(wrapper.find('[data-testid="dirty-warning"]').exists()).toBe(false)
    expect(wrapper.findAll('textarea')[0].element.value).toBe('原内容')
    expect(wrapper.find('[data-testid="finalize-build"]').attributes('disabled')).toBeUndefined()
  })

  it('disables review and repair actions while the automatic pipeline is running', async () => {
    const wrapper = mountPanel({
      canReview: false,
      candidates: [{ id: 1, status: 'PENDING', machineReviewStatus: 'NEEDS_HUMAN', subject: 'GC Roots' }]
    })

    await wrapper.findAll('.candidate-filter button').find((button) => button.text().includes('需关注')).trigger('click')
    expect(wrapper.text()).toContain('当前正在自动处理')
    expect(wrapper.find('[data-testid="repair-current"]').attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('button').filter((button) => ['保存人工修改', '不发布', '确认可发布'].includes(button.text())).every((button) => button.attributes('disabled') !== undefined)).toBe(true)
  })
})
