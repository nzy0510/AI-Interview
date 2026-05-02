<template>
  <div class="mentor-page">
    <header class="mentor-header">
      <div class="brand-cluster">
        <el-button :icon="ArrowLeft" class="icon-button" circle @click="router.push('/')" />
        <div class="header-copy">
          <p class="eyebrow">AI Mentor</p>
          <h1 class="page-title">智能教练分析</h1>
          <p class="page-subtitle">基于你的面试历史，AI 教练为你提供个性化诊断与提升建议。</p>
        </div>
      </div>
      <el-button :icon="RefreshRight" :loading="refreshing" @click="refreshMentor">
        刷新分析
      </el-button>
    </header>

    <el-main class="page-body">
      <el-empty v-if="!mentorInsight && !loading" description="完成首次面试后，AI Mentor 将为你生成个性化分析报告" />

      <template v-if="mentorInsight">
        <!-- Diagnosis -->
        <section class="surface-card section-shell">
          <div class="section-head">
            <div>
              <p class="section-kicker">Diagnosis</p>
              <h2 class="section-title">综合诊断</h2>
            </div>
          </div>
          <p v-if="mentorInsight.diagnosis?.overview" class="diagnosis-overview">
            {{ mentorInsight.diagnosis.overview }}
          </p>
          <div class="diagnosis-grid">
            <div class="diagnosis-col">
              <h4 class="col-title">优势</h4>
              <ul v-if="mentorInsight.diagnosis?.strengths?.length">
                <li v-for="s in mentorInsight.diagnosis.strengths" :key="s">{{ s }}</li>
              </ul>
              <p v-else class="no-data-hint">暂无优势分析</p>
            </div>
            <div class="diagnosis-col">
              <h4 class="col-title">待提升</h4>
              <ul v-if="mentorInsight.diagnosis?.weaknesses?.length">
                <li v-for="w in mentorInsight.diagnosis.weaknesses" :key="w">{{ w }}</li>
              </ul>
              <p v-else class="no-data-hint">暂无弱点分析</p>
            </div>
          </div>
        </section>

        <!-- Risk Alerts -->
        <section v-if="mentorInsight.riskAlerts?.length" class="surface-card section-shell">
          <div class="section-head">
            <div>
              <p class="section-kicker">Risk</p>
              <h2 class="section-title">风险预警</h2>
            </div>
          </div>
          <div class="risk-list">
            <div v-for="alert in mentorInsight.riskAlerts" :key="alert.message"
                 :class="['risk-badge', alert.severity]">
              {{ alert.message }}
            </div>
          </div>
        </section>

        <!-- Actions -->
        <section v-if="mentorInsight.actions?.length" class="surface-card section-shell">
          <div class="section-head">
            <div>
              <p class="section-kicker">Actions</p>
              <h2 class="section-title">建议行动</h2>
            </div>
          </div>
          <div class="actions-list">
            <div v-for="act in mentorInsight.actions" :key="act.message" class="action-item">
              <el-tag :type="act.priority === 1 ? 'danger' : act.priority === 2 ? 'warning' : 'info'" size="small">
                {{ act.priority === 1 ? '立即' : act.priority === 2 ? '短期' : '长期' }}
              </el-tag>
              <div>
                <strong>{{ act.category }}</strong>
                <p>{{ act.message }}</p>
              </div>
            </div>
          </div>
        </section>

        <!-- Knowledge Coverage -->
        <section v-if="mentorInsight.knowledgeCoverage?.details?.length" class="surface-card section-shell">
          <div class="section-head">
            <div>
              <p class="section-kicker">Knowledge Coverage</p>
              <h2 class="section-title">知识领域覆盖</h2>
              <p class="section-desc">
                覆盖 {{ mentorInsight.knowledgeCoverage.coveredCategories }} / {{ mentorInsight.knowledgeCoverage.totalCategories }} 个领域
                （{{ mentorInsight.knowledgeCoverage.coveragePercent }}%）
              </p>
            </div>
          </div>
          <div class="coverage-list">
            <div v-for="item in mentorInsight.knowledgeCoverage.details" :key="item.category"
                 class="coverage-row">
              <span class="coverage-name">{{ item.category }}</span>
              <div class="coverage-bar-bg">
                <div class="coverage-bar-fill" :style="{ width: `${Math.min(item.percent, 100)}%` }" />
              </div>
              <span class="coverage-num">{{ item.covered }}</span>
            </div>
          </div>
        </section>
      </template>
    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getMentorInsightAPI, getKnowledgeCoverageAPI, refreshMentorInsightAPI } from '@/api/user'

const router = useRouter()
const mentorInsight = ref(null)
const refreshing = ref(false)
const loading = ref(true)

const loadMentorData = async () => {
  loading.value = true
  try {
    const cov = await getKnowledgeCoverageAPI()
    if (cov?.knowledgeCoverage?.details?.length) {
      if (!mentorInsight.value) mentorInsight.value = {}
      mentorInsight.value.knowledgeCoverage = cov.knowledgeCoverage
    }
  } catch { /* optional */ }
  try {
    const data = await getMentorInsightAPI()
    if (data) {
      mentorInsight.value = { ...mentorInsight.value, ...data }
    }
  } catch { /* Mentor unavailable */ }
  loading.value = false
}

const refreshMentor = async () => {
  refreshing.value = true
  try {
    const data = await refreshMentorInsightAPI()
    if (data) {
      mentorInsight.value = data
      // refreshMentorInsightAPI might not include knowledgeCoverage, reload it
      try {
        const cov = await getKnowledgeCoverageAPI()
        if (cov?.knowledgeCoverage?.details?.length) {
          mentorInsight.value.knowledgeCoverage = cov.knowledgeCoverage
        }
      } catch { /* optional */ }
    }
    ElMessage.success('AI Mentor 分析已刷新')
  } catch {
    ElMessage.error('刷新失败，请稍后重试')
  }
  refreshing.value = false
}

onMounted(loadMentorData)
</script>

<style scoped>
.mentor-page {
  min-height: 100vh;
  background: #f7f9fb;
  color: #191c1e;
}

.mentor-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 18px 32px;
  background: rgba(247, 249, 251, 0.9);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(69, 70, 82, 0.08);
}

.brand-cluster { display: flex; align-items: center; gap: 16px; min-width: 0; }
.icon-button { flex: 0 0 auto; }
.header-copy { min-width: 0; }

.eyebrow, .section-kicker {
  margin: 0 0 4px;
  color: #3a388b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-title { margin: 0; font-size: 24px; line-height: 1.2; font-weight: 800; color: #191c1e; }
.page-subtitle, .section-desc { margin: 6px 0 0; color: #5a6678; font-size: 14px; line-height: 1.6; }

.page-body {
  max-width: 960px;
  margin: 0 auto;
  padding: 28px 32px 40px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  box-sizing: border-box;
}

.surface-card {
  background: #fff;
  border: 1px solid rgba(69, 70, 82, 0.08);
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(25, 28, 30, 0.04);
}

.section-shell { padding: 24px; }

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.section-title { margin: 0; font-size: 20px; line-height: 1.25; font-weight: 800; color: #191c1e; }

.diagnosis-overview {
  margin: 0 0 18px;
  color: #191c1e;
  font-size: 15px;
  line-height: 1.7;
}

.diagnosis-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.diagnosis-col {
  padding: 18px;
  border-radius: 14px;
  background: #faf9f5;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.col-title {
  margin: 0 0 10px;
  font-size: 14px;
  font-weight: 700;
  color: #3a388b;
}

.diagnosis-col ul {
  margin: 0;
  padding-left: 18px;
}

.diagnosis-col li {
  color: #191c1e;
  font-size: 14px;
  line-height: 1.7;
  margin-bottom: 4px;
}

.no-data-hint { font-size: 13px; color: #94a3b8; margin: 0; }

.risk-list { display: flex; flex-direction: column; gap: 10px; }

.risk-badge {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
}
.risk-badge.warning { background: rgba(245, 158, 11, 0.1); color: #9a6b17; border: 1px solid rgba(245, 158, 11, 0.2); }
.risk-badge.danger { background: rgba(239, 68, 68, 0.08); color: #9a2c2c; border: 1px solid rgba(239, 68, 68, 0.15); }
.risk-badge.info { background: rgba(58, 56, 139, 0.06); color: #454652; border: 1px solid rgba(58, 56, 139, 0.1); }

.actions-list { display: flex; flex-direction: column; gap: 4px; }

.action-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 12px 0;
  border-bottom: 1px solid rgba(69, 70, 82, 0.06);
}
.action-item:last-child { border-bottom: 0; }
.action-item strong { display: block; color: #191c1e; font-size: 14px; }
.action-item p { margin: 4px 0 0; color: #5e5d59; font-size: 13px; line-height: 1.6; }

.coverage-list { display: grid; gap: 12px; }

.coverage-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.coverage-name {
  width: 100px;
  font-size: 13px;
  color: #454652;
  text-align: right;
  flex-shrink: 0;
}

.coverage-bar-bg {
  flex: 1;
  height: 10px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(58, 56, 139, 0.08);
}

.coverage-bar-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #3a388b, #5250a4);
  transition: width 0.6s ease;
}

.coverage-num {
  width: 40px;
  font-size: 13px;
  color: #454652;
  text-align: right;
}

:deep(.el-empty) { padding: 60px 0; }

@media (max-width: 960px) {
  .mentor-header { flex-direction: column; align-items: stretch; }
  .diagnosis-grid { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .mentor-header, .page-body { padding-left: 16px; padding-right: 16px; }
}
</style>
