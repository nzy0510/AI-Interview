import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Login from '../Login.vue'
import { getAuthConfigAPI } from '@/api/user'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn()
  }
}))

vi.mock('@/api/user', () => ({
  getAuthConfigAPI: vi.fn(),
  loginAPI: vi.fn(),
  registerAPI: vi.fn(),
  sendCodeAPI: vi.fn(),
  forgotPasswordAPI: vi.fn(),
  resetPasswordAPI: vi.fn()
}))

vi.mock('@/utils/analytics', () => ({
  trackEvent: vi.fn()
}))

const mountLogin = () => mount(Login, {
  global: {
    stubs: {
      ElIcon: { template: '<i><slot /></i>' },
      ElTabs: { template: '<div><slot /></div>' },
      ElTabPane: {
        props: ['label', 'name'],
        template: '<section :data-name="name"><h2>{{ label }}</h2><slot /></section>'
      },
      ElForm: { template: '<form><slot /></form>' },
      ElFormItem: { template: '<div><slot /></div>' },
      ElInput: {
        props: ['modelValue', 'placeholder'],
        template: '<input :value="modelValue" :placeholder="placeholder" />'
      },
      ElButton: { template: '<button><slot /></button>' },
      ElLink: { template: '<a><slot /></a>' },
      Operation: true,
      Message: true,
      DataLine: true,
      Lock: true
    }
  }
})

describe('Login auth mode', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('local-admin 模式只显示用户名密码登录', async () => {
    getAuthConfigAPI.mockResolvedValue({ mode: 'local-admin' })
    const wrapper = mountLogin()

    await flushPromises()

    expect(wrapper.find('[data-name="register"]').exists()).toBe(false)
    expect(wrapper.find('[data-name="forgot"]').exists()).toBe(false)
    expect(wrapper.find('.local-admin-notice').exists()).toBe(false)
    expect(wrapper.findAll('input')[0].attributes('placeholder')).toBe('请输入用户名')
  })

  it('认证配置请求失败时保留邮箱注册与找回密码模式', async () => {
    getAuthConfigAPI.mockRejectedValue(new Error('network error'))
    const wrapper = mountLogin()

    await flushPromises()

    expect(wrapper.find('[data-name="register"]').exists()).toBe(true)
    expect(wrapper.find('[data-name="forgot"]').exists()).toBe(true)
    expect(wrapper.find('.local-admin-notice').exists()).toBe(false)
  })

  it.each([
    { mode: 'email-verified' },
    { mode: 'unexpected-mode' },
    null
  ])('认证配置为 $mode 时不暴露本地默认账号', async (config) => {
    getAuthConfigAPI.mockResolvedValue(config)
    const wrapper = mountLogin()

    await flushPromises()

    expect(wrapper.find('[data-name="register"]').exists()).toBe(true)
    expect(wrapper.find('[data-name="forgot"]').exists()).toBe(true)
    expect(wrapper.find('.local-admin-notice').exists()).toBe(false)
  })
})
