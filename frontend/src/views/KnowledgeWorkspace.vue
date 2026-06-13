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
                :loading="archiving"
                @click="archivePosition"
              >
                归档岗位
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
              <span>文件数</span>
              <strong>{{ sourceFiles.length }}</strong>
            </div>
            <div class="summary-item">
              <span>上传权限</span>
              <strong>{{ canUpload ? '可上传' : '不可上传' }}</strong>
            </div>
          </div>

          <div class="upload-zone" :class="{ 'is-disabled': !canUpload }">
            <el-upload
              drag
              :disabled="!canUpload"
              :show-file-list="false"
              :http-request="uploadFile"
              :before-upload="beforeUpload"
              accept=".pdf,.docx,.md,.markdown,.txt"
            >
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="upload-title">上传知识文件</div>
              <div class="upload-hint">支持 PDF、DOCX、Markdown/MD、TXT，单个文件不超过 20MB</div>
            </el-upload>
            <p v-if="!canUpload" class="readonly-note">公共岗位为只读内容，只有自己的私有岗位可以上传文件。</p>
          </div>

          <div class="file-toolbar">
            <div>
              <h3>文件与转换状态</h3>
              <p>上传后会创建后台转换作业，可在列表中查看进度和失败原因。</p>
            </div>
            <el-button :icon="RefreshRight" :loading="loadingJobs" @click="loadJobs">刷新作业</el-button>
          </div>

          <el-table :data="sourceFiles" class="file-table" empty-text="当前知识库还没有上传文件">
            <el-table-column prop="originalFilename" label="文件名" min-width="220" />
            <el-table-column label="文件状态" width="130">
              <template #default="{ row }">
                <el-tag :type="getFileStatusType(row.status)" effect="plain">
                  {{ getFileStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="作业进度" min-width="180">
              <template #default="{ row }">
                <div v-if="jobFor(row)" class="job-progress">
                  <el-progress :percentage="jobFor(row).progress || 0" :stroke-width="8" />
                  <span>{{ jobFor(row).status }} / {{ jobFor(row).stage || '等待调度' }}</span>
                </div>
                <span v-else class="muted-text">暂无作业</span>
              </template>
            </el-table-column>
            <el-table-column label="Markdown" width="120">
              <template #default="{ row }">
                <el-button
                  size="small"
                  :disabled="!row.hasMarkdown"
                  @click="openMarkdown(row)"
                >
                  查看
                </el-button>
              </template>
            </el-table-column>
            <el-table-column label="错误" min-width="180">
              <template #default="{ row }">
                <span class="error-text">{{ row.errorMessage || jobFor(row)?.errorMessage || '-' }}</span>
              </template>
            </el-table-column>
          </el-table>
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

    <el-dialog v-model="markdownDialogVisible" title="Markdown 预览" width="min(92vw, 820px)">
      <pre class="markdown-preview">{{ markdownPreview }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Delete, Plus, RefreshRight, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  archivePrivatePositionAPI,
  createPrivatePositionAPI,
  getAppJobsAPI,
  getKnowledgeWorkspaceAPI,
  uploadKnowledgeFileAPI
} from '@/api/knowledgeWorkspace'
import {
  canUploadToPosition,
  findLatestJobForSourceFile,
  getFileStatusLabel,
  getFileStatusType,
  getPositionScopeLabel,
  getPositionStatusType,
  isPositionEditable
} from '@/utils/knowledgeWorkspace'
import { withAuthHeaders } from '@/utils/auth'

const router = useRouter()
const loading = ref(false)
const loadingJobs = ref(false)
const creating = ref(false)
const archiving = ref(false)
const positions = ref([])
const jobs = ref([])
const activePositionId = ref(null)
const createDialogVisible = ref(false)
const markdownDialogVisible = ref(false)
const markdownPreview = ref('')
const createForm = reactive({ name: '', description: '' })
let pollTimer = null

const activePosition = computed(() => positions.value.find((item) => item.id === activePositionId.value) || positions.value[0] || null)
const sourceFiles = computed(() => activePosition.value?.knowledgeBase?.sourceFiles || [])
const canUpload = computed(() => canUploadToPosition(activePosition.value))

const selectPosition = (position) => {
  activePositionId.value = position.id
}

const loadWorkspace = async () => {
  loading.value = true
  try {
    const data = await getKnowledgeWorkspaceAPI()
    positions.value = data?.positions || []
    if (!positions.value.some((item) => item.id === activePositionId.value)) {
      activePositionId.value = positions.value[0]?.id || null
    }
    await loadJobs({ silent: true })
  } finally {
    loading.value = false
  }
}

const loadJobs = async (options = {}) => {
  loadingJobs.value = !options.silent
  try {
    jobs.value = await getAppJobsAPI({ silent: true })
  } finally {
    loadingJobs.value = false
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

const archivePosition = async () => {
  if (!activePosition.value || !isPositionEditable(activePosition.value)) return
  try {
    await ElMessageBox.confirm(`确认归档「${activePosition.value.name}」？归档后不能继续上传文件。`, '归档岗位', {
      confirmButtonText: '归档',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  archiving.value = true
  try {
    await archivePrivatePositionAPI(activePosition.value.id)
    ElMessage.success('岗位已归档')
    await loadWorkspace()
  } finally {
    archiving.value = false
  }
}

const beforeUpload = (file) => {
  const allowed = ['pdf', 'docx', 'md', 'markdown', 'txt']
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!allowed.includes(extension)) {
    ElMessage.error('仅支持 PDF、DOCX、Markdown/MD 和 TXT 文件')
    return false
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('知识库文件不能超过 20MB')
    return false
  }
  return true
}

const uploadFile = async ({ file, onSuccess, onError }) => {
  if (!activePosition.value?.knowledgeBase?.id) {
    const error = new Error('当前岗位没有可上传的知识库')
    ElMessage.error(error.message)
    onError?.(error)
    return
  }
  try {
    const result = await uploadKnowledgeFileAPI(activePosition.value.knowledgeBase.id, file)
    ElMessage.success('文件已上传，正在转换')
    onSuccess?.(result)
    await loadWorkspace()
    startPolling()
  } catch (error) {
    onError?.(error)
  }
}

const jobFor = (file) => findLatestJobForSourceFile(jobs.value, file.id)

const openMarkdown = async (file) => {
  try {
    const response = await fetch(`${import.meta.env.VITE_API_BASE_URL || ''}/api/knowledge-files/${file.id}/markdown`, {
      headers: withAuthHeaders()
    })
    if (!response.ok) {
      throw new Error('读取 Markdown 失败')
    }
    markdownPreview.value = await response.text()
    markdownDialogVisible.value = true
  } catch (error) {
    ElMessage.error(error.message || '读取 Markdown 失败')
  }
}

const startPolling = () => {
  if (pollTimer) return
  pollTimer = window.setInterval(async () => {
    await loadWorkspace()
    const visibleSourceFileIds = new Set(sourceFiles.value.map((file) => file.id))
    const activeJobs = jobs.value.some((job) =>
      visibleSourceFileIds.has(job.sourceFileId) && ['PENDING', 'RUNNING'].includes(job.status)
    )
    if (!activeJobs) {
      window.clearInterval(pollTimer)
      pollTimer = null
    }
  }, 3000)
}

onMounted(loadWorkspace)

onBeforeUnmount(() => {
  if (pollTimer) {
    window.clearInterval(pollTimer)
  }
})
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
}
</style>
