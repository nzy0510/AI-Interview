/**
 * ECharts tooltip — warm light style
 */
export function buildTooltipConfig(overrides = {}) {
  return {
    backgroundColor: '#faf9f5',
    borderColor: '#e8e6dc',
    borderWidth: 1,
    className: 'echarts-tooltip',
    textStyle: { color: '#141413' },
    extraCssText: 'box-shadow: 0 4px 24px rgba(0,0,0,0.05); border-radius: 8px;',
    ...overrides,
  }
}

/**
 * Heatmap visualMap — warm terracotta gradient
 */
export function buildHeatmapVisualMap(overrides = {}) {
  return {
    min: 0,
    max: 4,
    calculable: true,
    orient: 'horizontal',
    left: 'center',
    bottom: 0,
    show: false,
    seriesIndex: 0,
    inRange: {
      color: ['#e8e6dc', '#d4a88c', '#c96442', '#b04e30', '#8b2e1a'],
    },
    ...overrides,
  }
}

const GRADE_MAP = { A: 4, B: 3, C: 2, D: 1, E: 0 }

/**
 * Build heatmap data from interview records and ability dimensions.
 * Returns { data, yAxisData } where data is [[x, y, value, label], ...]
 */
export function buildHeatmapData(records, dimKeys, dimLabels) {
  const revKeys = [...dimKeys].reverse()
  const data = []

  records.forEach((r, xIndex) => {
    let ab = {}
    try {
      ab = JSON.parse(r.abilityJson || '{}')
    } catch {
      /* malformed JSON, fallback to empty */
    }
    revKeys.forEach((key, yIndex) => {
      const grade = ab[key] || 'E'
      const val = GRADE_MAP[grade] ?? 0
      data.push([xIndex, yIndex, val, grade])
    })
  })

  return {
    data,
    yAxisData: [...dimLabels].reverse(),
  }
}

const RADAR_GRADE_MAP = { A: 95, B: 80, C: 65, D: 45, E: 20 }

export function gradeToRadarScore(grade) {
  return RADAR_GRADE_MAP[grade] || 45
}

export function buildInterviewRadarOption(echarts, scores) {
  return {
    radar: {
      indicator: [
        { name: '技术深度', max: 100 },
        { name: '知识广度', max: 100 },
        { name: '逻辑思维', max: 100 },
        { name: '表达清晰', max: 100 },
        { name: '应变能力', max: 100 },
        { name: '解题思路', max: 100 },
      ],
      shape: 'polygon',
      axisName: { color: '#cbd5e1', fontSize: 13, fontWeight: 600 },
      splitNumber: 5,
      splitArea: {
        areaStyle: {
          color: [
            'rgba(16,185,129,0.06)',
            'rgba(16,185,129,0.02)',
            'transparent',
            'transparent',
            'transparent',
          ],
        },
      },
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
    },
    tooltip: buildTooltipConfig({ trigger: 'item' }),
    series: [
      {
        type: 'radar',
        data: [
          {
            value: scores,
            name: '综合评估',
            symbolSize: 6,
            itemStyle: { color: '#10b981', borderColor: '#fff', borderWidth: 2 },
            lineStyle: { color: '#10b981', width: 2 },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(16,185,129,0.6)' },
                { offset: 1, color: 'rgba(16,185,129,0.1)' },
              ]),
            },
          },
        ],
      },
    ],
  }
}
