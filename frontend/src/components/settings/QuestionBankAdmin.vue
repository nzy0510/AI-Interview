<template>
  <div class="question-bank-admin">
    <div class="admin-panel-row">
      <div>
        <h3 class="panel-title">题库后台</h3>
        <p class="panel-desc">管理员账号可执行题库导入、发布、归档和索引维护；权限由当前登录状态校验。</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="admin-tabs">
      <el-tab-pane label="导入发布" name="import">
        <div class="toolbar-line">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept=".json,application/json"
            :on-change="handleFileChange"
          >
            <el-button :icon="Upload">选择 JSON 导入包</el-button>
          </el-upload>
          <span class="file-name">{{ fileName || '未选择文件' }}</span>
          <el-button :icon="Search" :disabled="!importPackage" :loading="importLoading" @click="validateImport">
            校验
          </el-button>
          <el-button :icon="Refresh" :disabled="!canDryRun" :loading="dryRunLoading" @click="dryRunImport">
            试运行
          </el-button>
          <el-button type="primary" :icon="Check" :disabled="!canPublish" :loading="publishLoading" @click="publishImport">
            正式发布
          </el-button>
        </div>

        <el-alert
          v-if="preview?.errors?.length"
          class="inline-alert"
          type="error"
          :closable="false"
          title="导入包未通过校验"
        >
          <ul class="compact-list">
            <li v-for="error in preview.errors" :key="error">{{ error }}</li>
          </ul>
        </el-alert>

        <div v-if="preview" class="summary-grid">
          <div class="summary-item">
            <span>批次</span>
            <strong>{{ preview.batchId }}</strong>
          </div>
          <div class="summary-item">
            <span>模式</span>
            <strong>{{ modeLabel(preview.mode) }}</strong>
          </div>
          <div class="summary-item">
            <span>题目数</span>
            <strong>{{ preview.received }}</strong>
          </div>
          <div class="summary-item">
            <span>新增 / 更新</span>
            <strong>{{ preview.newCount }} / {{ preview.updateCount }}</strong>
          </div>
          <div class="summary-item">
            <span>批次号状态</span>
            <strong>{{ preview.batchIdExists ? '已存在，发布时会自动生成新批次号' : '可使用' }}</strong>
          </div>
        </div>

        <el-collapse v-if="preview && (preview.updateAtomIds?.length || preview.duplicateAtomIds?.length)" class="preview-collapse">
          <el-collapse-item title="题目 ID 变更明细" name="ids">
            <p v-if="preview.updateAtomIds?.length" class="id-line">将更新：{{ preview.updateAtomIds.join('、') }}</p>
            <p v-if="preview.duplicateAtomIds?.length" class="id-line danger">包内重复：{{ preview.duplicateAtomIds.join('、') }}</p>
          </el-collapse-item>
        </el-collapse>

        <div v-if="dryRunResult || publishResult" class="result-line">
          <el-tag v-if="dryRunResult" type="info">试运行：{{ resultText(dryRunResult) }}</el-tag>
          <el-tag v-if="publishResult" type="success">发布：{{ resultText(publishResult) }}</el-tag>
        </div>
      </el-tab-pane>

      <el-tab-pane label="题库查询" name="atoms">
        <div class="toolbar-line filters">
          <el-input v-model="atomFilters.keyword" clearable placeholder="关键词 / 题目 ID" />
          <el-input v-model="atomFilters.category" clearable placeholder="分类" />
          <el-select v-model="atomFilters.status" clearable placeholder="状态">
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
          <el-input v-model="atomFilters.difficulty" clearable placeholder="难度" />
          <el-select v-model="atomFilters.vectorStatus" clearable placeholder="索引状态">
            <el-option label="已同步" value="SYNCED" />
            <el-option label="待同步" value="PENDING" />
            <el-option label="失败" value="FAILED" />
            <el-option label="跳过" value="SKIPPED" />
          </el-select>
          <el-input v-model="atomFilters.sourceRef" clearable placeholder="来源" />
          <el-input v-model="atomFilters.batchId" clearable placeholder="批次号" />
          <el-button type="primary" :icon="Search" :loading="atomsLoading" @click="loadAtoms">
            查询
          </el-button>
        </div>

        <div class="toolbar-line">
          <el-button :icon="Delete" :disabled="!selectedAtomIds.length" @click="archiveSelectedAtoms">归档所选</el-button>
          <el-button :icon="Check" :disabled="!selectedAtomIds.length" @click="publishSelectedAtoms">恢复发布</el-button>
          <el-button :icon="Refresh" :disabled="!selectedAtomIds.length" @click="reindexSelectedAtoms">重试所选索引</el-button>
          <el-button :icon="Refresh" @click="reindexUnsynced">重建未同步</el-button>
          <el-button type="warning" plain :icon="Refresh" @click="reindexAll">全量重建索引</el-button>
        </div>

        <el-table :data="atoms" border stripe @selection-change="handleAtomSelectionChange" v-loading="atomsLoading">
          <el-table-column type="selection" width="44" />
          <el-table-column prop="atomId" label="题目 ID" min-width="180" show-overflow-tooltip />
          <el-table-column prop="subject" label="考核点" min-width="220" show-overflow-tooltip />
          <el-table-column prop="category" label="分类" width="140" />
          <el-table-column prop="difficulty" label="难度" width="100" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="vectorStatus" label="索引" width="100" />
          <el-table-column prop="sourceRef" label="来源" min-width="160" show-overflow-tooltip />
          <el-table-column prop="updateTime" label="更新时间" min-width="150" />
        </el-table>
        <el-pagination
          v-model:current-page="atomPage.page"
          v-model:page-size="atomPage.size"
          class="pager"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="atomPage.total"
          @current-change="loadAtoms"
          @size-change="loadAtoms"
        />
      </el-tab-pane>

      <el-tab-pane label="批次与检索" name="batches">
        <div class="toolbar-line">
          <el-button type="primary" :icon="Refresh" :loading="categoriesLoading" @click="loadCategories">
            刷新分类概览
          </el-button>
          <el-button :icon="Refresh" :loading="batchesLoading" @click="loadBatches">
            刷新批次
          </el-button>
        </div>

        <div v-if="categories.length" class="category-strip">
          <el-tag v-for="item in categories" :key="item.category" effect="plain">
            {{ item.category }}：{{ item.published || 0 }} / {{ item.total || 0 }}
          </el-tag>
        </div>

        <el-table :data="batches" border stripe v-loading="batchesLoading">
          <el-table-column prop="batchId" label="批次号" min-width="180" show-overflow-tooltip />
          <el-table-column prop="targetCategory" label="目标分类" width="140" />
          <el-table-column prop="mode" label="模式" width="120">
            <template #default="{ row }">{{ modeLabel(row.mode) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="110" />
          <el-table-column prop="atomCount" label="题目数" width="90" />
          <el-table-column prop="sourceRef" label="来源" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openBatch(row)">查看</el-button>
              <el-button size="small" type="danger" plain @click="archiveBatch(row)">归档</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="search-preview">
          <h4 class="sub-title">检索预览</h4>
          <div class="toolbar-line filters">
            <el-input v-model="searchForm.query" clearable placeholder="输入面试追问或考核点" />
            <el-input v-model="searchForm.category" clearable placeholder="分类，可留空" />
            <el-select v-model="searchForm.limit" placeholder="返回数量">
              <el-option label="3 条" :value="3" />
              <el-option label="5 条" :value="5" />
              <el-option label="10 条" :value="10" />
            </el-select>
            <el-button type="primary" :icon="Search" :loading="searchLoading" @click="runSearchPreview">
              预览
            </el-button>
          </div>
          <el-table :data="searchResults" border stripe>
            <el-table-column prop="atomId" label="题目 ID" min-width="180" show-overflow-tooltip />
            <el-table-column prop="subject" label="考核点" min-width="220" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="140" />
            <el-table-column label="匹配度" width="100">
              <template #default="{ row }">{{ scoreText(row.score) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="batchDialogVisible" title="批次详情" width="80%">
      <p v-if="batchDetail?.batch" class="dialog-summary">
        {{ batchDetail.batch.batchId }}，当前关联 {{ batchDetail.latestLinkedCount }} / 历史 {{ batchDetail.atomCount }} 个题目。
      </p>
      <el-table :data="batchDetail?.atoms || []" border stripe>
        <el-table-column prop="atomId" label="题目 ID" min-width="180" show-overflow-tooltip />
        <el-table-column prop="subject" label="考核点" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="vectorStatus" label="索引" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { Check, Delete, Refresh, Search, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  archiveQuestionBankAtomsAPI,
  archiveQuestionBankBatchAPI,
  dryRunQuestionBankImportAPI,
  getQuestionBankBatchAPI,
  getQuestionBankCategoriesAPI,
  listQuestionBankBatchesAPI,
  previewQuestionBankSearchAPI,
  publishQuestionBankAtomsAPI,
  publishQuestionBankImportAPI,
  reindexAllQuestionBankAPI,
  reindexQuestionBankAtomsAPI,
  reindexUnsyncedQuestionBankAPI,
  searchQuestionBankAtomsAPI,
  validateQuestionBankImportAPI
} from '@/api/questionBankAdmin'

const activeTab = ref('import')

const importPackage = ref(null)
const fileName = ref('')
const preview = ref(null)
const dryRunResult = ref(null)
const publishResult = ref(null)
const importLoading = ref(false)
const dryRunLoading = ref(false)
const publishLoading = ref(false)

const categories = ref([])
const categoriesLoading = ref(false)

const atomFilters = reactive({
  keyword: '',
  category: '',
  status: '',
  difficulty: '',
  vectorStatus: '',
  sourceRef: '',
  batchId: ''
})
const atomPage = reactive({ page: 1, size: 20, total: 0 })
const atoms = ref([])
const selectedAtoms = ref([])
const atomsLoading = ref(false)
const selectedAtomIds = computed(() => selectedAtoms.value.map(row => row.atomId))
const handleAtomSelectionChange = (rows) => {
  selectedAtoms.value = rows
}

const batches = ref([])
const batchesLoading = ref(false)
const batchDialogVisible = ref(false)
const batchDetail = ref(null)

const searchForm = reactive({ query: '', category: '', limit: 3 })
const searchResults = ref([])
const searchLoading = ref(false)

const canDryRun = computed(() => importPackage.value && preview.value && !preview.value.errors?.length)
const canPublish = computed(() => canDryRun.value && dryRunResult.value && !dryRunResult.value.failed)

const handleFileChange = async (uploadFile) => {
  const raw = uploadFile.raw
  if (!raw) return
  try {
    const text = await raw.text()
    const parsed = JSON.parse(text)
    if (!parsed || !Array.isArray(parsed.atoms)) {
      ElMessage.warning('导入包需要包含 atoms 数组')
      return
    }
    importPackage.value = parsed
    fileName.value = raw.name
    preview.value = null
    dryRunResult.value = null
    publishResult.value = null
    ElMessage.success('导入包已读取')
  } catch (e) {
    ElMessage.error('JSON 解析失败，请检查导入包格式')
  }
}

const validateImport = async () => {
  if (!importPackage.value) return
  importLoading.value = true
  try {
    preview.value = await validateQuestionBankImportAPI(importPackage.value)
    dryRunResult.value = null
    publishResult.value = null
    ElMessage.success(preview.value.errors?.length ? '校验完成，请处理错误' : '校验通过')
  } catch (e) {
    ElMessage.error(e.message || '校验失败')
  } finally {
    importLoading.value = false
  }
}

const dryRunImport = async () => {
  if (!importPackage.value) return
  dryRunLoading.value = true
  try {
    const response = await dryRunQuestionBankImportAPI(importPackage.value)
    preview.value = response.preview
    dryRunResult.value = response.result
    publishResult.value = null
    ElMessage.success('试运行完成，未写入题库')
  } catch (e) {
    ElMessage.error(e.message || '试运行失败')
  } finally {
    dryRunLoading.value = false
  }
}

const publishImport = async () => {
  if (!importPackage.value) return
  publishLoading.value = true
  try {
    const response = await publishQuestionBankImportAPI(importPackage.value)
    preview.value = response.preview
    publishResult.value = response.result
    ElMessage.success('题库已发布')
    await Promise.all([loadCategories(), loadAtoms(), loadBatches()])
  } catch (e) {
    ElMessage.error(e.message || '发布失败')
  } finally {
    publishLoading.value = false
  }
}

const loadCategories = async () => {
  categoriesLoading.value = true
  try {
    categories.value = await getQuestionBankCategoriesAPI()
  } finally {
    categoriesLoading.value = false
  }
}

const loadAtoms = async () => {
  atomsLoading.value = true
  try {
    const response = await searchQuestionBankAtomsAPI({
      ...atomFilters,
      page: atomPage.page,
      size: atomPage.size
    })
    atoms.value = response.items || []
    atomPage.total = response.total || 0
  } finally {
    atomsLoading.value = false
  }
}

const loadBatches = async () => {
  batchesLoading.value = true
  try {
    const response = await listQuestionBankBatchesAPI({ page: 1, size: 50 })
    batches.value = response.items || []
  } finally {
    batchesLoading.value = false
  }
}

const requirePhrase = async (phrase, title, message) => {
  try {
    await ElMessageBox.prompt(message, title, {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPattern: new RegExp(`^${phrase}$`),
      inputErrorMessage: `请输入 ${phrase}`
    })
    return true
  } catch {
    return false
  }
}

const archiveSelectedAtoms = async () => {
  if (!await requirePhrase('ARCHIVE', '归档题目', '输入 ARCHIVE 确认归档所选题目。')) return
  const result = await archiveQuestionBankAtomsAPI(selectedAtomIds.value)
  ElMessage.success(`归档完成：${resultText(result)}`)
  await loadAtoms()
}

const publishSelectedAtoms = async () => {
  if (!await requirePhrase('PUBLISH', '恢复发布', '输入 PUBLISH 确认恢复发布所选题目。')) return
  const result = await publishQuestionBankAtomsAPI(selectedAtomIds.value)
  ElMessage.success(`恢复发布完成：${resultText(result)}`)
  await loadAtoms()
}

const reindexSelectedAtoms = async () => {
  const result = await reindexQuestionBankAtomsAPI(selectedAtomIds.value)
  ElMessage.success(`索引完成：${resultText(result)}`)
  await loadAtoms()
}

const reindexUnsynced = async () => {
  const result = await reindexUnsyncedQuestionBankAPI()
  ElMessage.success(`未同步题目处理完成：${resultText(result)}`)
  await loadAtoms()
}

const reindexAll = async () => {
  if (!await requirePhrase('REINDEX', '全量重建索引', '全量重建可能较慢。输入 REINDEX 确认继续。')) return
  const result = await reindexAllQuestionBankAPI()
  ElMessage.success(`全量重建完成：${resultText(result)}`)
  await loadAtoms()
}

const openBatch = async (row) => {
  batchDetail.value = await getQuestionBankBatchAPI(row.batchId)
  batchDialogVisible.value = true
}

const archiveBatch = async (row) => {
  if (!await requirePhrase('ARCHIVE', '归档批次', `输入 ARCHIVE 确认归档批次 ${row.batchId} 当前仍关联的题目。`)) return
  const result = await archiveQuestionBankBatchAPI(row.batchId)
  ElMessage.success(`批次归档完成：${resultText(result)}`)
  await Promise.all([loadAtoms(), loadBatches()])
}

const runSearchPreview = async () => {
  if (!searchForm.query || searchForm.query.trim().length <= 2) {
    ElMessage.warning('检索内容至少 3 个字符')
    return
  }
  searchLoading.value = true
  try {
    searchResults.value = await previewQuestionBankSearchAPI({
      query: searchForm.query,
      categories: searchForm.category ? [searchForm.category] : [],
      limit: searchForm.limit
    })
  } finally {
    searchLoading.value = false
  }
}

const modeLabel = (mode) => {
  if (mode === 'AUTO_PUBLISH') return '自动发布'
  if (mode === 'DRY_RUN') return '试运行'
  return mode || '-'
}

const resultText = (result) => {
  if (!result) return '-'
  return Object.entries(result)
    .map(([key, value]) => `${key}:${value}`)
    .join('，')
}

const scoreText = (score) => {
  const value = Number(score || 0)
  return value > 0 ? value.toFixed(3) : 'LIKE'
}
</script>

<style scoped>
.question-bank-admin {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.admin-panel-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 360px);
  gap: 16px;
  align-items: center;
}

.panel-title,
.sub-title {
  margin: 0;
  color: #191c1e;
  font-size: 18px;
  line-height: 1.3;
}

.panel-desc {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.admin-tabs {
  --el-border-radius-base: 8px;
}

.toolbar-line {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
}

.toolbar-line.filters > * {
  flex: 1 1 160px;
}

.file-name {
  color: #64748b;
  font-size: 13px;
  min-width: 160px;
}

.inline-alert {
  margin-bottom: 14px;
}

.compact-list {
  margin: 8px 0 0;
  padding-left: 18px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(120px, 1fr));
  gap: 10px;
}

.summary-item {
  min-width: 0;
  padding: 12px;
  border: 1px solid rgba(69, 70, 82, 0.08);
  border-radius: 8px;
  background: #fafafa;
}

.summary-item span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.summary-item strong {
  display: block;
  margin-top: 4px;
  color: #1f2937;
  font-size: 14px;
  overflow-wrap: anywhere;
}

.preview-collapse,
.result-line,
.search-preview {
  margin-top: 14px;
}

.id-line {
  margin: 0 0 8px;
  color: #334155;
  overflow-wrap: anywhere;
}

.id-line.danger {
  color: #b91c1c;
}

.category-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.dialog-summary {
  margin: 0 0 12px;
  color: #475569;
}

@media (max-width: 860px) {
  .admin-panel-row {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }
}

@media (max-width: 520px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
