import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import QuestionBankJsonImportCard from '../QuestionBankJsonImportCard.vue'

describe('QuestionBankJsonImportCard', () => {
  it('reads a JSON package and sends it to the workspace for validation', async () => {
    const wrapper = mount(QuestionBankJsonImportCard, {
      props: { canImport: true },
      global: { plugins: [ElementPlus] }
    })
    const input = wrapper.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', {
      configurable: true,
      value: [{ name: 'public-bank.json', text: async () => '{"schemaVersion":"1.0","atoms":[]}' }]
    })

    await input.trigger('change')

    expect(wrapper.emitted('import-package')?.[0]).toEqual([
      { schemaVersion: '1.0', atoms: [] },
      'public-bank.json'
    ])
  })
})
