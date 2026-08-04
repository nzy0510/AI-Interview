import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import InterviewReportOverlay from '../InterviewReportOverlay.vue'
import reportSource from '../InterviewReportOverlay.vue?raw'
import interviewSource from '../../../views/Interview.vue?raw'
import videoInterviewSource from '../../../views/VideoInterview.vue?raw'

const mountReport = () => mount(InterviewReportOverlay, {
  props: {
    displayScore: 72,
    score: 72,
    scoreColor: '#3a388b',
    metrics: [],
    feedbackHtml: '<p>反馈</p>',
    recommendations: [],
    emotionLabelFn: (value) => value,
    emotionColorFn: () => '#3a388b'
  },
  global: {
    stubs: {
      ElButton: { template: '<button><slot /></button>' },
      ElProgress: { template: '<div><slot :percentage="percentage" /></div>', props: ['percentage'] },
      ElEmpty: { template: '<div />' },
      ElTag: { template: '<span><slot /></span>' },
      ElTimeline: { template: '<div><slot /></div>' },
      ElTimelineItem: { template: '<div><slot /></div>' }
    }
  }
})

describe('InterviewReportOverlay', () => {
  it('renders as an in-flow report page instead of a fixed overlay', () => {
    const wrapper = mountReport()

    expect(wrapper.classes()).toContain('interview-report-page')
    expect(wrapper.classes()).not.toContain('dashboard-overlay')
    expect(reportSource).not.toMatch(/\.interview-report-page\s*{[^}]*position:\s*fixed/s)
    expect(reportSource).not.toMatch(/\.interview-report-page\s*{[^}]*overflow-y:\s*auto/s)
  })

  it.each([interviewSource, videoInterviewSource])('replaces the interview workspace when the report is shown', (source) => {
    expect(source).toMatch(/v-if="!showReport"/)
    expect(source).toMatch(/<InterviewReportOverlay\s+v-else/)
  })
})
