<template>
  <div class="knowledge-page">
    <header class="knowledge-header">
      <div class="brand-cluster">
        <el-button :icon="ArrowLeft" class="icon-button" circle @click="router.push('/')" />
        <div class="header-copy">
          <p class="eyebrow">Knowledge Bank</p>
          <h1 class="page-title">{{ isPublicMaintenanceMode ? '公共题库维护' : '岗位 / 题库维护' }}</h1>
          <p class="page-subtitle">{{ workspaceDescription }}</p>
        </div>
      </div>
      <div class="header-actions">
        <el-button
          data-testid="workspace-refresh"
          :icon="RefreshRight"
          :loading="loading"
          :disabled="candidateReviewDirty"
          :title="candidateReviewDirty ? '请先保存或放弃最终审核中的修改' : ''"
          @click="loadWorkspace"
        >刷新</el-button>
        <el-button v-if="canCreatePosition" type="primary" :icon="Plus" @click="createDialogVisible = true">新建岗位</el-button>
      </div>
    </header>

    <main class="knowledge-body" :class="{ 'is-focused': sidebarCollapsed }">
      <KnowledgeWorkspaceSidebar
        :positions="positions"
        :active-id="activePositionId"
        :loading="loading"
        :can-create="canCreatePosition"
        :collapsed="sidebarCollapsed"
        :empty-description="isPublicMaintenanceMode ? '暂无公共岗位' : '暂无可用岗位'"
        @select="selectPosition"
        @create="createDialogVisible = true"
      />

      <section class="workspace-panel surface-card">
        <template v-if="activePosition">
          <div class="workspace-context">
            <div class="context-copy">
              <p class="section-kicker">{{ activePosition.scope === 'PUBLIC' ? 'Public Starter' : 'Private Workspace' }}</p>
              <h2>{{ activePosition.name }}</h2>
              <p>{{ activePosition.description || '默认知识库用于后续生成知识原子和面试 RAG。' }}</p>
            </div>
            <div class="context-actions">
              <el-button v-if="sidebarCollapsed" size="small" @click="sidebarCollapsed = false">展开岗位栏</el-button>
              <el-button v-if="isPositionEditable(activePosition)" type="danger" plain :loading="deleting" @click="deletePosition">删除岗位</el-button>
              <el-tag v-else :type="canMaintainPackage ? 'success' : 'info'" effect="plain">{{ canMaintainPackage ? '题库可维护' : '只读' }}</el-tag>
            </div>
          </div>

          <KnowledgeWorkspaceTabs :model-value="activeTab" :can-build="canBuildPackage" :candidate-count="candidateBadgeCount" @update:model-value="changeActiveTab" />

          <QuestionBankOverviewPanel
            v-if="activeTab === 'overview'"
            :position="activePosition"
            :is-public="activePosition.scope === 'PUBLIC'"
            :can-maintain="canMaintainPackage"
            :atom-total="atomPage.total"
            :published-count="publishedAtomCount"
            :coverage-details="coverageDetails"
            :coverage-loading="coverageLoading"
          />

          <QuestionBankBuildPanel
            v-if="activeTab === 'build'"
            :position="activePosition"
            :llm-status="llmStatus"
            :can-build="canBuildPackage"
            :builds="buildState.builds.value"
            :active-build="buildState.activeBuild.value"
            :loading="buildState.loading.value"
            :action-loading="buildState.actionLoading.value"
            :category-options="categoryOptions"
            @configure-llm="goLlmSettings"
            @refresh="refreshBuilds"
            @start="startBuild"
            @select-build="selectBuild"
            @retry="retryBuild"
            @delete="deleteBuild"
            @import-package="receiveJsonPackage"
          />

          <!-- 旧 JSON 导入入口暂时隐藏；保留组件调用，便于后续按需恢复。
          <QuestionBankJsonImportCard
            v-if="activeTab === 'atoms' && canImportPackage"
            :can-import="canImportPackage"
            @import-package="receiveJsonPackage"
          />
          -->

          <div v-if="['build', 'atoms'].includes(activeTab) && jsonPackage" class="json-preview-card">
            <div><strong>{{ jsonFileName }}</strong><span>已读取，需校验后才能导入。</span></div>
            <div><el-button :loading="jsonLoading === 'validate'" @click="validateJsonPackage">校验导入包</el-button><el-button type="primary" :disabled="!jsonPreview || jsonPreview.errors?.length" :loading="jsonLoading === 'import'" @click="importJsonPackage">导入为草稿</el-button></div>
          </div>

          <QuestionBankCandidateReviewPanel
            v-if="activeTab === 'candidates'"
            :candidates="buildState.candidates.value"
            :loading="buildState.candidatesLoading.value"
            :action-loading="buildState.actionLoading.value"
            :can-review="buildState.canReview.value"
            :can-finalize="buildState.canFinalize.value"
            :exception-count="buildState.exceptionCandidates.value.length"
            :finalizable-count="buildState.finalizableCandidates.value.length"
            @refresh="loadCandidates"
            @update="updateCandidate"
            @repair="repairCandidates"
            @finalize="finalizeBuild"
            @dirty-change="candidateReviewDirty = $event"
          />

          <QuestionBankAtomPanel
            v-if="activeTab === 'atoms'"
            :atoms="atoms"
            :total="atomPage.total"
            :loading="atomsLoading"
            :filters="atomFilters"
            :page="atomPage"
            :selected-ids="selectedAtomIds"
            :can-edit="canMaintainPackage"
            :can-publish="canPublishPackageAtoms"
            :can-reindex="canReindexPackageAtoms"
            :can-archive="canArchivePackageAtoms"
            :action-loading="atomActionLoading"
            @refresh="loadAtoms"
            @search="searchAtoms"
            @update-filter="updateAtomFilter"
            @selection-change="selectedAtomIds = $event"
            @edit="openAtomEditor"
            @publish="publishSelectedAtoms"
            @publish-all="publishAllDraftAtoms"
            @reindex="reindexSelectedAtoms"
            @archive="archiveSelectedAtoms"
            @archive-all="archiveAllAtoms"
            @page-change="loadAtoms"
          />

          <QuestionBankAtomEditDialog
            v-model="atomEditVisible"
            :atom="editingAtom"
            :saving="atomEditSaving"
            @save="saveAtomEdit"
          />
        </template>
        <el-empty v-else description="请选择一个岗位" />
      </section>
    </main>

    <el-dialog v-if="canCreatePosition" v-model="createDialogVisible" title="新建私有岗位" width="min(92vw, 520px)" :close-on-click-modal="false">
      <el-form label-position="top"><el-form-item label="岗位名称"><el-input v-model="createForm.name" maxlength="80" placeholder="例如：Java 中高级后端" /></el-form-item><el-form-item label="说明"><el-input v-model="createForm.description" type="textarea" :rows="3" maxlength="300" show-word-limit placeholder="可选，用于区分岗位方向或学习目标" /></el-form-item></el-form>
      <template #footer><el-button @click="createDialogVisible = false">取消</el-button><el-button type="primary" :loading="creating" @click="createPosition">创建</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Plus, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getLlmConfigStatusAPI } from '@/api/llm'
import {
  archiveAllAtomsAPI,
  archiveKnowledgeBaseAtomsAPI,
  createPrivatePositionAPI,
  deletePrivatePositionAPI,
  getKnowledgeWorkspaceAPI,
  getKnowledgeAtomAPI,
  getPositionCoverageAPI,
  getPublishedKnowledgeBaseAtomCountAPI,
  importKnowledgeBasePackageAPI,
  publishAllDraftAtomsAPI,
  publishKnowledgeBaseAtomsAPI,
  reindexKnowledgeBaseAtomsAPI,
  searchKnowledgeBaseAtomsAPI,
  updateKnowledgeAtomAPI,
  validateKnowledgeBaseImportAPI
} from '@/api/knowledgeWorkspace'
import { useQuestionBankBuild } from '@/composables/useQuestionBankBuild'
import {
  canArchiveQuestionBankAtoms,
  canBuildQuestionBank,
  canCreatePrivatePosition,
  canMaintainQuestionBank,
  canPublishQuestionBankAtoms,
  canReindexQuestionBankAtoms,
  isQuestionBankBuildInProgress,
  isPublicOnlyMaintenanceMode,
  isPositionEditable,
  getQuestionBankCategoryOptions,
  KNOWLEDGE_WORKSPACE_CAPABILITIES_KEY,
  normalizeKnowledgeWorkspaceCapabilities
} from '@/utils/knowledgeWorkspace'
import { normalizeLlmConfigStatus } from '@/utils/llmConfig'
import { normalizeQuestionBankAtom, normalizeQuestionBankAtomPage } from '@/api/knowledgeWorkspace'
import KnowledgeWorkspaceSidebar from '@/components/knowledge/KnowledgeWorkspaceSidebar.vue'
import KnowledgeWorkspaceTabs from '@/components/knowledge/KnowledgeWorkspaceTabs.vue'
import QuestionBankOverviewPanel from '@/components/knowledge/QuestionBankOverviewPanel.vue'
import QuestionBankBuildPanel from '@/components/knowledge/QuestionBankBuildPanel.vue'
import QuestionBankCandidateReviewPanel from '@/components/knowledge/QuestionBankCandidateReviewPanel.vue'
import QuestionBankAtomPanel from '@/components/knowledge/QuestionBankAtomPanel.vue'
import QuestionBankAtomEditDialog from '@/components/knowledge/QuestionBankAtomEditDialog.vue'
// 旧 JSON 导入入口暂时隐藏；保留 import 位置，恢复模板入口时一并启用。
// import QuestionBankJsonImportCard from '@/components/knowledge/QuestionBankJsonImportCard.vue'

const router = useRouter()
const workspaceCapabilities = inject(KNOWLEDGE_WORKSPACE_CAPABILITIES_KEY, ref(normalizeKnowledgeWorkspaceCapabilities()))
const isPublicMaintenanceMode = computed(() => isPublicOnlyMaintenanceMode(workspaceCapabilities.value))
const canCreatePosition = computed(() => canCreatePrivatePosition(workspaceCapabilities.value))
const workspaceDescription = computed(() => isPublicMaintenanceMode.value ? '维护平台内置公共题库，普通用户仅使用已发布内容。' : workspaceCapabilities.value.admin ? '维护公共 starter 题库，也可创建当前管理员自己的私有岗位与题库。' : '管理当前账号的私有岗位知识库，公共岗位仅作为只读 starter 内容。')

const positions = ref([])
const activePositionId = ref(null)
const activeTab = ref('overview')
const candidateReviewDirty = ref(false)
const sidebarCollapsed = ref(false)
const loading = ref(false)
const creating = ref(false)
const deleting = ref(false)
const createDialogVisible = ref(false)
const createForm = reactive({ name: '', description: '' })
const llmStatus = ref({ resolved: false, hasActiveConfig: false, activeProvider: '', activeModelName: '', activeDisplayName: '' })
const atoms = ref([])
const atomsLoading = ref(false)
const selectedAtomIds = ref([])
const atomActionLoading = ref('')
const atomEditVisible = ref(false)
const atomEditSaving = ref(false)
const editingAtom = ref(null)
const atomFilters = reactive({ keyword: '', category: '', status: '' })
const atomPage = reactive({ page: 1, size: 10, total: 0 })
const coverageDetails = ref([])
const coverageLoading = ref(false)
const jsonPackage = ref(null)
const jsonFileName = ref('')
const jsonPreview = ref(null)
const jsonLoading = ref('')
let workspaceRequest = 0
let atomEditorRequest = 0
let atomSaveRequest = 0
let jsonRequest = 0
const categoryOptions = computed(() => getQuestionBankCategoryOptions(coverageDetails.value, atoms.value))

const activePosition = computed(() => positions.value.find((position) => position.id === activePositionId.value) || positions.value[0] || null)
const activeKnowledgeBaseId = computed(() => activePosition.value?.knowledgeBase?.id || null)
const canBuildPackage = computed(() => canBuildQuestionBank(activePosition.value))
const activeBuildKnowledgeBaseId = computed(() => canBuildPackage.value ? activeKnowledgeBaseId.value : null)
const canMaintainPackage = computed(() => canMaintainQuestionBank(activePosition.value))
const canImportPackage = computed(() => Boolean(activePosition.value?.canImportPackage) && canMaintainPackage.value)
const canPublishPackageAtoms = computed(() => canPublishQuestionBankAtoms(activePosition.value))
const canReindexPackageAtoms = computed(() => canReindexQuestionBankAtoms(activePosition.value))
const canArchivePackageAtoms = computed(() => canArchiveQuestionBankAtoms(activePosition.value))
const publishedAtomCount = ref(0)
const candidateBadgeCount = computed(() => {
  const build = buildState.activeBuild.value
  return buildState.candidates.value.length
    ? buildState.exceptionCandidates.value.length
    : Math.max(0, Number(build?.needsHumanCount || 0))
})
const buildState = useQuestionBankBuild(activeBuildKnowledgeBaseId)

const loadLlmStatus = async () => {
  try {
    llmStatus.value = normalizeLlmConfigStatus(await getLlmConfigStatusAPI({ silent: true }))
  } catch {
    llmStatus.value = { resolved: true, hasActiveConfig: false, activeProvider: '', activeModelName: '', activeDisplayName: '' }
  }
}

const loadCoverage = async () => {
  if (!activePosition.value || !canMaintainPackage.value) { coverageDetails.value = []; coverageLoading.value = false; return }
  const positionId = activePosition.value.id
  coverageDetails.value = []
  coverageLoading.value = true
  try {
    const details = (await getPositionCoverageAPI(positionId))?.details || []
    if (activePosition.value?.id === positionId) coverageDetails.value = details
  } catch {
    if (activePosition.value?.id === positionId) coverageDetails.value = []
  } finally {
    if (activePosition.value?.id === positionId) coverageLoading.value = false
  }
}

const loadActivePositionData = () => Promise.allSettled([
  buildState.loadBuilds(),
  loadAtoms(),
  loadCoverage()
])

const loadWorkspace = async () => {
  const requestId = ++workspaceRequest
  loading.value = true
  try {
    const previousPositionId = activePositionId.value
    const data = await getKnowledgeWorkspaceAPI()
    if (requestId !== workspaceRequest) return
    const available = data?.positions || []
    positions.value = isPublicMaintenanceMode.value ? available.filter((position) => position.scope === 'PUBLIC') : available
    if (!positions.value.some((position) => position.id === activePositionId.value)) activePositionId.value = positions.value[0]?.id || null
    if (activePositionId.value === previousPositionId) await loadActivePositionData()
  } finally { if (requestId === workspaceRequest) loading.value = false }
}

const confirmDiscardCandidateEdits = async () => {
  if (!candidateReviewDirty.value) return true
  try {
    await ElMessageBox.confirm(
      '最终审核中还有未保存修改。放弃这些修改后再切换？',
      '放弃修改？',
      { type: 'warning', confirmButtonText: '放弃修改并切换', cancelButtonText: '继续编辑' }
    )
    return true
  } catch {
    return false
  }
}

const changeActiveTab = async (tab) => {
  if (tab === activeTab.value) return
  if (activeTab.value === 'candidates' && !await confirmDiscardCandidateEdits()) return
  candidateReviewDirty.value = false
  activeTab.value = tab
}

const selectPosition = async (position) => {
  if (activeTab.value === 'candidates' && !await confirmDiscardCandidateEdits()) return
  candidateReviewDirty.value = false
  atomEditorRequest += 1
  atomSaveRequest += 1
  jsonRequest += 1
  activePositionId.value = position.id
  activeTab.value = 'overview'
  sidebarCollapsed.value = false
  jsonPackage.value = null
  jsonPreview.value = null
  jsonLoading.value = ''
  atomEditVisible.value = false
  editingAtom.value = null
  atomEditSaving.value = false
}
const goLlmSettings = () => router.push({ path: '/llm-providers', query: { reason: 'missing-config', source: 'question-bank-build' } })

const loadAtoms = async () => {
  if (!activeKnowledgeBaseId.value || !canMaintainPackage.value) { atoms.value = []; atomPage.total = 0; publishedAtomCount.value = 0; atomsLoading.value = false; selectedAtomIds.value = []; return }
  const knowledgeBaseId = activeKnowledgeBaseId.value
  atoms.value = []
  atomPage.total = 0
  publishedAtomCount.value = 0
  selectedAtomIds.value = []
  atomsLoading.value = true
  try {
    const [response, publishedCount] = await Promise.all([
      searchKnowledgeBaseAtomsAPI(knowledgeBaseId, { ...cleanFilters(), page: atomPage.page, size: atomPage.size }),
      getPublishedKnowledgeBaseAtomCountAPI(knowledgeBaseId)
    ])
    if (activeKnowledgeBaseId.value !== knowledgeBaseId) return
    atoms.value = normalizeQuestionBankAtomPage(response).items.map(normalizeQuestionBankAtom); atomPage.total = response.total; atomPage.page = response.page; atomPage.size = response.size; publishedAtomCount.value = publishedCount
  } finally {
    if (activeKnowledgeBaseId.value === knowledgeBaseId) atomsLoading.value = false
  }
}
const cleanFilters = () => Object.fromEntries(Object.entries(atomFilters).map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value]).filter(([, value]) => value))
const updateAtomFilter = (key, value) => { atomFilters[key] = value }
const searchAtoms = () => { atomPage.page = 1; loadAtoms() }

const openAtomEditor = async (atomId) => {
  if (!atomId || !canMaintainPackage.value) return
  const positionId = activePositionId.value
  const knowledgeBaseId = activeKnowledgeBaseId.value
  const requestId = ++atomEditorRequest
  const atom = await getKnowledgeAtomAPI(atomId)
  if (requestId !== atomEditorRequest
      || activePositionId.value !== positionId
      || activeKnowledgeBaseId.value !== knowledgeBaseId) return
  editingAtom.value = atom
  atomEditVisible.value = true
}

const saveAtomEdit = async (patch) => {
  if (!editingAtom.value?.id) return
  const atomId = editingAtom.value.id
  const positionId = activePositionId.value
  const knowledgeBaseId = activeKnowledgeBaseId.value
  const requestId = ++atomSaveRequest
  atomEditSaving.value = true
  try {
    await updateKnowledgeAtomAPI(atomId, patch)
    if (requestId !== atomSaveRequest
        || activePositionId.value !== positionId
        || activeKnowledgeBaseId.value !== knowledgeBaseId
        || editingAtom.value?.id !== atomId) return
    atomEditVisible.value = false
    editingAtom.value = null
    await loadAtoms()
    ElMessage.success('知识原子已保存为审核通过的草稿')
  } finally { if (requestId === atomSaveRequest) atomEditSaving.value = false }
}

const runAtomAction = async (action, callback) => { atomActionLoading.value = action; try { await callback(); await Promise.all([loadAtoms(), loadCoverage()]) } finally { atomActionLoading.value = '' } }
const publishSelectedAtoms = (ids) => runAtomAction('publish', async () => { const result = await publishKnowledgeBaseAtomsAPI(activeKnowledgeBaseId.value, ids); ElMessage.success(`已发布 ${result?.published || 0} 条`) })
const reindexSelectedAtoms = (ids) => runAtomAction('reindex', async () => { const result = await reindexKnowledgeBaseAtomsAPI(activeKnowledgeBaseId.value, ids); ElMessage.success(`重建索引完成：成功 ${result?.synced || 0} 条`) })
const archiveSelectedAtoms = async (ids) => { try { await ElMessageBox.confirm(`确认归档所选 ${ids.length} 条原子？`, '归档原子', { type: 'warning' }) } catch { return }; await runAtomAction('archive', async () => { await archiveKnowledgeBaseAtomsAPI(activeKnowledgeBaseId.value, ids); ElMessage.success('已归档所选原子') }) }
const archiveAllAtoms = async () => { try { await ElMessageBox.confirm('确认归档当前知识库内所有原子？', '一键归档全部', { type: 'warning' }) } catch { return }; await runAtomAction('archiveAll', async () => { await archiveAllAtomsAPI(activeKnowledgeBaseId.value); ElMessage.success('已归档全部原子') }) }
const publishAllDraftAtoms = async () => { try { await ElMessageBox.confirm('仅发布通过审核的草稿原子，确认继续？', '一键发布全部草稿', { type: 'warning' }) } catch { return }; await runAtomAction('publishAll', async () => { await publishAllDraftAtomsAPI(activeKnowledgeBaseId.value); ElMessage.success('已提交草稿发布') }) }

const refreshBuilds = () => buildState.loadBuilds()
const startBuild = async (files, categories) => { await buildState.startBuild(files, categories); activeTab.value = 'build'; ElMessage.success('构建任务已提交，请在当前页查看进度') }
const selectBuild = async (buildId) => { await buildState.loadBuild(buildId); if (activeTab.value === 'candidates') await buildState.loadCandidates(buildId) }
const retryBuild = async () => { await buildState.retryBuild(); ElMessage.success('已重试原任务') }
const deleteBuild = async () => {
  if (!buildState.activeBuildId.value) return
  if (isQuestionBankBuildInProgress(buildState.activeBuild.value)) {
    ElMessage.warning('构建运行中，暂不能删除；请等待完成或失败后再操作')
    return
  }
  try { await ElMessageBox.confirm('确认删除当前构建批次及其保留源文件？此操作不可恢复。', '删除构建批次', { type: 'warning' }) } catch { return }
  await buildState.deleteBuild()
  ElMessage.success('构建批次已删除')
}
const loadCandidates = () => buildState.loadCandidates()
const updateCandidate = async (candidateId, action, fields) => { await buildState.updateCandidate(candidateId, action, fields); ElMessage.success(action === 'ACCEPT' ? '已标记为可发布' : action === 'REJECT' ? '已标记为不发布' : '人工修改已保存') }
const repairCandidates = async (candidateIds, instruction) => {
  await buildState.repairCandidates(candidateIds, instruction)
  activeTab.value = 'build'
  ElMessage.success('修复助手已开始处理，完成后会自动复查')
}
const finalizeBuild = async () => {
  const count = buildState.finalizableCandidates.value.length
  const retainedCount = buildState.exceptionCandidates.value.length
  try {
    await ElMessageBox.confirm(
      `系统已自动选择本批次全部 ${count} 条可发布知识原子，将一次性写入数据库并同步检索索引。${retainedCount ? `另有 ${retainedCount} 条需关注项将保留在本批次，不会发布。` : ''}`,
      '一键批量发布',
      { type: 'warning', confirmButtonText: '确认发布全部' }
    )
  } catch {
    return
  }
  await buildState.finalizeBuild()
  activeTab.value = 'build'
  ElMessage.success(`已提交 ${count} 条知识原子的批量发布任务`)
}

const receiveJsonPackage = (payload, fileName) => {
  jsonRequest += 1
  jsonPackage.value = payload
  jsonFileName.value = fileName
  jsonPreview.value = null
  jsonLoading.value = ''
}
const validateJsonPackage = async () => {
  const knowledgeBaseId = activeKnowledgeBaseId.value
  const payload = jsonPackage.value
  if (!knowledgeBaseId || !payload) return
  const requestId = ++jsonRequest
  jsonLoading.value = 'validate'
  try {
    const preview = await validateKnowledgeBaseImportAPI(knowledgeBaseId, payload)
    if (requestId !== jsonRequest || activeKnowledgeBaseId.value !== knowledgeBaseId || jsonPackage.value !== payload) return
    jsonPreview.value = preview
    ElMessage.success('导入包校验完成')
  } finally {
    if (requestId === jsonRequest) jsonLoading.value = ''
  }
}
const importJsonPackage = async () => {
  const knowledgeBaseId = activeKnowledgeBaseId.value
  const payload = jsonPackage.value
  if (!knowledgeBaseId || !payload || !jsonPreview.value || jsonPreview.value.errors?.length) return
  const requestId = ++jsonRequest
  jsonLoading.value = 'import'
  try {
    await importKnowledgeBasePackageAPI(knowledgeBaseId, payload)
    if (requestId !== jsonRequest || activeKnowledgeBaseId.value !== knowledgeBaseId || jsonPackage.value !== payload) return
    jsonPackage.value = null
    jsonPreview.value = null
    await loadAtoms()
    if (requestId === jsonRequest && activeKnowledgeBaseId.value === knowledgeBaseId) ElMessage.success('已导入为草稿')
  } finally {
    if (requestId === jsonRequest) jsonLoading.value = ''
  }
}

const createPosition = async () => { const name = createForm.name.trim(); if (!name) return ElMessage.warning('请填写岗位名称'); creating.value = true; try { const created = await createPrivatePositionAPI({ name, description: createForm.description.trim() }); createForm.name = ''; createForm.description = ''; createDialogVisible.value = false; await loadWorkspace(); activePositionId.value = created.id; ElMessage.success('私有岗位已创建') } finally { creating.value = false } }
const deletePosition = async () => { if (!activePosition.value) return; try { await ElMessageBox.confirm(`确认删除「${activePosition.value.name}」？该操作不可恢复。`, '删除岗位', { type: 'warning' }) } catch { return }; deleting.value = true; try { await deletePrivatePositionAPI(activePosition.value.id); await loadWorkspace(); ElMessage.success('岗位已删除') } finally { deleting.value = false } }

watch(activePositionId, async () => { await loadActivePositionData(); await buildState.startPolling(); if (!canBuildPackage.value && ['build', 'candidates'].includes(activeTab.value)) activeTab.value = 'overview' })
watch(activeTab, async (tab) => { sidebarCollapsed.value = tab === 'candidates'; if (tab === 'candidates' && buildState.activeBuildId.value) await buildState.loadCandidates(); if (tab === 'atoms') await loadAtoms(); if (tab === 'overview') await loadCoverage() })
watch(() => buildState.activeBuild.value?.stage, async (stage, previous) => {
  const publishedStages = ['PUBLISHED', 'PUBLISHED_WITH_INDEX_ERRORS']
  if (publishedStages.includes(stage) && stage !== previous) await Promise.all([loadAtoms(), loadCoverage()])
})
onMounted(async () => { await loadLlmStatus(); await loadWorkspace(); await buildState.startPolling() })
onUnmounted(() => buildState.stopPolling())
</script>

<style scoped>
.knowledge-page { display: grid; gap: 20px; }
.knowledge-header, .brand-cluster, .header-actions, .workspace-context, .context-actions { display: flex; align-items: center; gap: 12px; }
.knowledge-header, .workspace-context { justify-content: space-between; }
.header-copy, .context-copy { min-width: 0; }
.eyebrow, .section-kicker { margin: 0 0 4px; color: var(--app-text-muted); font-size: 0.78rem; font-weight: 700; text-transform: uppercase; }
.page-title, .workspace-context h2 { margin: 0; color: var(--app-text); line-height: 1.2; }
.page-title { font-size: 1.55rem; }
.page-subtitle, .context-copy p { margin: 6px 0 0; color: var(--app-text-muted); font-size: 0.92rem; }
.knowledge-body { display: grid; grid-template-columns: minmax(260px, 340px) minmax(0, 1fr); gap: 18px; align-items: start; }
.knowledge-body.is-focused { grid-template-columns: minmax(0, 1fr); }
.knowledge-body.is-focused > :first-child { display: none; }
.surface-card { border: 1px solid var(--app-border); border-radius: var(--app-radius-lg); background: var(--app-surface); box-shadow: var(--app-shadow-sm); }
.workspace-panel { min-width: 0; padding: 20px; }
.context-copy p { max-width: 780px; }
.json-preview-card { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 16px; padding: 12px; border: 1px solid var(--app-border); border-radius: var(--app-radius-md); background: var(--app-surface-2); }
.json-preview-card > div:first-child { display: grid; gap: 4px; min-width: 0; }
.json-preview-card span { color: var(--app-text-muted); font-size: 0.86rem; }
@media (max-width: 1180px) { .knowledge-body { grid-template-columns: 1fr; } .knowledge-body.is-focused { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .knowledge-header, .workspace-context, .context-actions, .json-preview-card { align-items: stretch; flex-direction: column; } .header-actions, .context-actions, .json-preview-card > div:last-child { width: 100%; } .header-actions .el-button, .context-actions .el-button, .json-preview-card .el-button { flex: 1; } .context-actions { flex-direction: row; } }
</style>
