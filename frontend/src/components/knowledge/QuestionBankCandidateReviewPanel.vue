<template>
  <div class="candidate-panel">
    <div class="candidate-head">
      <div>
        <p class="section-kicker">Candidate Review</p>
        <h2>候选审核 <el-badge :value="pendingCount" :hidden="pendingCount === 0" /></h2>
        <p class="section-desc">逐条核对原子内容和来源证据；只有接受的候选才能导入为草稿。</p>
      </div>
      <div class="candidate-head__actions">
        <el-button @click="$emit('refresh')">刷新候选</el-button>
        <el-button plain :disabled="!canDownload" :loading="actionLoading === 'download'" @click="$emit('download')">下载 JSON 包</el-button>
        <el-button type="primary" :disabled="!canImport" :loading="actionLoading === 'import'" @click="$emit('import')">导入为草稿</el-button>
      </div>
    </div>

    <el-alert v-if="!candidates.length && !loading" title="当前批次还没有候选原子" type="info" show-icon />
    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else-if="candidates.length">
      <div class="mobile-pane-switch" role="tablist">
        <button type="button" :class="{ 'is-active': mobilePane === 'content' }" @click="mobilePane = 'content'">内容</button>
        <button type="button" :class="{ 'is-active': mobilePane === 'evidence' }" @click="mobilePane = 'evidence'">来源证据</button>
      </div>
      <div class="review-grid">
        <aside class="candidate-queue" :class="{ 'mobile-hidden': mobilePane !== 'content' }">
          <div class="queue-head"><strong>候选队列</strong><span>{{ candidates.length }} 条</span></div>
          <button
            v-for="candidate in candidates"
            :key="candidate.id"
            type="button"
            class="candidate-item"
            :class="{ 'is-active': candidate.id === selectedId }"
            @click="selectCandidate(candidate)"
          >
            <span class="candidate-item__subject">{{ candidate.subject || '未命名考点' }}</span>
            <span class="candidate-item__meta"><el-tag size="small" :type="reviewType(candidate.status)" effect="plain">{{ reviewLabel(candidate.status) }}</el-tag><span>{{ candidate.category || '未分类' }}</span></span>
            <span v-if="candidate.duplicateHint" class="candidate-item__hint">疑似重复</span>
          </button>
        </aside>

        <section class="candidate-editor" :class="{ 'mobile-hidden': mobilePane !== 'content' }">
          <div class="editor-head"><h3>原子内容</h3><el-tag v-if="selectedCandidate" :type="reviewType(selectedCandidate.status)" effect="plain">{{ reviewLabel(selectedCandidate.status) }}</el-tag></div>
          <el-form v-if="selectedCandidate" label-position="top" class="editor-form">
            <div class="form-row"><el-form-item label="考点"><el-input v-model="editor.subject" /></el-form-item><el-form-item label="分类"><el-input v-model="editor.category" /></el-form-item><el-form-item label="难度"><el-select v-model="editor.difficulty" clearable><el-option label="junior" value="junior" /><el-option label="mid" value="mid" /><el-option label="senior" value="senior" /><el-option label="principal" value="principal" /></el-select></el-form-item></div>
            <el-form-item label="核心原则"><el-input v-model="editor.principles" type="textarea" :rows="5" /></el-form-item>
            <el-form-item label="常见陷阱"><el-input v-model="editor.pitfalls" type="textarea" :rows="3" /></el-form-item>
            <el-form-item label="追问路径（每行一条）"><el-input v-model="editor.followUpText" type="textarea" :rows="4" /></el-form-item>
          </el-form>
          <el-empty v-else description="请选择候选原子" />
        </section>

        <aside class="evidence-panel" :class="{ 'mobile-hidden': mobilePane !== 'evidence' }">
          <div class="editor-head"><h3>来源证据</h3><el-tag v-if="selectedCandidate?.selfCheck" :type="selectedCandidate.selfCheck.passed ? 'success' : 'warning'" effect="plain">自检 {{ selectedCandidate.selfCheck.passed ? '通过' : '需关注' }}</el-tag></div>
          <template v-if="selectedCandidate">
            <dl class="evidence-meta"><div><dt>来源</dt><dd>{{ selectedCandidate.sourceRef || selectedCandidate.source?.fileName || '未标注' }}</dd></div><div v-if="selectedCandidate.source?.page"><dt>页码/章节</dt><dd>{{ selectedCandidate.source.page }}</dd></div><div v-if="selectedCandidate.selfCheck?.confidence != null"><dt>置信度</dt><dd>{{ selectedCandidate.selfCheck.confidence }}</dd></div></dl>
            <blockquote v-for="(evidence, index) in evidenceItems" :key="`${index}-${evidence.quote}`">{{ evidence.quote || '未返回原文摘录' }}<cite>{{ evidence.pageOrSection || evidence.page || '' }}</cite></blockquote>
            <el-alert v-if="selectedCandidate.duplicateHint" title="疑似重复原子" :description="selectedCandidate.duplicateHint" type="warning" :closable="false" />
            <el-alert v-if="selectedCandidate.validationIssues?.length" title="校验提示" type="error" :closable="false"><ul class="issue-list"><li v-for="issue in selectedCandidate.validationIssues" :key="issue">{{ issue }}</li></ul></el-alert>
          </template>
          <el-empty v-else description="暂无来源证据" />
        </aside>
      </div>

      <footer v-if="selectedCandidate" class="review-actions">
        <span class="muted-text">{{ canReview ? '保存不会改变审核状态；接受后才可导入。' : '构建完成后才能审核候选。' }}</span>
        <div><el-button :disabled="!canReview" :loading="actionLoading.startsWith('candidate:')" @click="submit('SAVE')">保存修改</el-button><el-button type="danger" plain :disabled="!canReview" :loading="actionLoading.startsWith('candidate:')" @click="submit('REJECT')">拒绝</el-button><el-button type="primary" :disabled="!canReview" :loading="actionLoading.startsWith('candidate:')" @click="submit('ACCEPT')">接受候选</el-button></div>
      </footer>
    </template>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'

const props = defineProps({
  candidates: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  actionLoading: { type: String, default: '' },
  canReview: { type: Boolean, default: false },
  canImport: { type: Boolean, default: false },
  canDownload: { type: Boolean, default: false }
})

const emit = defineEmits(['refresh', 'update', 'import', 'download'])
const selectedId = ref(null)
const mobilePane = ref('content')
const editor = reactive({ subject: '', category: '', difficulty: '', principles: '', pitfalls: '', followUpText: '' })
const selectedCandidate = computed(() => props.candidates.find((candidate) => candidate.id === selectedId.value) || props.candidates[0] || null)
const pendingCount = computed(() => props.candidates.filter((candidate) => candidate.status === 'PENDING').length)
const evidenceItems = computed(() => {
  const selected = selectedCandidate.value
  if (!selected) return []
  if (Array.isArray(selected.sourceEvidence) && selected.sourceEvidence.length) return selected.sourceEvidence
  return selected.source?.evidence || (selected.source ? [selected.source] : [])
})

const syncEditor = (candidate) => {
  if (!candidate) return
  editor.subject = candidate.subject || ''
  editor.category = candidate.category || ''
  editor.difficulty = candidate.difficulty || ''
  editor.principles = candidate.principles || ''
  editor.pitfalls = candidate.pitfalls || ''
  editor.followUpText = (candidate.followUpPaths || []).join('\n')
}

const selectCandidate = (candidate) => {
  selectedId.value = candidate.id
  syncEditor(candidate)
}

watch(() => props.candidates, (next) => {
  if (!next.some((candidate) => candidate.id === selectedId.value)) selectedId.value = next[0]?.id || null
  syncEditor(next.find((candidate) => candidate.id === selectedId.value) || next[0])
}, { immediate: true })

const submit = (action) => {
  if (!props.canReview || !selectedCandidate.value) return
  emit('update', selectedCandidate.value.id, action, {
    subject: editor.subject.trim(),
    category: editor.category.trim(),
    difficulty: editor.difficulty,
    principles: editor.principles.trim(),
    pitfalls: editor.pitfalls.trim(),
    followUpPaths: editor.followUpText.split('\n').map((item) => item.trim()).filter(Boolean)
  })
}

const reviewLabel = (status) => ({ PENDING: '待审核', ACCEPTED: '已接受', REJECTED: '已拒绝' }[status] || status || '未知')
const reviewType = (status) => ({ PENDING: 'warning', ACCEPTED: 'success', REJECTED: 'info' }[status] || 'info')
</script>

<style scoped>
.candidate-panel {
  display: grid;
  gap: 16px;
  padding-top: 20px;
}

.candidate-head,
.candidate-head__actions,
.editor-head,
.review-actions,
.review-actions > div,
.queue-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.candidate-head h2,
.editor-head h3 {
  margin: 0;
  color: var(--app-text);
}

.candidate-head h2 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 1.35rem;
}

.section-kicker {
  margin: 0 0 4px;
  color: var(--app-text-muted);
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
}

.section-desc,
.muted-text {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 0.9rem;
}

.review-grid {
  display: grid;
  grid-template-columns: minmax(180px, 0.85fr) minmax(300px, 1.5fr) minmax(210px, 0.95fr);
  min-height: 540px;
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-2);
}

.candidate-queue,
.candidate-editor,
.evidence-panel {
  min-width: 0;
  padding: 14px;
}

.candidate-queue,
.evidence-panel {
  overflow: auto;
}

.candidate-queue,
.candidate-editor {
  border-right: 1px solid var(--app-border);
}

.queue-head {
  margin-bottom: 8px;
  color: var(--app-text-muted);
  font-size: 0.84rem;
}

.candidate-item {
  width: 100%;
  display: grid;
  gap: 7px;
  padding: 11px 10px;
  border: 0;
  border-top: 1px solid var(--app-border);
  background: transparent;
  color: var(--app-text);
  text-align: left;
  cursor: pointer;
}

.candidate-item.is-active {
  background: rgba(58, 56, 139, 0.08);
}

.candidate-item__subject {
  font-weight: 700;
  overflow-wrap: anywhere;
}

.candidate-item__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  color: var(--app-text-muted);
  font-size: 0.8rem;
}

.candidate-item__hint {
  color: var(--app-warning, #a56a00);
  font-size: 0.78rem;
}

.editor-form {
  margin-top: 16px;
}

.form-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 130px;
  gap: 10px;
}

.evidence-meta {
  display: grid;
  gap: 8px;
  margin: 16px 0;
}

.evidence-meta div {
  display: grid;
  grid-template-columns: 74px minmax(0, 1fr);
  gap: 8px;
}

.evidence-meta dt {
  color: var(--app-text-muted);
}

.evidence-meta dd {
  margin: 0;
  overflow-wrap: anywhere;
}

blockquote {
  margin: 0 0 10px;
  padding: 11px;
  border-left: 3px solid var(--app-primary);
  background: var(--app-surface);
  color: var(--app-text);
  white-space: pre-wrap;
}

blockquote cite {
  display: block;
  margin-top: 8px;
  color: var(--app-text-muted);
  font-size: 0.78rem;
  font-style: normal;
}

.issue-list {
  margin: 0;
  padding-left: 18px;
}

.review-actions {
  align-items: flex-end;
  padding: 12px 0 0;
  border-top: 1px solid var(--app-border);
}

.mobile-pane-switch {
  display: none;
}

@media (max-width: 920px) {
  .review-grid {
    grid-template-columns: minmax(200px, 0.85fr) minmax(0, 1.2fr);
  }

  .evidence-panel {
    grid-column: 1 / -1;
    border-top: 1px solid var(--app-border);
  }
}

@media (max-width: 640px) {
  .candidate-head,
  .review-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .candidate-head__actions,
  .review-actions > div {
    width: 100%;
  }

  .candidate-head__actions .el-button,
  .review-actions > div .el-button {
    flex: 1;
  }

  .mobile-pane-switch {
    display: flex;
    gap: 4px;
    padding: 4px;
    border: 1px solid var(--app-border);
    border-radius: var(--app-radius-md);
    background: var(--app-surface-2);
  }

  .mobile-pane-switch button {
    flex: 1;
    min-height: 34px;
    border: 0;
    border-radius: 7px;
    background: transparent;
    color: var(--app-text-muted);
  }

  .mobile-pane-switch button.is-active {
    background: var(--app-surface);
    color: var(--app-text);
    font-weight: 700;
  }

  .review-grid {
    display: block;
    min-height: 0;
  }

  .candidate-queue,
  .candidate-editor,
  .evidence-panel {
    border: 0;
  }

  .mobile-hidden {
    display: none;
  }

  .review-actions {
    position: sticky;
    bottom: 0;
    z-index: 2;
    padding: 12px;
    border: 1px solid var(--app-border);
    border-radius: var(--app-radius-md);
    background: var(--app-surface);
    box-shadow: var(--app-shadow-sm);
  }

  .form-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
