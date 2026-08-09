import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import KnowledgeWorkspaceTabs from '../KnowledgeWorkspaceTabs.vue'

describe('KnowledgeWorkspaceTabs', () => {
  it('keeps build tabs hidden when the position has no build capability', () => {
    const wrapper = mount(KnowledgeWorkspaceTabs, { props: { canBuild: false, candidateCount: 3 }, global: { plugins: [ElementPlus] } })
    expect(wrapper.text()).toContain('概览')
    expect(wrapper.text()).toContain('题库原子')
    expect(wrapper.text()).not.toContain('智能构建')
    expect(wrapper.text()).not.toContain('终审发布')
  })

  it('shows build tabs and candidate count for any authorized target', () => {
    const wrapper = mount(KnowledgeWorkspaceTabs, { props: { canBuild: true, candidateCount: 12 }, global: { plugins: [ElementPlus] } })
    expect(wrapper.text()).toContain('智能构建')
    expect(wrapper.text()).toContain('终审发布')
    expect(wrapper.find('.el-badge').exists()).toBe(true)
  })
})
