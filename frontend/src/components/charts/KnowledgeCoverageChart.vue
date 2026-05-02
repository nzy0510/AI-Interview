<template>
  <div class="knowledge-rose">
    <div ref="chartRef" class="knowledge-rose__chart" aria-label="知识领域覆盖玫瑰图"></div>
    <div class="knowledge-rose__summary">
      <div class="summary-main">
        <span class="summary-label">累计命中</span>
        <strong>{{ totalCovered }}</strong>
        <span class="summary-unit">个知识原子</span>
      </div>
      <div class="summary-list">
        <div v-for="item in sortedDetails" :key="item.category" class="summary-row">
          <span class="summary-dot" :style="{ background: item.color }"></span>
          <span class="summary-name">{{ item.category || '未分类' }}</span>
          <strong>{{ item.covered }}</strong>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { buildKnowledgeRoseOption, KNOWLEDGE_ROSE_COLORS } from '@/utils/chartOptions'

const props = defineProps({
  details: {
    type: Array,
    default: () => [],
  },
})

const chartRef = ref(null)
let chartInstance = null

const sortedDetails = computed(() => {
  return [...props.details]
    .sort((a, b) => (Number(b.covered) || 0) - (Number(a.covered) || 0))
    .map((item, index) => ({
      ...item,
      color: KNOWLEDGE_ROSE_COLORS[index % KNOWLEDGE_ROSE_COLORS.length],
    }))
})

const totalCovered = computed(() => {
  return props.details.reduce((sum, item) => sum + (Number(item.covered) || 0), 0)
})

const drawChart = async () => {
  await nextTick()
  if (!chartRef.value) return
  if (!chartInstance) chartInstance = echarts.init(chartRef.value)
  chartInstance.setOption(buildKnowledgeRoseOption(sortedDetails.value), true)
}

const handleResize = () => {
  if (chartInstance) chartInstance.resize()
}

onMounted(() => {
  drawChart()
  window.addEventListener('resize', handleResize)
})

watch(() => props.details, drawChart, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped>
.knowledge-rose {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(180px, 240px);
  gap: 18px;
  align-items: center;
}

.knowledge-rose__chart {
  width: 100%;
  height: 320px;
  min-width: 0;
}

.knowledge-rose__summary {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.summary-main {
  padding: 16px;
  border: 1px solid rgba(143, 47, 27, 0.12);
  border-radius: 12px;
  background: linear-gradient(180deg, #fff7ed, #fffdf9);
}

.summary-label,
.summary-unit {
  display: block;
  color: #6f6259;
  font-size: 12px;
  line-height: 1.4;
}

.summary-main strong {
  display: block;
  margin: 6px 0 4px;
  color: #8f2f1b;
  font-size: 34px;
  line-height: 1;
}

.summary-list {
  display: grid;
  gap: 8px;
}

.summary-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  color: #454652;
  font-size: 13px;
}

.summary-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
}

.summary-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-row strong {
  color: #191c1e;
}

@media (max-width: 760px) {
  .knowledge-rose {
    grid-template-columns: 1fr;
  }

  .knowledge-rose__chart {
    height: 280px;
  }
}
</style>
