<template>
  <div class="dashboard-overlay">
    <div class="dashboard-container">
      <div class="dash-header">
        <span class="dash-title">📋 智能面试深度体检报告</span>
        <div class="dash-actions">
          <el-button @click="$emit('history')" plain>查看历史</el-button>
          <el-button type="primary" @click="$emit('home')">返回大厅</el-button>
        </div>
      </div>

      <div class="bento-grid">
        <div class="bento-card bento-score">
          <h3 class="bento-card-title">综合能力评定</h3>
          <div class="score-display">
            <el-progress type="dashboard" :percentage="displayScore" :color="scoreColor" :width="160" :stroke-width="12">
              <template #default="{ percentage }">
                <div class="score-val">{{ percentage }}</div>
                <div class="score-lbl">总分</div>
              </template>
            </el-progress>
            <div class="grade-badge" :style="{ color: scoreColor }">评级: {{ gradeLabel }}</div>
          </div>
        </div>

        <div class="bento-card bento-radar">
          <h3 class="bento-card-title">六维能力图谱</h3>
          <div class="echarts-container">
            <slot name="radar" />
          </div>
        </div>

        <div class="bento-card bento-kpis">
          <div v-for="metric in metrics" :key="metric.label" class="kpi-item">
            <div class="kpi-icon">{{ metric.icon }}</div>
            <div class="kpi-data">
              <div class="kpi-val" :class="{ highlight: metric.highlight }">
                {{ metric.value }}
                <span v-if="metric.unit" class="unit">{{ metric.unit }}</span>
              </div>
              <div class="kpi-lbl">{{ metric.label }}</div>
            </div>
          </div>
        </div>

        <div v-if="hasEmotionSection" class="bento-card bento-sentiment">
          <h3 class="bento-card-title">
            🧠 情感分析
            <el-tag v-if="emotionTag" size="small" type="success" effect="plain" style="margin-left: 8px">{{ emotionTag }}</el-tag>
          </h3>
          <div class="sentiment-content">
            <div v-if="emotionDistribution" class="emotion-bars">
              <div v-for="(val, key) in emotionDistribution" :key="key" class="em-bar-row">
                <span class="em-name">{{ emotionLabelFn(key) }}</span>
                <div class="em-bar-bg">
                  <div class="em-bar-fill" :style="{ width: `${val * 100}%`, background: emotionColorFn(key) }"></div>
                </div>
                <span class="em-pct">{{ (val * 100).toFixed(0) }}%</span>
              </div>
            </div>
            <div v-if="emotionSummaryText" class="sentiment-summary">
              <p>{{ emotionSummaryText }}</p>
            </div>
          </div>
        </div>

        <div class="bento-card bento-feedback">
          <h3 class="bento-card-title">🤖 面试官综合评价</h3>
          <div class="markdown-body custom-md" v-html="feedbackHtml"></div>
        </div>

        <div class="bento-card bento-roadmap">
          <h3 class="bento-card-title">🚀 定制化提升路线</h3>
          <div v-if="recommendations?.length" class="timeline-wrapper">
            <el-timeline>
              <el-timeline-item
                v-for="(rec, i) in recommendations"
                :key="i"
                :timestamp="rec.period"
                placement="top"
                :type="i === 0 ? 'success' : 'primary'"
                :hollow="i !== 0"
              >
                <div class="roadmap-item">
                  <h4>{{ rec.action }}</h4>
                  <p>{{ rec.detail }}</p>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
          <el-empty v-else description="暂无针对性建议" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  displayScore: { type: Number, required: true },
  score: { type: Number, required: true },
  scoreColor: { type: String, required: true },
  metrics: { type: Array, default: () => [] },
  emotionDistribution: { type: Object, default: null },
  emotionSummaryText: { type: String, default: '' },
  emotionTag: { type: String, default: '' },
  feedbackHtml: { type: String, required: true },
  recommendations: { type: Array, default: () => [] },
  emotionLabelFn: { type: Function, required: true },
  emotionColorFn: { type: Function, required: true }
})

defineEmits(['history', 'home'])

const gradeLabel = computed(() => {
  if (props.score >= 90) return '卓越 (A)'
  if (props.score >= 75) return '良好 (B)'
  return '及格 (C)'
})

const hasEmotionSection = computed(() => Boolean(props.emotionDistribution || props.emotionSummaryText))
</script>

<style scoped>
.dashboard-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 28px 20px;
  background:
    radial-gradient(circle at top, rgba(58, 56, 139, 0.20), transparent 35%),
    rgba(18, 20, 25, 0.84);
  backdrop-filter: blur(18px);
  overflow-y: auto;
}

.dashboard-container {
  width: min(1160px, 100%);
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.dash-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.dash-title {
  font-size: 24px;
  font-weight: 750;
  color: #f8fafc;
}

.dash-actions {
  display: flex;
  gap: 12px;
}

.bento-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.95fr) minmax(0, 1.25fr) minmax(0, 1.25fr);
  grid-template-rows: auto auto auto;
  gap: 18px;
}

.bento-card {
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(18px);
  border-radius: 18px;
  padding: 22px;
  display: flex;
  flex-direction: column;
  box-shadow:
    0 20px 50px rgba(0, 0, 0, 0.18),
    0 0 0 1px rgba(255, 255, 255, 0.05);
}

.bento-card-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 700;
  color: #e6ebf2;
  display: flex;
  align-items: center;
  gap: 8px;
}

.bento-score {
  grid-column: 1 / 2;
  grid-row: 1 / 3;
  align-items: center;
  text-align: center;
  justify-content: center;
}

.score-display {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  justify-content: center;
}

.score-val {
  font-size: 38px;
  font-weight: 800;
  line-height: 1;
}

.score-lbl {
  font-size: 13px;
  color: rgba(230, 235, 242, 0.72);
}

.grade-badge {
  font-size: 16px;
  font-weight: 700;
  margin-top: 6px;
  padding: 8px 16px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.05);
}

.bento-radar {
  grid-column: 2 / 4;
  grid-row: 1 / 2;
  min-height: 340px;
}

.bento-kpis {
  grid-column: 2 / 4;
  grid-row: 2 / 3;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  padding: 18px;
  align-items: center;
  background: rgba(255, 255, 255, 0.06);
}

.echarts-container {
  width: 100%;
  height: 100%;
  min-height: 300px;
  flex: 1;
}

:slotted(.radar-host) {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 300px;
}

.kpi-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 150px;
  flex: 1 1 150px;
}

.kpi-icon {
  font-size: 28px;
  background: rgba(255, 255, 255, 0.06);
  padding: 10px;
  border-radius: 14px;
  line-height: 1;
}

.kpi-val {
  font-size: 20px;
  font-weight: 800;
  color: #f8fafc;
}

.kpi-val.highlight {
  color: #d3c8ff;
}

.kpi-lbl {
  font-size: 12px;
  color: rgba(230, 235, 242, 0.62);
}

.unit {
  font-size: 12px;
  font-weight: normal;
  color: rgba(230, 235, 242, 0.50);
  margin-left: 2px;
}

.bento-sentiment {
  grid-column: 1 / 4;
  grid-row: 3 / 4;
}

.sentiment-content {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.emotion-bars {
  flex: 1;
  min-width: 280px;
}

.em-bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.em-name {
  width: 40px;
  font-size: 13px;
  color: rgba(230, 235, 242, 0.68);
  text-align: right;
  flex-shrink: 0;
}

.em-bar-bg {
  flex: 1;
  height: 18px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 999px;
  overflow: hidden;
}

.em-bar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.8s ease;
}

.em-pct {
  width: 40px;
  font-size: 13px;
  color: rgba(230, 235, 242, 0.78);
  text-align: right;
  flex-shrink: 0;
}

.sentiment-summary {
  flex: 1;
  min-width: 220px;
}

.sentiment-summary p {
  color: #dce3ec;
  font-size: 14px;
  line-height: 1.75;
  margin: 0;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 14px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.04) inset;
}

.bento-feedback {
  grid-column: 1 / 3;
  grid-row: 4 / 5;
  min-height: 300px;
  max-height: 500px;
  overflow-y: auto;
}

.bento-roadmap {
  grid-column: 3 / 4;
  grid-row: 4 / 5;
  min-height: 300px;
  max-height: 500px;
  overflow-y: auto;
}

.custom-md {
  color: #e4eaf1;
  font-size: 15px;
  line-height: 1.78;
}

.custom-md :deep(h1),
.custom-md :deep(h2),
.custom-md :deep(h3) {
  color: #f8fafc;
  margin-top: 0;
  padding-bottom: 8px;
}

.custom-md :deep(strong) {
  color: #b9afff;
}

.roadmap-item h4 {
  margin: 0 0 6px;
  color: #f8fafc;
  font-size: 15px;
}

.roadmap-item p {
  margin: 0;
  color: rgba(230, 235, 242, 0.80);
  font-size: 13.5px;
  line-height: 1.65;
}

:deep(.el-timeline-item__content) {
  padding-bottom: 16px;
}

:deep(.el-button.is-plain) {
  background: rgba(255, 255, 255, 0.55);
  color: #171a1f;
  box-shadow: 0 0 0 1px rgba(23, 26, 31, 0.06);
}

@media (max-width: 1100px) {
  .bento-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .bento-score,
  .bento-radar,
  .bento-kpis,
  .bento-sentiment,
  .bento-feedback,
  .bento-roadmap {
    grid-column: auto;
    grid-row: auto;
  }

  .bento-feedback,
  .bento-roadmap {
    max-height: none;
  }
}

@media (max-width: 860px) {
  .dash-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .dash-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .sentiment-content {
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .dashboard-overlay {
    padding: 18px 12px;
  }

  .dash-title {
    font-size: 20px;
  }

  .bento-card {
    padding: 18px;
  }
}
</style>
