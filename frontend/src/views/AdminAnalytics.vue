<template>
  <div class="ops-page">
    <header class="ops-header">
      <div>
        <p class="eyebrow">Operations</p>
        <h1 class="page-title">访问统计与成本保护</h1>
        <p class="page-subtitle">查看内测访问、关键行为、限流命中、用户反馈和今日额度使用。</p>
      </div>
      <div class="ops-controls">
        <el-select v-model="days" class="days-select" @change="loadSummary">
          <el-option :value="1" label="近 1 天" />
          <el-option :value="7" label="近 7 天" />
          <el-option :value="30" label="近 30 天" />
          <el-option :value="90" label="近 90 天" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadSummary">刷新</el-button>
      </div>
    </header>

    <section class="token-band">
      <el-input
        v-model="adminToken"
        type="password"
        show-password
        placeholder="输入 APP_ADMIN_TOKEN 后查看统计"
        @keyup.enter="loadSummary"
      />
      <el-button :loading="loading" @click="loadSummary">加载统计</el-button>
    </section>

    <el-empty v-if="!summary && !loading" description="输入管理令牌后加载运营数据" />

    <template v-if="summary">
      <section class="metric-grid">
        <div v-for="item in metrics" :key="item.label" class="metric-cell">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </section>

      <section class="ops-grid">
        <div class="section-block">
          <div class="section-head">
            <h2>关键事件</h2>
            <span>按天聚合</span>
          </div>
          <el-table :data="summary.dailyEvents || []" size="small" height="320">
            <el-table-column prop="day" label="日期" width="120" />
            <el-table-column prop="eventType" label="事件" min-width="180" />
            <el-table-column prop="count" label="次数" width="90" />
          </el-table>
        </div>

        <div class="section-block">
          <div class="section-head">
            <h2>热门路径</h2>
            <span>Top 20</span>
          </div>
          <el-table :data="summary.topPaths || []" size="small" height="320">
            <el-table-column prop="path" label="路径" min-width="220" />
            <el-table-column prop="count" label="次数" width="90" />
          </el-table>
        </div>
      </section>

      <section class="section-block">
        <div class="section-head">
          <h2>今日额度使用</h2>
          <span>最多展示 200 条</span>
        </div>
        <el-table :data="summary.todayQuotaUsage || []" size="small">
          <el-table-column prop="userId" label="用户 ID" width="100" />
          <el-table-column prop="quotaType" label="额度类型" min-width="180" />
          <el-table-column prop="usedCount" label="已用" width="90" />
          <el-table-column prop="limitCount" label="上限" width="90" />
          <el-table-column prop="updateTime" label="更新时间" min-width="180" />
        </el-table>
      </section>

      <section class="section-block">
        <div class="section-head">
          <h2>最新反馈</h2>
          <span>最近 20 条</span>
        </div>
        <el-table :data="summary.latestFeedback || []" size="small">
          <el-table-column prop="category" label="类型" width="110" />
          <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
          <el-table-column prop="contact" label="联系方式" width="180" show-overflow-tooltip />
          <el-table-column prop="pageUrl" label="页面" width="180" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" width="180" />
        </el-table>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAnalyticsSummaryAPI } from '@/api/analytics'

const LEGACY_TOKEN_KEY = 'interwise_admin_token'

const adminToken = ref('')
const days = ref(7)
const loading = ref(false)
const summary = ref(null)

const metrics = computed(() => {
  if (!summary.value) return []
  return [
    { label: 'PV', value: summary.value.pageViews || 0 },
    { label: 'UV', value: summary.value.uniqueVisitors || 0 },
    { label: '注册', value: summary.value.registrations || 0 },
    { label: '登录', value: summary.value.logins || 0 },
    { label: '开始面试', value: summary.value.interviewStarts || 0 },
    { label: '完成面试', value: summary.value.interviewFinishes || 0 },
    { label: '完成率', value: `${summary.value.interviewCompletionRate || 0}%` },
    { label: '简历解析', value: summary.value.resumeParses || 0 },
    { label: 'Mentor 生成', value: summary.value.mentorGenerations || 0 },
    { label: '反馈', value: summary.value.feedbackCount || 0 },
    { label: '错误', value: summary.value.errorCount || 0 },
    { label: '限流命中', value: summary.value.limitedCount || 0 }
  ]
})

const loadSummary = async () => {
  if (!adminToken.value.trim()) {
    ElMessage.warning('请先输入管理令牌')
    summary.value = null
    return
  }
  loading.value = true
  try {
    summary.value = await getAnalyticsSummaryAPI(days.value, adminToken.value.trim())
  } catch (e) {
    summary.value = null
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  localStorage.removeItem(LEGACY_TOKEN_KEY)
})
</script>

<style scoped>
.ops-page {
  min-height: 100vh;
  color: #191c1e;
}

.ops-header,
.token-band {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.eyebrow {
  margin: 0 0 4px;
  color: #3a388b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-title {
  margin: 0;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 800;
}

.page-subtitle {
  margin: 6px 0 0;
  color: #5a6678;
  font-size: 14px;
  line-height: 1.6;
}

.ops-controls {
  display: flex;
  align-items: center;
  gap: 10px;
}

.days-select {
  width: 132px;
}

.token-band {
  align-items: center;
  padding: 16px;
  background: #ffffff;
  border: 1px solid rgba(69, 70, 82, 0.08);
  border-radius: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.metric-cell {
  padding: 14px 16px;
  background: #ffffff;
  border: 1px solid rgba(69, 70, 82, 0.08);
  border-radius: 12px;
}

.metric-cell span {
  display: block;
  color: #5a6678;
  font-size: 12px;
}

.metric-cell strong {
  display: block;
  margin-top: 6px;
  color: #191c1e;
  font-size: 24px;
  line-height: 1.1;
}

.ops-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.section-block {
  margin-bottom: 18px;
  padding: 18px;
  background: #ffffff;
  border: 1px solid rgba(69, 70, 82, 0.08);
  border-radius: 12px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.section-head h2 {
  margin: 0;
  font-size: 16px;
  line-height: 1.3;
}

.section-head span {
  color: #7b8494;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .ops-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .ops-header,
  .token-band,
  .ops-controls {
    flex-direction: column;
    align-items: stretch;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .days-select {
    width: 100%;
  }
}
</style>
