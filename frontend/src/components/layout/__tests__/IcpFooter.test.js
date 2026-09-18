import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { reactive } from 'vue'
import App from '../../../App.vue'

const route = reactive({ path: '/login' })

vi.mock('vue-router', () => ({
  useRoute: () => route,
}))

vi.mock('@/components/layout/AppShell.vue', () => ({
  default: { template: '<div class="app-shell"><slot /></div>' },
}))

const mountApp = () => mount(App, {
  global: {
    stubs: {
      RouterView: { template: '<div>Route content</div>' },
    },
  },
})

afterEach(() => {
  vi.unstubAllEnvs()
})

describe('optional ICP footer', () => {
  it.each([undefined, '', '   '])('does not add a footer when the record is blank (%j)', (record) => {
    vi.stubEnv('VITE_ICP_RECORD', record)
    const wrapper = mountApp()

    expect(wrapper.find('footer').exists()).toBe(false)
    expect(wrapper.find('.app-root--with-footer').exists()).toBe(false)
    wrapper.unmount()
  })

  it.each(['/login', '/', '/interview/session'])('shows the configured record on %s', (path) => {
    route.path = path
    vi.stubEnv('VITE_ICP_RECORD', '浙ICP备00000000号')
    const wrapper = mountApp()
    const link = wrapper.get('footer a')

    expect(link.text()).toBe('浙ICP备00000000号')
    expect(link.attributes('href')).toBe('https://beian.miit.gov.cn/')
    expect(link.attributes('rel')).toContain('noopener')
    expect(wrapper.find('.app-root--with-footer').exists()).toBe(true)
    wrapper.unmount()
  })
})
