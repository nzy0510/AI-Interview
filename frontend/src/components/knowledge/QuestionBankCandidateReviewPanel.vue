<template>
  <div class="candidate-panel">
    <div class="candidate-head">
      <div>
        <p class="section-kicker">Final Review</p>
        <h2>最终审核</h2>
        <p class="section-desc">系统已完成生成、监督和自动修复。你只需确认可发布项；仍需关注的候选会保留在本批次，不会混入题库。</p>
      </div>
      <div class="candidate-head__actions">
        <el-button data-testid="refresh-results" :disabled="editorDirty" @click="$emit('refresh')">刷新结果</el-button>
        <el-button
          v-if="attentionCandidates.length"
          type="primary"
          plain
          :disabled="!canReview || editorDirty"
          :loading="actionLoading === 'repair'"
          data-testid="repair-all"
          @click="openRepairDialog(attentionCandidates)"
        >
          让修复助手处理 {{ attentionCandidates.length }} 条
        </el-button>
      </div>
    </div>

    <div class="review-summary" aria-label="终审统计">
      <div><span>候选总数</span><strong>{{ candidates.length }}</strong></div>
      <div class="is-success"><span>可直接发布</span><strong>{{ finalizableCount }}</strong></div>
      <div :class="{ 'is-warning': exceptionCount > 0 }"><span>仍需关注</span><strong>{{ exceptionCount }}</strong></div>
      <div class="is-primary"><span>助手处理过</span><strong>{{ repairedCandidates.length }}</strong></div>
      <div class="is-success"><span>修复后通过</span><strong>{{ successfullyRepairedCandidates.length }}</strong></div>
    </div>

    <section v-if="candidates.length" class="batch-finalize" data-testid="batch-publish-bar">
      <div>
        <strong>{{ finalizableCount ? `已自动选中 ${finalizableCount} 条可发布原子` : '当前没有可发布原子' }}</strong>
        <p>机器监督通过和人工确认的候选会自动纳入本次终审，无需逐条确认。</p>
        <span v-if="exceptionCount">其余 {{ exceptionCount }} 条需关注项将保留在批次中，不会发布。</span>
      </div>
      <el-button
        data-testid="finalize-build"
        type="primary"
        size="large"
        :disabled="!canFinalize || editorDirty"
        :loading="actionLoading === 'finalize'"
        @click="$emit('finalize')"
      >
        {{ finalizableCount ? `一键发布全部 ${finalizableCount} 条` : '暂无可发布项' }}
      </el-button>
    </section>

    <el-alert
      v-if="exceptionCount > 0 && finalizableCount > 0"
      :title="`已有 ${finalizableCount} 条可发布`"
      :description="`剩余 ${exceptionCount} 条可让修复助手继续处理，也可暂时保留；它们不会阻断其他通过项发布。`"
      type="info"
      show-icon
      :closable="false"
    />

    <div v-if="candidates.length" class="candidate-filter" role="tablist" aria-label="候选范围">
      <button type="button" :disabled="editorDirty" :class="{ 'is-active': filterMode === 'publishable' }" @click="changeFilter('publishable')">待发布 {{ publishableCandidates.length }}</button>
      <button type="button" :disabled="editorDirty" :class="{ 'is-active': filterMode === 'attention' }" @click="changeFilter('attention')">需关注 {{ attentionCandidates.length }}</button>
      <button type="button" :disabled="editorDirty" :class="{ 'is-active': filterMode === 'repaired' }" @click="changeFilter('repaired')">助手处理 {{ repairedCandidates.length }}</button>
      <button type="button" :disabled="editorDirty" :class="{ 'is-active': filterMode === 'all' }" @click="changeFilter('all')">全部 {{ candidates.length }}</button>
    </div>
    <div v-if="editorDirty" class="dirty-warning" data-testid="dirty-warning">
      <span>当前候选有未保存修改。请先保存，或明确放弃修改后再刷新、切换或终审发布。</span>
      <el-button data-testid="discard-editor" type="warning" plain :disabled="candidateActionLoading" @click="discardEditorChanges">放弃未保存修改</el-button>
    </div>
    <el-alert v-if="!candidates.length && !loading" title="当前批次还没有候选原子" type="info" show-icon />
    <el-alert
      v-else-if="!visibleCandidates.length && !loading"
      :title="emptyFilterMessage"
      type="success"
      show-icon
      :closable="false"
    />
    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else-if="visibleCandidates.length">
      <div class="mobile-pane-switch" role="tablist">
        <button type="button" :class="{ 'is-active': mobilePane === 'content' }" @click="mobilePane = 'content'">内容</button>
        <button type="button" :class="{ 'is-active': mobilePane === 'evidence' }" @click="mobilePane = 'evidence'">依据与修改</button>
      </div>
      <div class="review-grid">
        <aside class="candidate-queue" :class="{ 'mobile-hidden': mobilePane !== 'content' }">
          <div class="queue-head"><strong>{{ currentFilterLabel }}</strong><span>{{ visibleCandidates.length }} 条</span></div>
          <button
            v-for="candidate in visibleCandidates"
            :key="candidate.id"
            type="button"
            class="candidate-item"
            :disabled="editorDirty"
            :class="{ 'is-active': candidate.id === selectedId }"
            @click="selectCandidate(candidate)"
          >
            <span class="candidate-item__subject">{{ candidate.subject || '未命名考点' }}</span>
            <span class="candidate-item__meta">
              <el-tag size="small" :type="machineReviewType(candidate.machineReviewStatus)" effect="plain">{{ machineReviewLabel(candidate.machineReviewStatus) }}</el-tag>
              <el-tag v-if="isAssistantHandled(candidate)" size="small" :type="repairType(candidate.repairStatus)" effect="plain">{{ repairLabel(candidate.repairStatus) }}</el-tag>
              <el-tag v-if="candidate.status !== 'PENDING'" size="small" :type="reviewType(candidate.status)" effect="plain">{{ reviewLabel(candidate.status) }}</el-tag>
              <span>{{ candidate.category || '未分类' }}</span>
            </span>
            <span v-if="candidate.duplicateHint" class="candidate-item__hint">疑似重复</span>
          </button>
        </aside>

        <section class="candidate-editor" :class="{ 'mobile-hidden': mobilePane !== 'content' }">
          <div class="editor-head"><h3>原子内容</h3><el-tag v-if="selectedCandidate" :type="reviewType(selectedCandidate.status)" effect="plain">{{ reviewLabel(selectedCandidate.status) }}</el-tag></div>
          <el-form v-if="selectedCandidate" label-position="top" class="editor-form" :disabled="candidateActionLoading">
            <div class="form-row"><el-form-item label="考点"><el-input v-model="editor.subject" /></el-form-item><el-form-item label="分类"><el-input v-model="editor.category" /></el-form-item><el-form-item label="难度"><el-select v-model="editor.difficulty" clearable><el-option label="junior" value="junior" /><el-option label="mid" value="mid" /><el-option label="senior" value="senior" /><el-option label="principal" value="principal" /></el-select></el-form-item></div>
            <el-form-item label="核心原则"><el-input v-model="editor.principles" type="textarea" :rows="5" /></el-form-item>
            <el-form-item label="常见陷阱"><el-input v-model="editor.pitfalls" type="textarea" :rows="3" /></el-form-item>
            <el-form-item label="追问路径（每行一条）"><el-input v-model="editor.followUpText" type="textarea" :rows="4" /></el-form-item>
          </el-form>
          <el-empty v-else description="请选择候选原子" />
        </section>

        <aside class="evidence-panel" :class="{ 'mobile-hidden': mobilePane !== 'evidence' }">
          <div class="editor-head"><h3>依据与修改</h3><el-tag v-if="selectedCandidate" :type="machineReviewType(selectedCandidate.machineReviewStatus)" effect="plain">{{ machineReviewLabel(selectedCandidate.machineReviewStatus) }}</el-tag></div>
          <template v-if="selectedCandidate">
            <dl class="evidence-meta"><div><dt>来源</dt><dd>{{ selectedCandidate.sourceRef || selectedCandidate.source?.fileName || '未标注' }}</dd></div><div v-if="selectedCandidate.source?.page"><dt>页码/章节</dt><dd>{{ selectedCandidate.source.page }}</dd></div><div v-if="selectedCandidate.machineReviewScore != null"><dt>监督评分</dt><dd>{{ selectedCandidate.machineReviewScore }}</dd></div></dl>
            <blockquote v-for="(evidence, index) in evidenceItems" :key="`${index}-${evidence.quote}`">{{ evidence.quote || '未返回原文摘录' }}<cite>{{ evidence.pageOrSection || evidence.page || '' }}</cite></blockquote>
            <el-alert v-if="selectedCandidate.duplicateHint" title="疑似重复原子" :description="selectedCandidate.duplicateHint" type="warning" :closable="false" />
            <el-alert v-if="selectedCandidate.machineReviewIssues?.length" title="为什么需要关注" type="warning" :closable="false"><ul class="issue-list"><li v-for="issue in selectedCandidate.machineReviewIssues" :key="issue">{{ issue }}</li></ul></el-alert>

            <section v-if="selectedCandidate.repairSummary" class="repair-summary">
              <strong>修复助手说明</strong>
              <p>{{ selectedCandidate.repairSummary }}</p>
            </section>

            <section v-if="latestRepairChanges.length || olderRepairChanges.length" class="change-section">
              <strong>助手修改记录</strong>
              <div v-if="latestRepairChanges.length" class="change-stack" data-testid="latest-repair-changes">
                <article v-for="(change, index) in latestRepairChanges" :key="`${change.field}-${index}`" class="change-card">
                  <header><span>{{ fieldLabel(change.field) }}</span><small>{{ change.source }}</small></header>
                  <div><span>修改前</span><p>{{ formatChangeValue(change.before) }}</p></div>
                  <div class="is-after"><span>修改后</span><p>{{ formatChangeValue(change.after) }}</p></div>
                </article>
              </div>
              <details v-if="olderRepairChanges.length" class="older-changes" data-testid="older-repair-changes">
                <summary>查看较早的 {{ olderRepairChanges.length }} 条修改</summary>
                <div class="change-stack">
                  <article v-for="(change, index) in olderRepairChanges" :key="`${change.field}-${index}`" class="change-card">
                    <header><span>{{ fieldLabel(change.field) }}</span><small>{{ change.source }}</small></header>
                    <div><span>修改前</span><p>{{ formatChangeValue(change.before) }}</p></div>
                    <div class="is-after"><span>修改后</span><p>{{ formatChangeValue(change.after) }}</p></div>
                  </article>
                </div>
              </details>
            </section>

            <section v-if="suggestionChanges.length" class="change-section">
              <strong>监督器建议</strong>
              <article v-for="change in suggestionChanges" :key="change.field" class="change-card is-suggestion">
                <header><span>{{ fieldLabel(change.field) }}</span><small>{{ change.source }}</small></header>
                <div><span>当前内容</span><p>{{ formatChangeValue(change.before) }}</p></div>
                <div class="is-after"><span>建议内容</span><p>{{ formatChangeValue(change.after) }}</p></div>
              </article>
            </section>
          </template>
          <el-empty v-else description="暂无来源与修改记录" />
        </aside>
      </div>

      <footer v-if="selectedCandidate" class="review-actions">
        <span class="muted-text">{{ reviewActionHint }}</span>
        <div>
          <el-button
            v-if="isAttentionCandidate(selectedCandidate)"
            type="primary"
            plain
            data-testid="repair-current"
            :disabled="!canReview || editorDirty"
            :loading="actionLoading === 'repair'"
            @click="openRepairDialog([selectedCandidate])"
          >让助手修改这条</el-button>
          <el-button :disabled="!canReview" :loading="actionLoading.startsWith('candidate:')" @click="submit('SAVE')">保存人工修改</el-button>
          <el-button type="danger" plain :disabled="!canReview" :loading="actionLoading.startsWith('candidate:')" @click="submit('REJECT')">不发布</el-button>
          <el-tag v-if="isQuestionBankCandidateFinalizable(selectedCandidate)" data-testid="batch-included" type="success" effect="plain">已纳入本次批量发布</el-tag>
          <el-button v-else type="primary" :disabled="!canReview" :loading="actionLoading.startsWith('candidate:')" @click="submit('ACCEPT')">确认可发布</el-button>
        </div>
      </footer>
    </template>

    <el-dialog v-model="repairDialogVisible" title="让修复助手再处理" width="min(560px, 92vw)" :close-on-click-modal="false">
      <div class="repair-dialog-copy">
        <p>助手会根据监督问题和原文依据修改 {{ repairTargetIds.length }} 条候选，完成后自动再次监督。仍不确定的内容会继续留在“需关注”。</p>
        <div class="repair-targets"><span v-for="candidate in repairTargets.slice(0, 5)" :key="candidate.id">{{ candidate.subject || `候选 #${candidate.id}` }}</span><span v-if="repairTargets.length > 5">另 {{ repairTargets.length - 5 }} 条</span></div>
        <label for="repair-instruction">补充要求（可选）</label>
        <el-input id="repair-instruction" v-model="repairInstruction" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="例如：只依据原文修正，不要扩写文档未提及的结论。" />
      </div>
      <template #footer><el-button @click="repairDialogVisible = false">取消</el-button><el-button data-testid="repair-confirm" type="primary" :disabled="!repairTargetIds.length" @click="submitRepair">开始修复并复查</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import {
  isQuestionBankCandidateFinalizable,
  isQuestionBankCandidateRepairVerified,
  wasQuestionBankCandidateRepairAttempted
} from '@/utils/knowledgeWorkspace'

const props = defineProps({
  candidates: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  actionLoading: { type: String, default: '' },
  canReview: { type: Boolean, default: false },
  canFinalize: { type: Boolean, default: false },
  exceptionCount: { type: Number, default: 0 },
  finalizableCount: { type: Number, default: 0 }
})

const emit = defineEmits(['refresh', 'update', 'repair', 'finalize', 'dirty-change'])
const selectedId = ref(null)
const mobilePane = ref('content')
const filterMode = ref('publishable')
const repairDialogVisible = ref(false)
const repairTargets = ref([])
const repairInstruction = ref('')
const editor = reactive({ subject: '', category: '', difficulty: '', principles: '', pitfalls: '', followUpText: '' })

const candidateEditorValue = (candidate) => ({
  subject: String(candidate?.subject || '').trim(),
  category: String(candidate?.category || '').trim(),
  difficulty: String(candidate?.difficulty || ''),
  principles: String(candidate?.principles || '').trim(),
  pitfalls: String(candidate?.pitfalls || '').trim(),
  followUpPaths: Array.isArray(candidate?.followUpPaths)
    ? candidate.followUpPaths.map((item) => String(item).trim()).filter(Boolean)
    : []
})
const currentEditorValue = () => ({
  subject: editor.subject.trim(),
  category: editor.category.trim(),
  difficulty: editor.difficulty,
  principles: editor.principles.trim(),
  pitfalls: editor.pitfalls.trim(),
  followUpPaths: editor.followUpText.split('\n').map((item) => item.trim()).filter(Boolean)
})

const isAttentionCandidate = (candidate) => {
  const reviewStatus = String(candidate?.status || candidate?.reviewStatus || '').toUpperCase()
  if (['ACCEPTED', 'REJECTED'].includes(reviewStatus)) return false
  return !['AUTO_PASS', 'AUTO_REJECT', 'SKIPPED'].includes(String(candidate?.machineReviewStatus || '').toUpperCase())
}
const isAssistantHandled = wasQuestionBankCandidateRepairAttempted
const attentionCandidates = computed(() => props.candidates.filter(isAttentionCandidate))
const publishableCandidates = computed(() => props.candidates.filter(isQuestionBankCandidateFinalizable))
const repairedCandidates = computed(() => props.candidates.filter(isAssistantHandled))
const successfullyRepairedCandidates = computed(() => props.candidates.filter(isQuestionBankCandidateRepairVerified))
const visibleCandidates = computed(() => {
  if (filterMode.value === 'all') return props.candidates
  if (filterMode.value === 'repaired') return repairedCandidates.value
  if (filterMode.value === 'publishable') return publishableCandidates.value
  return attentionCandidates.value
})
const currentFilterLabel = computed(() => ({ publishable: '待发布', attention: '需关注', repaired: '助手处理', all: '全部候选' }[filterMode.value]))
const emptyFilterMessage = computed(() => filterMode.value === 'publishable'
  ? '当前没有可发布候选'
  : filterMode.value === 'attention'
  ? '没有需要关注的候选，可直接确认发布'
  : filterMode.value === 'repaired' ? '本批次没有助手处理记录' : '当前没有候选')
const selectedCandidate = computed(() => visibleCandidates.value.find((candidate) => candidate.id === selectedId.value) || visibleCandidates.value[0] || null)
const editorDirty = computed(() => Boolean(selectedCandidate.value)
  && JSON.stringify(currentEditorValue()) !== JSON.stringify(candidateEditorValue(selectedCandidate.value)))
const repairTargetIds = computed(() => repairTargets.value.map((candidate) => candidate.id).filter((id) => id != null))
const candidateActionLoading = computed(() => props.actionLoading.startsWith('candidate:'))
const evidenceItems = computed(() => {
  const selected = selectedCandidate.value
  if (!selected) return []
  if (Array.isArray(selected.sourceEvidence) && selected.sourceEvidence.length) return selected.sourceEvidence
  return selected.source?.evidence || (selected.source ? [selected.source] : [])
})

const fieldNames = {
  subject: '考点',
  category: '分类',
  difficulty: '难度',
  tags: '标签',
  principles: '核心原则',
  pitfalls: '常见陷阱',
  followUpPaths: '追问路径',
  follow_up_paths: '追问路径'
}
const fieldLabel = (field) => fieldNames[field] || String(field || '内容')
const formatChangeValue = (value) => {
  if (value == null || value === '') return '未记录'
  if (Array.isArray(value)) return value.length ? value.map(formatChangeValue).join('；') : '无'
  if (typeof value === 'object') {
    const entries = Object.entries(value)
    return entries.length ? entries.map(([key, item]) => `${fieldLabel(key)}：${formatChangeValue(item)}`).join('；') : '无'
  }
  return String(value)
}
const normalizeChange = (change, source) => ({
  field: change?.field || change?.key || change?.name || 'content',
  before: change?.before ?? change?.oldValue ?? change?.from,
  after: change?.after ?? change?.newValue ?? change?.to ?? change?.value,
  source: change?.source || source
})
const changesFromRecord = (record, index) => {
  const source = record?.source || record?.actor || `修复助手 · 第 ${record?.round || index + 1} 轮`
  const listed = record?.changes || record?.fieldChanges
  if (Array.isArray(listed)) return listed.map((change) => normalizeChange(change, source))
  if (listed && typeof listed === 'object') {
    return Object.entries(listed).map(([field, value]) => normalizeChange(
      value && typeof value === 'object' ? { field, ...value } : { field, after: value },
      source
    ))
  }
  const before = record?.before || record?.beforeValues || record?.original || {}
  const after = record?.after || record?.afterValues || record?.updated || record?.patch || {}
  const fields = Array.isArray(record?.changedFields)
    ? record.changedFields
    : Array.from(new Set([...Object.keys(before), ...Object.keys(after)]))
  return fields.map((field) => ({ field, before: before[field], after: after[field], source }))
}
const repairChangeGroups = computed(() => (selectedCandidate.value?.repairHistory || [])
  .map((record, index) => changesFromRecord(record, index)
    .filter((change) => change.before !== undefined || change.after !== undefined)))
const latestRepairChanges = computed(() => repairChangeGroups.value.at(-1) || [])
const olderRepairChanges = computed(() => repairChangeGroups.value.slice(0, -1).flat())
const suggestionChanges = computed(() => {
  const candidate = selectedCandidate.value
  const patch = candidate?.machineSuggestedPatch
  if (!patch || typeof patch !== 'object') return []
  return Object.entries(patch)
    .filter(([field, after]) => formatChangeValue(candidate?.[field]) !== formatChangeValue(after))
    .map(([field, after]) => ({ field, before: candidate?.[field], after, source: '质量监督建议' }))
})

const syncEditor = (candidate) => {
  if (!candidate) {
    Object.assign(editor, { subject: '', category: '', difficulty: '', principles: '', pitfalls: '', followUpText: '' })
    return
  }
  editor.subject = candidate.subject || ''
  editor.category = candidate.category || ''
  editor.difficulty = candidate.difficulty || ''
  editor.principles = candidate.principles || ''
  editor.pitfalls = candidate.pitfalls || ''
  editor.followUpText = (candidate.followUpPaths || []).join('\n')
}
const selectCandidate = (candidate) => {
  if (editorDirty.value) return
  selectedId.value = candidate.id
  syncEditor(candidate)
}
const changeFilter = (mode) => {
  if (editorDirty.value) return
  filterMode.value = mode
}
const discardEditorChanges = () => syncEditor(selectedCandidate.value)

watch([() => props.candidates, filterMode], () => {
  const visible = visibleCandidates.value
  if (!visible.some((candidate) => candidate.id === selectedId.value)) selectedId.value = visible[0]?.id || null
  syncEditor(visible.find((candidate) => candidate.id === selectedId.value) || visible[0])
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
const openRepairDialog = (candidates) => {
  if (!props.canReview || editorDirty.value) return
  repairTargets.value = candidates.filter(isAttentionCandidate)
  repairInstruction.value = ''
  repairDialogVisible.value = repairTargets.value.length > 0
}
const submitRepair = () => {
  if (!repairTargetIds.value.length) return
  emit('repair', repairTargetIds.value, repairInstruction.value.trim())
  repairDialogVisible.value = false
}

const reviewActionHint = computed(() => props.canReview
  ? editorDirty.value
    ? '当前有未保存修改；保存或放弃后才能继续其他操作。'
    : isAttentionCandidate(selectedCandidate.value)
    ? '可让助手按监督意见重做；也可人工修改、确认或不发布。'
    : '这是自动通过或已处理项，确认整批发布前仍可抽查。'
  : '当前正在自动处理，完成后才能操作候选。')
const reviewLabel = (status) => ({ PENDING: '待最终确认', ACCEPTED: '已确认发布', REJECTED: '不发布' }[status] || status || '未知')
const reviewType = (status) => ({ PENDING: 'warning', ACCEPTED: 'success', REJECTED: 'info' }[status] || 'info')
const machineReviewLabel = (status) => ({ AUTO_PASS: '监督通过', NEEDS_HUMAN: '需关注', AUTO_REJECT: '自动排除', FAILED: '监督失败', PENDING: '等待监督', RUNNING: '正在监督' }[String(status || '').toUpperCase()] || '未监督')
const machineReviewType = (status) => ({ AUTO_PASS: 'success', NEEDS_HUMAN: 'warning', AUTO_REJECT: 'info', FAILED: 'danger', PENDING: 'info', RUNNING: 'warning' }[String(status || '').toUpperCase()] || 'info')
const repairLabel = (status) => ({ PENDING: '等待修复', RUNNING: '正在修复', REPAIRED: '已修改待复查', VERIFIED: '助手已修复', DROPPED: '助手已排除', EXHAUSTED: '仍需关注', SUCCEEDED: '助手已修复', COMPLETED: '助手已修复', FAILED: '修复未通过', ERROR: '修复失败', NOT_STARTED: '未修复', NOT_NEEDED: '无需修复' }[String(status || '').toUpperCase()] || '助手已处理')
const repairType = (status) => ({ REPAIRED: 'warning', VERIFIED: 'success', DROPPED: 'info', EXHAUSTED: 'warning', SUCCEEDED: 'success', COMPLETED: 'success', FAILED: 'danger', ERROR: 'danger', PENDING: 'warning', RUNNING: 'warning' }[String(status || '').toUpperCase()] || 'info')

watch(editorDirty, (dirty) => emit('dirty-change', dirty), { immediate: true })
</script>

<style scoped>
.candidate-panel { display: grid; gap: 16px; padding-top: 20px; }
.candidate-head, .candidate-head__actions, .editor-head, .review-actions, .review-actions > div, .queue-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.candidate-head h2, .editor-head h3 { margin: 0; color: var(--app-text); }
.candidate-head h2 { font-size: 1.35rem; }
.candidate-head__actions { flex-wrap: wrap; justify-content: flex-end; }
.section-kicker { margin: 0 0 4px; color: var(--app-text-muted); font-size: 0.78rem; font-weight: 700; text-transform: uppercase; }
.section-desc, .muted-text { margin: 6px 0 0; color: var(--app-text-muted); font-size: 0.9rem; }
.review-summary { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; }
.review-summary > div { display: grid; gap: 4px; padding: 14px; border: 1px solid var(--app-border); border-radius: var(--app-radius-md); background: var(--app-surface-2); }
.review-summary span { color: var(--app-text-muted); font-size: 0.82rem; }
.review-summary strong { color: var(--app-text); font-size: 1.35rem; }
.review-summary .is-success strong { color: var(--app-success, #16835b); }
.review-summary .is-warning strong { color: var(--app-warning, #a56a00); }
.review-summary .is-primary strong { color: var(--app-primary); }
.batch-finalize { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 16px 18px; border: 1px solid rgba(58, 56, 139, 0.28); border-radius: var(--app-radius-md); background: rgba(58, 56, 139, 0.06); }
.batch-finalize > div { display: grid; gap: 5px; }
.batch-finalize strong { color: var(--app-text); font-size: 1rem; }
.batch-finalize p { margin: 0; color: var(--app-text-muted); line-height: 1.5; }
.batch-finalize span { color: var(--app-warning, #a56a00); font-size: 0.84rem; }
.batch-finalize .el-button { min-width: 190px; }
.candidate-filter { display: inline-flex; width: fit-content; gap: 4px; padding: 4px; border: 1px solid var(--app-border); border-radius: var(--app-radius-md); background: var(--app-surface-2); }
.candidate-filter button { min-height: 32px; padding: 0 12px; border: 0; border-radius: 7px; background: transparent; color: var(--app-text-muted); cursor: pointer; }
.candidate-filter button.is-active { background: var(--app-surface); color: var(--app-text); font-weight: 700; }
.candidate-filter button:disabled { cursor: not-allowed; opacity: 0.55; }
.dirty-warning { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 11px 12px; border: 1px solid rgba(208, 139, 45, 0.35); border-radius: var(--app-radius-md); background: rgba(208, 139, 45, 0.08); color: var(--app-warning, #a56a00); }
.review-grid { display: grid; grid-template-columns: minmax(180px, 0.8fr) minmax(300px, 1.35fr) minmax(260px, 1.1fr); height: clamp(540px, 68vh, 720px); overflow: hidden; border: 1px solid var(--app-border); border-radius: var(--app-radius-md); background: var(--app-surface-2); }
.candidate-queue, .candidate-editor, .evidence-panel { min-width: 0; padding: 14px; }
.candidate-queue, .candidate-editor, .evidence-panel { overflow-x: hidden; overflow-y: auto; }
.candidate-queue { scrollbar-gutter: stable; }
.candidate-queue, .candidate-editor { border-right: 1px solid var(--app-border); }
.queue-head { margin-bottom: 8px; color: var(--app-text-muted); font-size: 0.84rem; }
.candidate-item { width: 100%; display: grid; gap: 7px; padding: 11px 10px; border: 0; border-top: 1px solid var(--app-border); background: transparent; color: var(--app-text); text-align: left; cursor: pointer; }
.candidate-item.is-active { background: rgba(58, 56, 139, 0.08); }
.candidate-item__subject { font-weight: 700; overflow-wrap: anywhere; }
.candidate-item__meta { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; color: var(--app-text-muted); font-size: 0.8rem; }
.candidate-item__hint { color: var(--app-warning, #a56a00); font-size: 0.78rem; }
.editor-form { margin-top: 16px; }
.form-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 130px; gap: 10px; }
.evidence-meta { display: grid; gap: 8px; margin: 16px 0; }
.evidence-meta div { display: grid; grid-template-columns: 74px minmax(0, 1fr); gap: 8px; }
.evidence-meta dt { color: var(--app-text-muted); }
.evidence-meta dd { margin: 0; overflow-wrap: anywhere; }
blockquote { margin: 0 0 10px; padding: 11px; border-left: 3px solid var(--app-primary); background: var(--app-surface); color: var(--app-text); white-space: pre-wrap; }
blockquote cite { display: block; margin-top: 8px; color: var(--app-text-muted); font-size: 0.78rem; font-style: normal; }
.issue-list { margin: 0; padding-left: 18px; }
.repair-summary, .change-section, .change-stack { display: grid; gap: 8px; margin-top: 12px; }
.change-stack { margin-top: 0; }
.repair-summary { padding: 11px; border: 1px solid rgba(58, 56, 139, 0.2); border-radius: 8px; background: rgba(58, 56, 139, 0.06); }
.repair-summary p { margin: 0; color: var(--app-text-muted); line-height: 1.55; }
.change-card { overflow: hidden; border: 1px solid var(--app-border); border-radius: 8px; background: var(--app-surface); }
.change-card header { display: flex; justify-content: space-between; gap: 8px; padding: 9px 10px; border-bottom: 1px solid var(--app-border); }
.change-card header span { color: var(--app-text); font-weight: 700; }
.change-card header small, .change-card > div > span { color: var(--app-text-muted); }
.change-card > div { display: grid; grid-template-columns: 58px minmax(0, 1fr); gap: 8px; padding: 9px 10px; }
.change-card > div.is-after { background: rgba(47, 158, 106, 0.07); }
.change-card.is-suggestion > div.is-after { background: rgba(208, 139, 45, 0.08); }
.change-card p { margin: 0; color: var(--app-text); line-height: 1.5; white-space: pre-wrap; overflow-wrap: anywhere; }
.older-changes { padding: 8px 10px; border: 1px solid var(--app-border); border-radius: 8px; background: var(--app-surface); }
.older-changes summary { color: var(--app-text-muted); font-weight: 700; cursor: pointer; }
.older-changes[open] summary { margin-bottom: 8px; }
.review-actions { align-items: flex-end; padding: 12px 0 0; border-top: 1px solid var(--app-border); }
.review-actions > div { flex-wrap: wrap; justify-content: flex-end; }
.repair-dialog-copy { display: grid; gap: 12px; }
.repair-dialog-copy > p { margin: 0; color: var(--app-text-muted); line-height: 1.6; }
.repair-dialog-copy label { color: var(--app-text); font-weight: 700; }
.repair-targets { display: flex; flex-wrap: wrap; gap: 6px; }
.repair-targets span { padding: 4px 8px; border-radius: 999px; background: var(--app-surface-2); color: var(--app-text-muted); font-size: 0.8rem; }
.mobile-pane-switch { display: none; }
@media (max-width: 1080px) { .review-grid { grid-template-columns: minmax(200px, 0.8fr) minmax(0, 1.2fr); grid-template-rows: minmax(0, 1fr) minmax(200px, 0.8fr); } .evidence-panel { grid-column: 1 / -1; border-top: 1px solid var(--app-border); } }
@media (max-width: 720px) { .review-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 640px) {
  .candidate-head, .batch-finalize, .review-actions { align-items: stretch; flex-direction: column; }
  .candidate-head__actions, .review-actions > div { width: 100%; }
  .candidate-head__actions .el-button, .batch-finalize .el-button, .review-actions > div .el-button { flex: 1; width: 100%; }
  .candidate-filter { display: flex; width: auto; }
  .candidate-filter button { flex: 1; padding: 0 8px; }
  .dirty-warning { align-items: stretch; flex-direction: column; }
  .mobile-pane-switch { display: flex; gap: 4px; padding: 4px; border: 1px solid var(--app-border); border-radius: var(--app-radius-md); background: var(--app-surface-2); }
  .mobile-pane-switch button { flex: 1; min-height: 34px; border: 0; border-radius: 7px; background: transparent; color: var(--app-text-muted); }
  .mobile-pane-switch button.is-active { background: var(--app-surface); color: var(--app-text); font-weight: 700; }
  .review-grid { display: block; height: auto; min-height: 0; }
  .candidate-queue, .candidate-editor, .evidence-panel { border: 0; }
  .candidate-queue { max-height: min(52vh, 420px); }
  .mobile-hidden { display: none; }
  .review-actions { position: sticky; bottom: 0; z-index: 2; padding: 12px; border: 1px solid var(--app-border); border-radius: var(--app-radius-md); background: var(--app-surface); box-shadow: var(--app-shadow-sm); }
  .form-row { grid-template-columns: 1fr; gap: 0; }
}
</style>
