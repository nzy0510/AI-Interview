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
            <el-table-column label="原子" width="120">
              <template #default="{ row }">
                <el-button
                  size="small"
                  :type="activeSourceFile?.id === row.id ? 'primary' : 'default'"
                  @click="selectSourceFile(row)"
                >
                  查看
                </el-button>
              </template>
            </el-table-column>
            <el-table-column label="作业操作" width="120">
              <template #default="{ row }">
                <el-button
                  size="small"
                  :icon="RefreshRight"
                  :loading="retryingJobId === jobFor(row)?.id"
                  :disabled="!canManageAtoms || !canRetryJob(jobFor(row))"
                  @click="retryJob(row)"
                >
                  重试
                </el-button>
              </template>
            </el-table-column>
            <el-table-column label="错误" min-width="180">
              <template #default="{ row }">
                <span class="error-text">{{ row.errorMessage || jobFor(row)?.errorMessage || '-' }}</span>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="activeSourceFile" class="atom-panel">
            <div class="file-toolbar atom-toolbar">
              <div>
                <h3>原子审查与发布</h3>
                <p>{{ activeSourceFile.originalFilename }} · {{ atoms.length }} 条原子</p>
              </div>
              <div class="atom-toolbar__actions">
                <el-button :icon="RefreshRight" :loading="loadingAtoms" @click="loadAtoms">刷新原子</el-button>
                <el-button
                  :loading="generatingAtoms"
                  :disabled="!canManageAtoms || !activeSourceFile.hasMarkdown"
                  @click="generateAtoms"
                >
                  {{ generateAtomButtonLabel }}
                </el-button>
                <el-button
                  type="success"
                  :loading="publishingAtoms"
                  :disabled="!canManageAtoms || publishableAtomCount === 0"
                  @click="publishAllAtoms"
                >
                  一键发布 {{ publishableAtomCount || '' }}
                </el-button>
                <el-button
                  type="primary"
                  :disabled="!canManageAtoms"
                  @click="openCreateAtom"
                >
                  新建原子
                </el-button>
              </div>
            </div>

            <el-table :data="atoms" class="atom-table" empty-text="当前文件还没有知识原子">
              <el-table-column prop="subject" label="考点" min-width="180" />
              <el-table-column label="二审" width="110">
                <template #default="{ row }">
                  <el-tag :type="getAtomReviewType(row.reviewStatus)" effect="plain">
                    {{ getAtomReviewLabel(row.reviewStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="发布" width="110">
                <template #default="{ row }">
                  <el-tag :type="getPublicationStatusType(row.publicationStatus)" effect="plain">
                    {{ getPublicationStatusLabel(row.publicationStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="vectorStatus" label="向量" width="100" />
              <el-table-column label="审查原因" min-width="180">
                <template #default="{ row }">
                  <span class="muted-text">{{ row.reviewReason || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="260" fixed="right">
                <template #default="{ row }">
                  <div class="atom-actions">
                    <el-button
                      size="small"
                      :disabled="!canManageAtoms || !canApplySuggestedPatch(row)"
                      @click="acceptPatch(row)"
                    >
                      应用补丁
                    </el-button>
                    <el-button size="small" :disabled="!canManageAtoms" @click="openEditAtom(row)">编辑</el-button>
                    <el-button
                      size="small"
                      type="primary"
                      :disabled="!canManageAtoms || !canPublishAtom(row)"
                      @click="publishAtom(row)"
                    >
                      发布
                    </el-button>
                  </div>
                </template>
              </el-table-column>
            </el-table>
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

    <el-dialog v-model="markdownDialogVisible" title="Markdown 预览" width="min(92vw, 820px)">
      <pre class="markdown-preview">{{ markdownPreview }}</pre>
    </el-dialog>

    <el-dialog
      v-model="atomDialogVisible"
      :title="editingAtom ? '编辑知识原子' : '新建知识原子'"
      width="min(94vw, 720px)"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" class="atom-form">
        <el-form-item label="考点">
          <el-input v-model="atomForm.subject" maxlength="160" />
        </el-form-item>
        <div class="atom-form-grid">
          <el-form-item label="分类">
            <el-input v-model="atomForm.category" maxlength="80" />
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="atomForm.difficulty">
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
              <el-option label="困难" value="HARD" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="标签">
          <el-input v-model="atomForm.tagsText" placeholder="用逗号分隔，例如 Java,集合,HashMap" />
        </el-form-item>
        <el-form-item label="核心原理与标准答案">
          <el-input v-model="atomForm.principles" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="常见陷阱">
          <el-input v-model="atomForm.pitfalls" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="追问路径">
          <el-input v-model="atomForm.followUpText" type="textarea" :rows="3" placeholder="每行一个追问方向" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="atomDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingAtom" @click="saveAtom">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Delete, Plus, RefreshRight, UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  acceptKnowledgeAtomPatchAPI,
  archivePrivatePositionAPI,
  createManualKnowledgeAtomAPI,
  createPrivatePositionAPI,
  generateKnowledgeAtomsAPI,
  getAppJobsAPI,
  getKnowledgeFileAtomsAPI,
  getKnowledgeWorkspaceAPI,
  publishKnowledgeFileAtomsAPI,
  publishKnowledgeAtomAPI,
  retryAppJobAPI,
  updateKnowledgeAtomAPI,
  uploadKnowledgeFileAPI
} from '@/api/knowledgeWorkspace'
import {
  canApplySuggestedPatch,
  canPublishAtom,
  canRetryJob,
  canUploadToPosition,
  countPublishableAtoms,
  findLatestJobForSourceFile,
  generationCompletionMessage,
  getAtomReviewLabel,
  getAtomReviewType,
  getFileStatusLabel,
  getFileStatusType,
  getPublicationStatusLabel,
  getPublicationStatusType,
  getPositionScopeLabel,
  getPositionStatusType,
  isPositionEditable
} from '@/utils/knowledgeWorkspace'
import { withAuthHeaders } from '@/utils/auth'

const router = useRouter()
const loading = ref(false)
const loadingJobs = ref(false)
const loadingAtoms = ref(false)
const creating = ref(false)
const archiving = ref(false)
const generatingAtoms = ref(false)
const publishingAtoms = ref(false)
const retryingJobId = ref(null)
const savingAtom = ref(false)
const positions = ref([])
const jobs = ref([])
const atoms = ref([])
const activePositionId = ref(null)
const activeSourceFileId = ref(null)
const createDialogVisible = ref(false)
const markdownDialogVisible = ref(false)
const atomDialogVisible = ref(false)
const markdownPreview = ref('')
const createForm = reactive({ name: '', description: '' })
const editingAtom = ref(null)
const atomForm = reactive({
  subject: '',
  category: '',
  difficulty: 'MEDIUM',
  tagsText: '',
  principles: '',
  pitfalls: '',
  followUpText: ''
})
let pollTimer = null

const activePosition = computed(() => positions.value.find((item) => item.id === activePositionId.value) || positions.value[0] || null)
const sourceFiles = computed(() => activePosition.value?.knowledgeBase?.sourceFiles || [])
const canUpload = computed(() => canUploadToPosition(activePosition.value))
const canManageAtoms = computed(() => canUpload.value)
const activeSourceFile = computed(() => sourceFiles.value.find((item) => item.id === activeSourceFileId.value) || sourceFiles.value[0] || null)
const publishableAtomCount = computed(() => countPublishableAtoms(atoms.value))
const generateAtomButtonLabel = computed(() => atoms.value.length > 0 ? '追加生成 / 二审' : '生成 / 二审')

const selectPosition = (position) => {
  activePositionId.value = position.id
  activeSourceFileId.value = null
  atoms.value = []
}

const selectSourceFile = async (file) => {
  activeSourceFileId.value = file.id
  await loadAtoms()
}

const loadWorkspace = async (options = {}) => {
  loading.value = true
  try {
    const data = await getKnowledgeWorkspaceAPI()
    positions.value = data?.positions || []
    if (!positions.value.some((item) => item.id === activePositionId.value)) {
      activePositionId.value = positions.value[0]?.id || null
    }
    if (!sourceFiles.value.some((item) => item.id === activeSourceFileId.value)) {
      activeSourceFileId.value = sourceFiles.value[0]?.id || null
    }
    await loadJobs({ silent: true, notifyGenerationCompletion: options.notifyGenerationCompletion })
    await loadAtoms({ silent: true })
  } finally {
    loading.value = false
  }
}

const loadJobs = async (options = {}) => {
  loadingJobs.value = !options.silent
  try {
    const previousJobs = jobs.value
    jobs.value = await getAppJobsAPI({ silent: true })
    if (options.notifyGenerationCompletion) {
      notifyCompletedGenerationJobs(previousJobs, jobs.value)
    }
  } finally {
    loadingJobs.value = false
  }
}

const loadAtoms = async (options = {}) => {
  if (!activeSourceFile.value?.id) {
    atoms.value = []
    return
  }
  loadingAtoms.value = !options.silent
  try {
    atoms.value = await getKnowledgeFileAtomsAPI(activeSourceFile.value.id, { silent: true })
  } finally {
    loadingAtoms.value = false
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

const generateAtoms = async () => {
  if (!activeSourceFile.value?.id) return
  if (atoms.value.length > 0) {
    try {
      await ElMessageBox.confirm(
        `当前文件已有 ${atoms.value.length} 条原子。继续后会追加生成并二审，不会覆盖或清空已有原子。`,
        '追加生成 / 二审',
        {
          confirmButtonText: '继续追加',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
    } catch {
      return
    }
  }
  generatingAtoms.value = true
  try {
    await generateKnowledgeAtomsAPI(activeSourceFile.value.id)
    ElMessage.success('原子生成作业已创建，完成后会提示本次生成结果')
    await loadJobs({ silent: true })
    startPolling()
  } finally {
    generatingAtoms.value = false
  }
}

const retryJob = async (file) => {
  const job = jobFor(file)
  if (!canRetryJob(job)) return
  retryingJobId.value = job.id
  try {
    await retryAppJobAPI(job.id)
    ElMessage.success('作业已重新投递')
    await loadJobs({ silent: true })
    startPolling()
  } finally {
    retryingJobId.value = null
  }
}

const acceptPatch = async (atom) => {
  await acceptKnowledgeAtomPatchAPI(atom.id)
  ElMessage.success('建议补丁已应用')
  await loadAtoms()
}

const publishAtom = async (atom) => {
  await publishKnowledgeAtomAPI(atom.id)
  ElMessage.success('原子已发布，向量同步状态已更新')
  await loadAtoms()
}

const publishAllAtoms = async () => {
  if (!activeSourceFile.value?.id || publishableAtomCount.value === 0) return
  try {
    await ElMessageBox.confirm(
      `确认发布当前文件下 ${publishableAtomCount.value} 条二审通过的草稿原子？发布后会同步到向量索引。`,
      '一键发布',
      {
        confirmButtonText: '发布',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  publishingAtoms.value = true
  try {
    const result = await publishKnowledgeFileAtomsAPI(activeSourceFile.value.id)
    ElMessage.success(`已发布 ${result.published} 条，向量同步成功 ${result.synced} 条，跳过 ${result.skipped} 条`)
    await loadAtoms()
  } finally {
    publishingAtoms.value = false
  }
}

const openCreateAtom = () => {
  editingAtom.value = null
  resetAtomForm()
  atomDialogVisible.value = true
}

const openEditAtom = (atom) => {
  editingAtom.value = atom
  atomForm.subject = atom.subject || ''
  atomForm.category = atom.category || ''
  atomForm.difficulty = atom.difficulty || 'MEDIUM'
  atomForm.tagsText = parseJsonArray(atom.tagsJson).join(', ')
  atomForm.principles = atom.principles || ''
  atomForm.pitfalls = atom.pitfalls || ''
  atomForm.followUpText = parseJsonArray(atom.followUpPathsJson).join('\n')
  atomDialogVisible.value = true
}

const saveAtom = async () => {
  if (!atomForm.subject.trim() || !atomForm.principles.trim()) {
    ElMessage.warning('请填写考点和核心原理')
    return
  }
  const payload = atomFormPayload()
  savingAtom.value = true
  try {
    if (editingAtom.value) {
      await updateKnowledgeAtomAPI(editingAtom.value.id, payload)
      ElMessage.success('原子已保存')
    } else {
      await createManualKnowledgeAtomAPI(activeSourceFile.value.id, payload)
      ElMessage.success('原子已创建')
    }
    atomDialogVisible.value = false
    await loadAtoms()
  } finally {
    savingAtom.value = false
  }
}

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
    await loadWorkspace({ notifyGenerationCompletion: true })
    await loadAtoms({ silent: true })
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

const notifyCompletedGenerationJobs = (previousJobs, nextJobs) => {
  const previousById = new Map((previousJobs || []).map((job) => [job.id, job]))
  for (const job of nextJobs || []) {
    const previous = previousById.get(job.id)
    if (
      job.jobType === 'GENERATE_ATOMS'
      && job.status === 'COMPLETED'
      && previous
      && previous.status !== 'COMPLETED'
    ) {
      ElMessage.success(generationCompletionMessage(job))
    }
  }
}

const resetAtomForm = () => {
  atomForm.subject = ''
  atomForm.category = activePosition.value?.name || ''
  atomForm.difficulty = 'MEDIUM'
  atomForm.tagsText = ''
  atomForm.principles = ''
  atomForm.pitfalls = ''
  atomForm.followUpText = ''
}

const atomFormPayload = () => ({
  subject: atomForm.subject.trim(),
  category: atomForm.category.trim(),
  difficulty: atomForm.difficulty,
  tags: atomForm.tagsText.split(/[,，]/).map((item) => item.trim()).filter(Boolean),
  principles: atomForm.principles.trim(),
  pitfalls: atomForm.pitfalls.trim(),
  followUpPaths: atomForm.followUpText.split('\n').map((item) => item.trim()).filter(Boolean)
})

const parseJsonArray = (value) => {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

watch(activeSourceFile, async (file, previous) => {
  if (file?.id && file.id !== previous?.id) {
    activeSourceFileId.value = file.id
    await loadAtoms({ silent: true })
  }
})

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

  .atom-toolbar__actions .el-button {
    flex: 1;
  }

  .atom-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
