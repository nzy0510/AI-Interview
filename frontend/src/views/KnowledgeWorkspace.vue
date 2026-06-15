<template>
  <div class="knowledge-page">
    <header class="knowledge-header">
      <div class="brand-cluster">
        <el-button :icon="ArrowLeft" class="icon-button" circle @click="router.push('/')" />
        <div class="header-copy">
          <p class="eyebrow">Knowledge Bank</p>
          <h1 class="page-title">知识库 / 题库</h1>
          <p class="page-subtitle">管理当前账号的私有岗位知识库，公共岗位仅作为只读 starter 内容。</p>
        </div>
      </div>
      <div class="header-actions">
        <el-button :icon="RefreshRight" :loading="loading" @click="loadWorkspace">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="createDialogVisible = true">新建岗位</el-button>
      </div>
    </header>

    <el-main class="page-body knowledge-body">
      <section class="surface-card section-shell position-panel">
        <div class="section-head compact">
          <div>
            <p class="section-kicker">Positions</p>
            <h2 class="section-title">岗位空间</h2>
          </div>
          <el-tag effect="plain">{{ positions.length }} 个岗位</el-tag>
        </div>

        <el-empty v-if="!loading && !positions.length" description="暂无可用岗位">
          <el-button type="primary" @click="createDialogVisible = true">创建私有岗位</el-button>
        </el-empty>

        <div v-else class="position-list">
          <button
            v-for="position in positions"
            :key="position.id"
            type="button"
            class="position-item"
            :class="{ 'is-active': activePosition?.id === position.id, 'is-archived': position.status === 'ARCHIVED' }"
            @click="selectPosition(position)"
          >
            <span class="position-item__title">{{ position.name }}</span>
            <span class="position-item__meta">
              <el-tag size="small" :type="position.scope === 'PUBLIC' ? 'info' : 'success'" effect="plain">
                {{ getPositionScopeLabel(position) }}
              </el-tag>
              <el-tag size="small" :type="getPositionStatusType(position.status)" effect="plain">
                {{ position.status === 'ARCHIVED' ? '已归档' : '可用' }}
              </el-tag>
            </span>
          </button>
        </div>
      </section>

      <section class="surface-card section-shell workspace-panel">
        <template v-if="activePosition">
          <div class="section-head">
            <div>
              <p class="section-kicker">{{ activePosition.scope === 'PUBLIC' ? 'Public Starter' : 'Private Workspace' }}</p>
              <h2 class="section-title">{{ activePosition.name }}</h2>
              <p class="section-desc">{{ activePosition.description || '默认知识库用于后续生成知识原子和面试 RAG。' }}</p>
            </div>
            <div class="section-actions">
              <el-button
                v-if="isPositionEditable(activePosition)"
                type="danger"
                plain
                :icon="Delete"
                :loading="deleting"
                @click="deletePosition"
              >
                删除岗位
              </el-button>
              <el-tag v-else type="info" effect="plain">只读</el-tag>
            </div>
          </div>

          <div class="workspace-summary">
            <div class="summary-item">
              <span>作用域</span>
              <strong>{{ getPositionScopeLabel(activePosition) }}</strong>
            </div>
            <div class="summary-item">
              <span>默认知识库</span>
              <strong>{{ activePosition.knowledgeBase?.name || '未创建' }}</strong>
            </div>
            <div class="summary-item">
              <span>导入原子</span>
              <strong>{{ packageAtomPage.total }}</strong>
            </div>
            <div class="summary-item">
              <span>维护权限</span>
              <strong>{{ canMaintainPackage ? '可维护' : '只读' }}</strong>
            </div>
          </div>

          <div v-if="canMaintainPackage" class="package-panel">
            <div class="file-toolbar package-toolbar">
              <div>
                <h3>导入包维护</h3>
                <p>使用本地 skill 生成的 JSON 导入包维护当前岗位题库，导入后先进入草稿。</p>
              </div>
              <div class="atom-toolbar__actions">
                <input
                  ref="packageFileInput"
                  class="visually-hidden"
                  type="file"
                  accept=".json,application/json"
                  @change="handleImportPackageFile"
                >
                <el-button :disabled="!canMaintainPackage" @click="packageFileInput?.click()">选择导入包</el-button>
                <el-button :loading="importLoading" :disabled="!canMaintainPackage || !importPackage" @click="validateImportPackage">
                  校验导入包
                </el-button>
                <el-button
                  type="primary"
                  :loading="importingPackage"
                  :disabled="!canMaintainPackage || !importPackage"
                  @click="importPackageDraft"
                >
                  导入为草稿
                </el-button>
              </div>
            </div>

            <p v-if="importFileName" class="package-file-name">已选择：{{ importFileName }}</p>

            <div v-if="importPreview || importResult" class="package-result">
              <div v-if="importPreview" class="package-result__summary">
                <span>批次 {{ importPreview.batchId || '-' }}</span>
                <span>接收 {{ importPreview.received || 0 }}</span>
                <span>新增 {{ importPreview.newCount || 0 }}</span>
                <span>更新 {{ importPreview.updateCount || 0 }}</span>
                <span v-if="importPreview.batchIdExists">批次已存在</span>
              </div>
              <div v-if="importResult" class="package-result__summary">
                <span>导入 {{ importResult.imported || 0 }}</span>
                <span>发布 {{ importResult.published || 0 }}</span>
                <span>失败 {{ importResult.failed || 0 }}</span>
              </div>
              <ul v-if="packageErrors.length" class="package-errors">
                <li v-for="error in packageErrors" :key="error">{{ error }}</li>
              </ul>
            </div>

            <div class="package-filters">
              <el-input v-model="packageAtomFilters.keyword" clearable placeholder="关键词 / atomId / 内容" />
              <el-input v-model="packageAtomFilters.category" clearable placeholder="分类" />
              <el-input v-model="packageAtomFilters.batchId" clearable placeholder="批次 ID" />
              <el-select v-model="packageAtomFilters.difficulty" clearable placeholder="难度">
                <el-option label="junior" value="junior" />
                <el-option label="mid" value="mid" />
                <el-option label="senior" value="senior" />
                <el-option label="principal" value="principal" />
              </el-select>
              <el-select v-model="packageAtomFilters.status" clearable placeholder="状态">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="已发布" value="PUBLISHED" />
                <el-option label="归档" value="ARCHIVED" />
              </el-select>
              <el-button :icon="RefreshRight" :loading="packageAtomsLoading" :disabled="!canMaintainPackage" @click="loadPackageAtoms">
                查询
              </el-button>
            </div>

            <div class="package-bulk-actions">
              <span class="muted-text">已选 {{ selectedPackageAtomIds.length }} 条</span>
              <el-button
                type="success"
                :loading="packageAtomActionLoading === 'publishAllDrafts'"
                :disabled="!canPublishPackageAtoms"
                @click="publishAllDraftAtoms"
              >
                一键发布全部草稿
              </el-button>
              <el-button
                type="success"
                :loading="packageAtomActionLoading === 'publish'"
                :disabled="!canPublishPackageAtoms || !selectedPackageAtomIds.length"
                @click="publishSelectedPackageAtoms"
              >
                发布所选
              </el-button>
              <el-button
                :loading="packageAtomActionLoading === 'reindex'"
                :disabled="!canReindexPackageAtoms || !selectedPackageAtomIds.length"
                @click="reindexSelectedPackageAtoms"
              >
                重建索引
              </el-button>
              <el-button
                type="danger"
                plain
                :loading="packageAtomActionLoading === 'archive'"
                :disabled="!canArchivePackageAtoms || !selectedPackageAtomIds.length"
                @click="archiveSelectedPackageAtoms"
              >
                归档所选
              </el-button>
            </div>

            <el-table
              :data="packageAtoms"
              class="atom-table"
              empty-text="当前知识库还没有导入包原子"
              @selection-change="handlePackageAtomSelectionChange"
            >
              <el-table-column type="selection" width="44" />
              <el-table-column prop="atomId" label="Atom ID" min-width="180" />
              <el-table-column prop="subject" label="考点" min-width="180" />
              <el-table-column prop="category" label="分类" min-width="120" />
              <el-table-column prop="difficulty" label="难度" width="90" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'ARCHIVED' ? 'info' : 'success'" effect="plain">
                    {{ row.status || '-' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="vectorStatus" label="向量" width="100" />
              <el-table-column prop="sourceRef" label="来源" min-width="160" />
            </el-table>

            <el-pagination
              v-if="packageAtomPage.total > packageAtomPage.size"
              v-model:current-page="packageAtomPage.page"
              v-model:page-size="packageAtomPage.size"
              class="package-pagination"
              layout="total, sizes, prev, pager, next"
              :page-sizes="[10, 20, 50]"
              :total="packageAtomPage.total"
              @current-change="loadPackageAtoms"
              @size-change="loadPackageAtoms"
            />
          </div>
        </template>

        <el-empty v-else description="请选择一个岗位" />
      </section>
    </el-main>

    <el-dialog
      v-model="createDialogVisible"
      title="新建私有岗位"
      width="min(92vw, 520px)"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="岗位名称">
          <el-input v-model="createForm.name" maxlength="80" placeholder="例如：Java 中高级后端" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            maxlength="300"
            show-word-limit
            placeholder="可选，用于区分岗位方向或学习目标"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createPosition">创建</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Delete, Plus, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  archiveKnowledgeBaseAtomsAPI,
  deletePrivatePositionAPI,
  createPrivatePositionAPI,
  getKnowledgeWorkspaceAPI,
  importKnowledgeBasePackageAPI,
  publishAllDraftAtomsAPI,
  publishKnowledgeBaseAtomsAPI,
  reindexKnowledgeBaseAtomsAPI,
  searchKnowledgeBaseAtomsAPI,
  validateKnowledgeBaseImportAPI
} from '@/api/knowledgeWorkspace'
import {
  canArchiveQuestionBankAtoms,
  canMaintainQuestionBank,
  canPublishQuestionBankAtoms,
  canReindexQuestionBankAtoms,
  getPositionScopeLabel,
  getPositionStatusType,
  isPositionEditable,
  parseImportPackageText
} from '@/utils/knowledgeWorkspace'

const router = useRouter()
const loading = ref(false)
const creating = ref(false)
const deleting = ref(false)
const positions = ref([])
const activePositionId = ref(null)
const createDialogVisible = ref(false)
const packageFileInput = ref(null)
const importPackage = ref(null)
const importFileName = ref('')
const importPreview = ref(null)
const importResult = ref(null)
const importLoading = ref(false)
const importingPackage = ref(false)
const packageAtomsLoading = ref(false)
const packageAtomActionLoading = ref('')
const packageAtoms = ref([])
const selectedPackageAtomIds = ref([])
const createForm = reactive({ name: '', description: '' })
const packageAtomFilters = reactive({
  keyword: '',
  category: '',
  batchId: '',
  difficulty: '',
  status: ''
})
const packageAtomPage = reactive({
  page: 1,
  size: 20,
  total: 0
})

const activePosition = computed(() => positions.value.find((item) => item.id === activePositionId.value) || positions.value[0] || null)
const activeKnowledgeBaseId = computed(() => activePosition.value?.knowledgeBase?.id || null)
const canMaintainPackage = computed(() => canMaintainQuestionBank(activePosition.value))
const canPublishPackageAtoms = computed(() => canPublishQuestionBankAtoms(activePosition.value))
const canReindexPackageAtoms = computed(() => canReindexQuestionBankAtoms(activePosition.value))
const canArchivePackageAtoms = computed(() => canArchiveQuestionBankAtoms(activePosition.value))
const packageErrors = computed(() => [
  ...((importPreview.value?.errors) || []),
  ...((importResult.value?.errors) || [])
])

const selectPosition = (position) => {
  activePositionId.value = position.id
  resetImportPackageState()
  resetPackageAtoms()
}

const loadWorkspace = async () => {
  loading.value = true
  try {
    const data = await getKnowledgeWorkspaceAPI()
    positions.value = data?.positions || []
    if (!positions.value.some((item) => item.id === activePositionId.value)) {
      activePositionId.value = positions.value[0]?.id || null
    }
    await loadPackageAtoms({ silent: true })
  } finally {
    loading.value = false
  }
}

const createPosition = async () => {
  const name = createForm.name.trim()
  if (!name) {
    ElMessage.warning('请填写岗位名称')
    return
  }
  creating.value = true
  try {
    const created = await createPrivatePositionAPI({
      name,
      description: createForm.description.trim()
    })
    ElMessage.success('私有岗位已创建')
    createForm.name = ''
    createForm.description = ''
    createDialogVisible.value = false
    await loadWorkspace()
    activePositionId.value = created.id
  } finally {
    creating.value = false
  }
}

const deletePosition = async () => {
  if (!activePosition.value || !isPositionEditable(activePosition.value)) return
  try {
    await ElMessageBox.confirm(
      `确认删除「${activePosition.value.name}」？该操作将永久删除岗位及其所有知识库、原子和相关数据，不可恢复。`,
      '删除岗位',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    await deletePrivatePositionAPI(activePosition.value.id)
    ElMessage.success('岗位已删除')
    await loadWorkspace()
  } finally {
    deleting.value = false
  }
}

const handleImportPackageFile = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  importPreview.value = null
  importResult.value = null
  try {
    const text = await file.text()
    importPackage.value = parseImportPackageText(text)
    importFileName.value = file.name
    ElMessage.success('导入包已读取')
  } catch (error) {
    importPackage.value = null
    importFileName.value = ''
    ElMessage.error(error.message || '读取导入包失败')
  } finally {
    event.target.value = ''
  }
}

const validateImportPackage = async () => {
  if (!activeKnowledgeBaseId.value || !importPackage.value) return
  importLoading.value = true
  try {
    importPreview.value = await validateKnowledgeBaseImportAPI(activeKnowledgeBaseId.value, importPackage.value)
    importResult.value = null
    if (packageErrors.value.length) {
      ElMessage.warning('导入包校验完成，但存在错误')
    } else {
      ElMessage.success('导入包校验通过')
    }
  } finally {
    importLoading.value = false
  }
}

const importPackageDraft = async () => {
  if (!activeKnowledgeBaseId.value || !importPackage.value) return
  importingPackage.value = true
  try {
    importResult.value = await importKnowledgeBasePackageAPI(activeKnowledgeBaseId.value, importPackage.value)
    importPreview.value = null
    if (importResult.value.failed > 0 || importResult.value.errors?.length) {
      ElMessage.warning(`导入完成，失败 ${importResult.value.failed || 0} 条`)
    } else {
      ElMessage.success(`已导入 ${importResult.value.imported || 0} 条草稿原子`)
    }
    packageAtomFilters.batchId = importResult.value.batchId || importPackage.value.batchId || packageAtomFilters.batchId
    packageAtomPage.page = 1
    await loadPackageAtoms()
  } finally {
    importingPackage.value = false
  }
}

const loadPackageAtoms = async (options = {}) => {
  if (!activeKnowledgeBaseId.value || !canMaintainPackage.value) {
    resetPackageAtoms()
    return
  }
  packageAtomsLoading.value = !options.silent
  try {
    const response = await searchKnowledgeBaseAtomsAPI(activeKnowledgeBaseId.value, {
      ...cleanPackageFilters(),
      page: packageAtomPage.page,
      size: packageAtomPage.size
    })
    packageAtoms.value = response?.items || []
    packageAtomPage.total = response?.total || 0
    packageAtomPage.page = response?.page || packageAtomPage.page
    packageAtomPage.size = response?.size || packageAtomPage.size
    selectedPackageAtomIds.value = []
  } finally {
    packageAtomsLoading.value = false
  }
}

const cleanPackageFilters = () => Object.fromEntries(
  Object.entries(packageAtomFilters)
    .map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value])
    .filter(([, value]) => value)
)

const handlePackageAtomSelectionChange = (selection) => {
  selectedPackageAtomIds.value = (selection || [])
    .map((item) => item.atomId)
    .filter(Boolean)
}

const publishSelectedPackageAtoms = async () => {
  await runPackageAtomAction('publish', async () => {
    const result = await publishKnowledgeBaseAtomsAPI(activeKnowledgeBaseId.value, selectedPackageAtomIds.value)
    ElMessage.success(`已发布 ${result?.published || 0} 条，向量同步成功 ${result?.synced || 0} 条`)
  })
}

const reindexSelectedPackageAtoms = async () => {
  await runPackageAtomAction('reindex', async () => {
    const result = await reindexKnowledgeBaseAtomsAPI(activeKnowledgeBaseId.value, selectedPackageAtomIds.value)
    ElMessage.success(`重建索引完成：成功 ${result?.synced || 0} 条，失败 ${result?.failed || 0} 条`)
  })
}

const archiveSelectedPackageAtoms = async () => {
  if (!selectedPackageAtomIds.value.length) return
  try {
    await ElMessageBox.confirm(`确认归档所选 ${selectedPackageAtomIds.value.length} 条原子？`, '归档原子', {
      confirmButtonText: '归档',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  await runPackageAtomAction('archive', async () => {
    const result = await archiveKnowledgeBaseAtomsAPI(activeKnowledgeBaseId.value, selectedPackageAtomIds.value)
    ElMessage.success(`已归档 ${result?.archived || 0} 条`)
  })
}

const publishAllDraftAtoms = async () => {
  if (!activeKnowledgeBaseId.value || !canPublishPackageAtoms.value) return
  try {
    await ElMessageBox.confirm(
      '确认将当前知识库内所有草稿原子一键发布？已发布的原子将被跳过。',
      '一键发布全部草稿',
      {
        confirmButtonText: '确认发布',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  packageAtomActionLoading.value = 'publishAllDrafts'
  try {
    const result = await publishAllDraftAtomsAPI(activeKnowledgeBaseId.value)
    ElMessage.success(`已发布 ${result?.published || 0} 条，向量同步成功 ${result?.synced || 0} 条，失败 ${result?.failed || 0} 条`)
    await loadPackageAtoms()
  } finally {
    packageAtomActionLoading.value = ''
  }
}

const runPackageAtomAction = async (action, callback) => {
  if (!activeKnowledgeBaseId.value || !selectedPackageAtomIds.value.length) return
  packageAtomActionLoading.value = action
  try {
    await callback()
    await loadPackageAtoms()
  } finally {
    packageAtomActionLoading.value = ''
  }
}

const resetImportPackageState = () => {
  importPackage.value = null
  importFileName.value = ''
  importPreview.value = null
  importResult.value = null
}

const resetPackageAtoms = () => {
  packageAtoms.value = []
  selectedPackageAtomIds.value = []
  packageAtomPage.page = 1
  packageAtomPage.total = 0
}

onMounted(loadWorkspace)
</script>

<style scoped>
.knowledge-page {
  display: grid;
  gap: 22px;
}

.knowledge-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.brand-cluster,
.header-actions,
.section-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-copy,
.section-head > div {
  min-width: 0;
}

.eyebrow,
.section-kicker {
  margin: 0 0 4px;
  color: var(--app-text-muted);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.page-title,
.section-title {
  margin: 0;
  color: var(--app-text);
  line-height: 1.2;
}

.page-title {
  font-size: 1.55rem;
}

.page-subtitle,
.section-desc,
.file-toolbar p {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 0.94rem;
}

.page-body {
  padding: 0;
}

.knowledge-body {
  display: grid;
  grid-template-columns: minmax(260px, 340px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.surface-card {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface);
  box-shadow: var(--app-shadow-sm);
}

.section-shell {
  padding: 20px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 18px;
}

.section-head.compact {
  align-items: center;
}

.position-list {
  display: grid;
  gap: 8px;
}

.position-item {
  width: 100%;
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-2);
  color: var(--app-text);
  text-align: left;
  cursor: pointer;
}

.position-item.is-active {
  border-color: rgba(58, 56, 139, 0.34);
  background: rgba(58, 56, 139, 0.07);
}

.position-item.is-archived {
  opacity: 0.72;
}

.position-item__title {
  font-weight: 700;
}

.position-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.workspace-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 18px;
}

.summary-item {
  display: grid;
  gap: 4px;
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
  min-width: 0;
  overflow-wrap: anywhere;
}

.upload-zone {
  margin-bottom: 20px;
}

.upload-zone.is-disabled {
  opacity: 0.68;
}

.upload-icon {
  margin-bottom: 8px;
  color: var(--app-primary);
  font-size: 2rem;
}

.upload-title {
  font-weight: 700;
}

.upload-hint,
.readonly-note,
.muted-text {
  color: var(--app-text-muted);
  font-size: 0.9rem;
}

.readonly-note {
  margin: 8px 0 0;
}

.file-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin: 18px 0 12px;
}

.file-toolbar h3 {
  margin: 0;
  font-size: 1rem;
}

.file-table {
  width: 100%;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.package-panel {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid var(--app-border);
}

.package-panel.is-disabled {
  opacity: 0.76;
}

.package-toolbar {
  margin-top: 0;
}

.package-file-name {
  margin: 0 0 12px;
  color: var(--app-text);
  font-size: 0.9rem;
  overflow-wrap: anywhere;
}

.package-result {
  display: grid;
  gap: 8px;
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-2);
}

.package-result__summary,
.package-bulk-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 14px;
}

.package-result__summary span {
  color: var(--app-text);
  font-size: 0.9rem;
}

.package-errors {
  margin: 0;
  padding-left: 18px;
  color: var(--app-danger);
  font-size: 0.9rem;
}

.package-filters {
  display: grid;
  grid-template-columns: minmax(180px, 1.3fr) repeat(4, minmax(120px, 0.8fr)) auto;
  gap: 10px;
  margin-bottom: 12px;
}

.package-bulk-actions {
  justify-content: flex-end;
  margin-bottom: 12px;
}

.package-pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.atom-panel {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid var(--app-border);
}

.atom-toolbar {
  margin-top: 0;
}

.atom-toolbar__actions,
.atom-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.atom-toolbar__actions {
  justify-content: flex-end;
}

.atom-actions .el-button + .el-button,
.atom-toolbar__actions .el-button + .el-button {
  margin-left: 0;
}

.atom-table {
  width: 100%;
}

.atom-form {
  display: grid;
  gap: 2px;
}

.atom-form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: 12px;
}

.job-progress {
  display: grid;
  gap: 4px;
}

.job-progress span {
  color: var(--app-text-muted);
  font-size: 0.82rem;
}

.error-text {
  color: var(--app-danger);
  overflow-wrap: anywhere;
}

.markdown-preview {
  max-height: 62vh;
  margin: 0;
  padding: 14px;
  overflow: auto;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: #0f172a;
  color: #e5edf7;
  white-space: pre-wrap;
}

@media (max-width: 1180px) {
  .knowledge-body {
    grid-template-columns: 1fr;
  }

  .workspace-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .knowledge-header,
  .section-head,
  .file-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .brand-cluster {
    align-items: flex-start;
  }

  .header-actions,
  .section-actions {
    width: 100%;
  }

  .header-actions .el-button,
  .section-actions .el-button {
    flex: 1;
  }

  .workspace-summary {
    grid-template-columns: 1fr;
  }

  .atom-toolbar__actions {
    justify-content: stretch;
  }

  .package-filters {
    grid-template-columns: 1fr;
  }

  .package-bulk-actions {
    justify-content: stretch;
  }

  .package-bulk-actions .el-button {
    flex: 1;
  }

  .atom-toolbar__actions .el-button {
    flex: 1;
  }

  .atom-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
