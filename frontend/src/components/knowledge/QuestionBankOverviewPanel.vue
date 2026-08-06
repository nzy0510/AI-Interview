<template>
  <div class="overview-panel">
    <div class="overview-head">
      <div>
        <p class="section-kicker">{{ isPublic ? 'Public Starter' : 'Private Workspace' }}</p>
        <h2 class="section-title">{{ position?.name }}</h2>
        <p class="section-desc">{{ position?.description || '默认知识库用于后续生成知识原子和面试 RAG。' }}</p>
      </div>
      <el-tag :type="canMaintain ? 'success' : 'info'" effect="plain">{{ isPublic ? (canMaintain ? '公共题库可维护' : '公共岗位只读') : '我的岗位' }}</el-tag>
    </div>

    <div class="summary-grid">
      <div class="summary-item"><span>默认知识库</span><strong>{{ position?.knowledgeBase?.name || '未创建' }}</strong></div>
      <div class="summary-item"><span>题库原子</span><strong>{{ atomTotal }}</strong></div>
      <div class="summary-item"><span>维护权限</span><strong>{{ canMaintain ? '可维护' : '只读' }}</strong></div>
      <div class="summary-item"><span>已发布向量</span><strong>{{ publishedCount }}</strong></div>
    </div>

    <section class="coverage-panel">
      <div class="section-head">
        <div>
          <p class="section-kicker">Coverage</p>
          <h3>知识领域覆盖</h3>
          <p class="section-desc">当前岗位在面试中的知识领域覆盖与命中情况。</p>
        </div>
      </div>
      <el-skeleton v-if="coverageLoading" :rows="3" animated />
      <KnowledgeCoverageChart v-else-if="coverageDetails.length" :details="coverageDetails" />
      <p v-else class="empty-hint">该岗位暂无已发布题库分类</p>
    </section>
  </div>
</template>

<script setup>
import KnowledgeCoverageChart from '@/components/charts/KnowledgeCoverageChart.vue'

defineProps({
  position: { type: Object, default: null },
  isPublic: { type: Boolean, default: false },
  canMaintain: { type: Boolean, default: false },
  atomTotal: { type: Number, default: 0 },
  publishedCount: { type: Number, default: 0 },
  coverageDetails: { type: Array, default: () => [] },
  coverageLoading: { type: Boolean, default: false }
})
</script>

<style scoped>
.overview-panel {
  display: grid;
  gap: 20px;
  padding-top: 20px;
}

.overview-head,
.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.section-kicker {
  margin: 0 0 4px;
  color: var(--app-text-muted);
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
}

.section-title,
.section-head h3 {
  margin: 0;
  color: var(--app-text);
}

.section-title {
  font-size: 1.4rem;
}

.section-head h3 {
  font-size: 1.05rem;
}

.section-desc {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 0.92rem;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.summary-item {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-2);
}

.summary-item span {
  color: var(--app-text-muted);
  font-size: 0.82rem;
}

.summary-item strong {
  overflow-wrap: anywhere;
}

.coverage-panel {
  padding-top: 18px;
  border-top: 1px solid var(--app-border);
}

.empty-hint {
  margin: 0;
  padding: 26px 0;
  color: var(--app-text-muted);
  text-align: center;
}

@media (max-width: 720px) {
  .overview-head,
  .section-head {
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 460px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
