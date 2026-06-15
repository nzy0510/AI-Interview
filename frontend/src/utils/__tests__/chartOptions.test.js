import { describe, it, expect } from 'vitest'
import {
  buildTooltipConfig,
  buildHeatmapVisualMap,
  buildHeatmapData,
  buildKnowledgeRoseOption,
} from '../chartOptions'

describe('buildTooltipConfig', () => {
  it('returns warm light background instead of black', () => {
    const tooltip = buildTooltipConfig()

    // 暖色浅底，而非黑色
    expect(tooltip.backgroundColor).toBe('#faf9f5')
    // className 配合全局 CSS
    expect(tooltip.className).toBe('echarts-tooltip')
    // 暖灰边框
    expect(tooltip.borderColor).toBe('#e8e6dc')
    // 深色文字
    expect(tooltip.textStyle.color).toBe('#141413')
  })

  it('does not use dark background colors', () => {
    const tooltip = buildTooltipConfig()

    // CSS 注入额外样式
    expect(tooltip.extraCssText).toContain('box-shadow')
    expect(tooltip.extraCssText).toContain('border-radius')
  })
})

describe('buildHeatmapVisualMap', () => {
  it('binds to seriesIndex 0', () => {
    const visualMap = buildHeatmapVisualMap()

    expect(visualMap.seriesIndex).toBe(0)
  })

  it('uses warm terracotta color gradient, not green', () => {
    const visualMap = buildHeatmapVisualMap()
    const colors = visualMap.inRange.color

    // 颜色从暖沙色到深赤陶，不是绿色
    expect(colors[0]).toBe('#e8e6dc')  // E 级
    expect(colors[colors.length - 1]).toBe('#8b2e1a')  // A 级

    // 确保没有绿色
    colors.forEach(c => {
      expect(c).not.toContain('185, 129')
      expect(c).not.toContain('10b981')
    })
  })

  it('visualMap controller is hidden (not needed for users)', () => {
    const visualMap = buildHeatmapVisualMap()

    expect(visualMap.show).toBe(false)
  })
})

describe('buildHeatmapData', () => {
  it('parses abilityJson and converts grades to numeric values', () => {
    const records = [
      { createTime: '2025-01-01', abilityJson: '{"expression": "A", "problemSolving": "B"}' },
      { createTime: '2025-01-02', abilityJson: '{"expression": "C", "problemSolving": "D"}' },
    ]
    const dimKeys = ['problemSolving', 'expression']
    const dimLabels = ['解题思路', '表达清晰']

    const result = buildHeatmapData(records, dimKeys, dimLabels)

    // 4 条数据 (2 records × 2 dimensions)，y 轴已反转
    expect(result.data.length).toBe(4)
    // revKeys = ['expression', 'problemSolving']
    // record 0: y=expression(A), y=problemSolving(B)
    expect(result.data[0]).toEqual([0, 0, 4, 'A'])
    expect(result.data[1]).toEqual([0, 1, 3, 'B'])
    // record 1: y=expression(C), y=problemSolving(D)
    expect(result.data[2]).toEqual([1, 0, 2, 'C'])
    expect(result.data[3]).toEqual([1, 1, 1, 'D'])
  })

  it('returns empty array for empty records', () => {
    const result = buildHeatmapData([], ['expression'], ['表达清晰'])
    expect(result.data).toEqual([])
    expect(result.yAxisData).toEqual(['表达清晰'])
  })

  it('defaults to grade E (value 0) for missing abilities', () => {
    const records = [
      { createTime: '2025-01-01', abilityJson: '{"expression": "A"}' },
    ]
    const dimKeys = ['problemSolving', 'expression']
    const dimLabels = ['解题思路', '表达清晰']

    const result = buildHeatmapData(records, dimKeys, dimLabels)

    // revKeys = ['expression', 'problemSolving']，先遍历 expression(A)，再 problemSolving(E)
    expect(result.data[0]).toEqual([0, 0, 4, 'A'])
    expect(result.data[1]).toEqual([0, 1, 0, 'E'])
  })
})

describe('buildKnowledgeRoseOption', () => {
  it('builds a warm rose chart from knowledge coverage details', () => {
    const option = buildKnowledgeRoseOption([
      { category: 'Java基础', covered: 8, total: 12, percent: 66.7 },
      { category: 'Redis', covered: 4, total: 8, percent: 50 },
    ])

    expect(option.color).toEqual(['#f8e4c8', '#f4b46f', '#e27a3f', '#c9542f', '#8f2f1b'])
    expect(option.series[0].type).toBe('pie')
    expect(option.series[0].roseType).toBe('radius')
    expect(option.series[0].data).toEqual([
      { name: 'Java基础', value: 8, total: 12, percent: 66.7 },
      { name: 'Redis', value: 4, total: 8, percent: 50 },
    ])
  })

  it('uses a non-blue fallback slice for empty coverage', () => {
    const option = buildKnowledgeRoseOption([])

    expect(option.series[0].data).toEqual([{ name: '暂无覆盖', value: 1, total: 0, percent: 0 }])
    expect(option.color).not.toContain('#3a388b')
    expect(option.color).not.toContain('#5250a4')
  })
})
