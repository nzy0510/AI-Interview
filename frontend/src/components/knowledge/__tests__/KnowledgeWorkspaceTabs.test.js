import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import KnowledgeWorkspaceTabs from '../KnowledgeWorkspaceTabs.vue'

describe('KnowledgeWorkspaceTabs', () => {
  it('keeps public positions to overview and atom tabs', () => {
    const wrapper = mount(KnowledgeWorkspaceTabs, { props: { isPublic: true, canBuild: false, candidateCount: 3 }, global: { plugins: [ElementPlus] } })
    expect(wrapper.text()).toContain('概览')
    expect(wrapper.text()).toContain('题库原子')
    expect(wrapper.text()).not.toContain('智能构建')
    expect(wrapper.text()).not.toContain('候选审核')
  })

  it('shows candidate count for private review workspace', () => {
    const wrapper = mount(KnowledgeWorkspaceTabs, { props: { isPublic: false, canBuild: true, candidateCount: 12 }, global: { plugins: [ElementPlus] } })
    expect(wrapper.text()).toContain('智能构建')
    expect(wrapper.text()).toContain('候选审核')
    expect(wrapper.find('.el-badge').exists()).toBe(true)
  })
})
