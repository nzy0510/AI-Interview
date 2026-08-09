import { describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import QuestionBankAtomPanel from '../QuestionBankAtomPanel.vue'

describe('QuestionBankAtomPanel', () => {
  it('defaults pagination to ten rows', async () => {
    const wrapper = mount(QuestionBankAtomPanel, {
      props: { total: 11 },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    expect(wrapper.findComponent({ name: 'ElPagination' }).props('pageSize')).toBe(10)
  })

  it('lets an authorized administrator open a knowledge atom for editing', async () => {
    const wrapper = mount(QuestionBankAtomPanel, {
      props: {
        canEdit: true,
        atoms: [{ id: 7, atomId: 'jvm-gc-roots', subject: 'GC Roots', category: 'jvm', status: 'DRAFT', reviewStatus: 'PASS' }],
        total: 1
      },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    const editButton = wrapper.findAll('button').find((button) => button.text().includes('编辑'))
    expect(editButton).toBeTruthy()
    await editButton.trigger('click')
    expect(wrapper.emitted('edit')).toEqual([[7]])
  })
})
