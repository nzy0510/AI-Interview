import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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

beforeEach(() => {
  vi.stubEnv('VITE_ICP_RECORD', '')
  vi.stubEnv('VITE_PUBLIC_SECURITY_RECORD', '')
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

  it.each(['/login', '/', '/interview/session'])('links the public security record to its official query on %s', (path) => {
    route.path = path
    vi.stubEnv('VITE_ICP_RECORD', '浙ICP备00000000号')
    vi.stubEnv('VITE_PUBLIC_SECURITY_RECORD', '浙公网安备33020602001742号')
    const wrapper = mountApp()
    const links = wrapper.findAll('footer a')

    expect(links).toHaveLength(2)
    expect(links[1].text()).toBe('浙公网安备33020602001742号')
    expect(links[1].attributes('href')).toBe('https://beian.mps.gov.cn/#/query/webSearch?code=33020602001742')
    expect(links[1].attributes('rel')).toContain('noopener')
    expect(links[1].find('img').exists()).toBe(true)
    wrapper.unmount()
  })

  it('can show a public security record without an ICP record', () => {
    vi.stubEnv('VITE_PUBLIC_SECURITY_RECORD', '浙公网安备33020602001742号')
    const wrapper = mountApp()

    expect(wrapper.findAll('footer a')).toHaveLength(1)
    expect(wrapper.get('footer a').text()).toBe('浙公网安备33020602001742号')
    expect(wrapper.find('.app-root--with-footer').exists()).toBe(true)
    wrapper.unmount()
  })
})
