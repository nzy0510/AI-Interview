import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import LocalAdminNotice from '../LocalAdminNotice.vue'

describe('LocalAdminNotice', () => {
  it('展示本地默认账号并提供填入动作', async () => {
    const wrapper = mount(LocalAdminNotice, {
      global: {
        stubs: {
          ElButton: {
            template: '<button><slot /></button>'
          }
        }
      }
    })

    expect(wrapper.text()).toContain('admin')
    expect(wrapper.text()).toContain('admin123')
    expect(wrapper.text()).toContain('DeepSeek')

    await wrapper.get('.local-admin-notice__fill').trigger('click')

    expect(wrapper.emitted('fill')).toEqual([[
      { username: 'admin', password: 'admin123' }
    ]])
  })
})
