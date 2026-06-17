import { nextTick, ref } from 'vue'
import * as echarts from 'echarts'
import { buildHeatmapData, buildHeatmapVisualMap, buildTooltipConfig } from '@/utils/chartOptions'

export function useChartRenderer({ abilityDimensions, chartData, chartMode, formatDate, selected, selectedAbility }) {
  const growthChartRef = ref(null)
  const miniRadarRef = ref(null)
  let growthChartInstance = null
  let miniRadarInstance = null

  const drawGrowthChart = () => {
    const container = growthChartRef.value
    if (!container || chartData.value.length === 0) {
      if (growthChartInstance) growthChartInstance.clear()
      return
    }

    if (!growthChartInstance) {
      growthChartInstance = echarts.init(container)
    }

    const data = chartData.value
    const xAxisData = data.map(r => formatDate(r.createTime).split(' ')[0])

    if (chartMode.value === 'score') {
      const scores = data.map(r => r.score || 0)
      growthChartInstance.setOption({
        grid: { top: 40, right: 30, bottom: 40, left: 50 },
        tooltip: buildTooltipConfig({ trigger: 'axis' }),
        xAxis: {
          type: 'category',
          data: xAxisData,
          axisLine: { lineStyle: { color: '#cfcdc4' } },
          axisLabel: { color: '#5e5d59' }
        },
        yAxis: {
          type: 'value',
          min: 'dataMin',
          max: 'dataMax',
          splitLine: { lineStyle: { color: '#e8e6dc', type: 'dashed' } },
          axisLabel: { color: '#5e5d59' }
        },
        series: [
          {
            name: '综合得分',
            data: scores,
            type: 'line',
            smooth: true,
            symbolSize: 8,
            itemStyle: { color: '#c96442' },
            lineStyle: { color: '#c96442', width: 3 },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(201, 100, 66, 0.35)' },
                { offset: 1, color: 'rgba(201, 100, 66, 0.0)' }
              ])
            }
          }
        ]
      }, true)
      return
    }

    if (chartMode.value === 'radar') {
      const dimKeys = Object.keys(abilityDimensions)
      const dimLabels = Object.values(abilityDimensions).map(d => d.label)
      const { data: hData, yAxisData } = buildHeatmapData(data, dimKeys, dimLabels)

      growthChartInstance.setOption({
        grid: { top: 30, right: 30, bottom: 50, left: 80 },
        tooltip: {
          ...buildTooltipConfig(),
          position: 'top',
          formatter: (params) => `${params.name} <br/> 维度: <b>${yAxisData[params.value[1]]}</b> <br/> 评级: <b>${params.value[3]}</b>`
        },
        xAxis: {
          type: 'category',
          data: xAxisData,
          axisLine: { lineStyle: { color: '#cfcdc4' } },
          axisLabel: { color: '#5e5d59' },
          splitArea: { show: true, areaStyle: { color: ['rgba(0,0,0,0.02)', 'transparent'] } }
        },
        yAxis: {
          type: 'category',
          data: yAxisData,
          axisLine: { lineStyle: { color: '#cfcdc4' } },
          axisLabel: { color: '#5e5d59' }
        },
        visualMap: buildHeatmapVisualMap(),
        series: [{
          name: '能力评级',
          type: 'heatmap',
          data: hData,
          label: {
            show: true,
            formatter: (p) => p.data[3],
            color: '#141413',
            fontSize: 12,
            fontWeight: 'bold'
          },
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowColor: 'rgba(201, 100, 66, 0.3)'
            }
          },
          itemStyle: {
            borderColor: '#faf9f5',
            borderWidth: 2,
            borderRadius: 4
          }
        }]
      }, true)
    }
  }

  const drawMiniRadar = () => {
    const container = miniRadarRef.value
    if (!container || !selected.value) return
    if (!miniRadarInstance) miniRadarInstance = echarts.init(container)

    const ability = selectedAbility.value
    const gradeToNum = (grade) => {
      const map = { A: 95, B: 80, C: 65, D: 45, E: 20 }
      return map[grade] || 20
    }
    const scores = [
      gradeToNum(ability.techDepth),
      gradeToNum(ability.breadth),
      gradeToNum(ability.logic),
      gradeToNum(ability.expression),
      gradeToNum(ability.adaptability),
      gradeToNum(ability.problemSolving)
    ]

    miniRadarInstance.setOption({
      radar: {
        indicator: [
          { name: '技术深度', max: 100 },
          { name: '知识广度', max: 100 },
          { name: '逻辑思维', max: 100 },
          { name: '表达清晰', max: 100 },
          { name: '应变能力', max: 100 },
          { name: '解题思路', max: 100 }
        ],
        shape: 'polygon',
        axisName: { color: '#5e5d59', fontSize: 12 },
        splitNumber: 4,
        splitArea: { areaStyle: { color: ['rgba(201, 100, 66, 0.05)', 'transparent'] } },
        axisLine: { lineStyle: { color: 'rgba(0,0,0,0.1)' } },
        splitLine: { lineStyle: { color: 'rgba(0,0,0,0.08)' } }
      },
      series: [{
        type: 'radar',
        data: [{
          value: scores,
          symbolSize: 4,
          itemStyle: { color: '#c96442' },
          lineStyle: { width: 2 },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(201, 100, 66, 0.45)' },
              { offset: 1, color: 'rgba(201, 100, 66, 0.08)' }
            ])
          }
        }]
      }]
    })
  }

  const refreshChart = () => {
    nextTick(() => drawGrowthChart())
  }

  const handleResize = () => {
    if (growthChartInstance) growthChartInstance.resize()
    if (miniRadarInstance) miniRadarInstance.resize()
  }

  const disposeCharts = () => {
    if (growthChartInstance) growthChartInstance.dispose()
    if (miniRadarInstance) miniRadarInstance.dispose()
    growthChartInstance = null
    miniRadarInstance = null
  }

  return {
    growthChartRef,
    miniRadarRef,
    drawGrowthChart,
    drawMiniRadar,
    refreshChart,
    handleResize,
    disposeCharts
  }
}
