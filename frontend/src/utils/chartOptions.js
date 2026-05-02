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

export const KNOWLEDGE_ROSE_COLORS = ['#f8e4c8', '#f4b46f', '#e27a3f', '#c9542f', '#8f2f1b']

export function buildKnowledgeRoseOption(details = []) {
  const source = Array.isArray(details) ? details : []
  const data = source.length
    ? source.map((item) => ({
        name: item.category || '未分类',
        value: Number(item.covered) || 0,
        total: Number(item.total) || 0,
        percent: Number(item.percent) || 0,
      }))
    : [{ name: '暂无覆盖', value: 1, total: 0, percent: 0 }]

  return {
    color: KNOWLEDGE_ROSE_COLORS,
    tooltip: {
      ...buildTooltipConfig({ trigger: 'item' }),
      formatter: (params) => {
        const item = params.data || {}
        if (item.name === '暂无覆盖') return '暂无知识覆盖数据'
        return `${params.marker}${item.name}<br/>命中知识原子: <b>${item.value}</b><br/>覆盖占比: <b>${item.percent}%</b>`
      },
    },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 0,
      top: 'middle',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#5e5d59', fontSize: 12 },
    },
    series: [
      {
        name: '知识领域覆盖',
        type: 'pie',
        roseType: 'radius',
        radius: ['24%', '76%'],
        center: ['38%', '50%'],
        minAngle: 8,
        avoidLabelOverlap: true,
        label: {
          color: '#3f3d38',
          fontSize: 12,
          formatter: '{b}',
        },
        labelLine: {
          lineStyle: { color: '#d4a88c' },
        },
        itemStyle: {
          borderColor: '#fffaf2',
          borderWidth: 2,
          borderRadius: 5,
        },
        emphasis: {
          scaleSize: 8,
          itemStyle: {
            shadowBlur: 18,
            shadowColor: 'rgba(143, 47, 27, 0.22)',
          },
        },
        data,
      },
    ],
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
