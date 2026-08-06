import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import QuestionBankAtomEditDialog from '../QuestionBankAtomEditDialog.vue'

const formStubs = {
  ElDialog: { template: '<section><slot /><slot name="footer" /></section>' },
  ElForm: { template: '<form><slot /></form>' },
  ElFormItem: { template: '<label><slot /></label>' },
  ElInput: { template: '<input />' },
  ElSelect: { template: '<div><slot /></div>' },
  ElOption: { name: 'ElOption', props: ['label', 'value'], template: '<span />' },
  ElButton: { emits: ['click'], template: '<button type="button" @click="$emit(\'click\')"><slot /></button>' }
}

describe('QuestionBankAtomEditDialog', () => {
  it('uses the question-bank difficulty contract and preserves an existing mid value', async () => {
    const wrapper = mount(QuestionBankAtomEditDialog, {
      props: {
        modelValue: true,
        atom: {
          subject: '双亲委派模型',
          category: 'jvm',
          difficulty: 'mid',
          tags: ['JVM'],
          principles: '父加载器优先',
          pitfalls: '不是继承关系',
          followUpPaths: ['何时打破双亲委派？', '如何避免类冲突？']
        }
      },
      global: { stubs: formStubs }
    })

    const difficultyValues = wrapper
      .findAllComponents({ name: 'ElOption' })
      .map((option) => option.props('value'))

    expect(difficultyValues).toEqual(['junior', 'mid', 'senior', 'principal'])

    const saveButton = wrapper.findAll('button').find((button) => button.text().includes('保存为已审核草稿'))
    expect(saveButton).toBeTruthy()
    await saveButton.trigger('click')
    expect(wrapper.emitted('save')?.[0]?.[0].difficulty).toBe('mid')
  })

  it('does not mark an atom reviewed when it has fewer than two follow-up paths', async () => {
    const wrapper = mount(QuestionBankAtomEditDialog, {
      props: {
        modelValue: true,
        atom: {
          subject: '双亲委派模型',
          category: 'jvm',
          difficulty: 'mid',
          principles: '父加载器优先',
          followUpPaths: ['只有一条追问']
        }
      },
      global: { stubs: formStubs }
    })

    const saveButton = wrapper.findAll('button').find((button) => button.text().includes('保存为已审核草稿'))
    await saveButton.trigger('click')

    expect(wrapper.emitted('save')).toBeUndefined()
  })
})
