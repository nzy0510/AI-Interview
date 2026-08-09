<template>
  <div class="build-panel">
    <div class="build-head">
      <div>
        <p class="section-kicker">Question Bank Builder</p>
        <h2>文档入库流水线</h2>
        <p class="section-desc">系统按固定步骤解析、生成和质量监督；只有你点击终审后才会发布。</p>
      </div>
      <el-button :loading="loading" @click="$emit('refresh')">刷新任务</el-button>
    </div>

    <el-alert
      v-if="llmStatus.resolved && !llmStatus.hasActiveConfig"
      title="尚未配置可用的大模型"
      description="题库构建会把文档内容发送到你配置的第三方模型服务。请先配置并启用 Provider。"
      type="warning"
      show-icon
    >
      <template #default>
        <div class="llm-blocked-actions">
          <span>当前构建已阻断，避免使用系统密钥或未知模型。</span>
          <el-button type="primary" plain @click="$emit('configure-llm')">去配置</el-button>
        </div>
      </template>
    </el-alert>
    <el-alert v-else-if="!llmStatus.resolved" title="正在检测大模型配置" type="info" show-icon />

    <section class="upload-card" :class="{ 'is-blocked': !canBuild || !llmStatus.hasActiveConfig }">
      <div class="preflight-grid">
        <div><span>目标岗位</span><strong>{{ position?.name || '-' }}</strong></div>
        <div><span>Provider / Model</span><strong>{{ llmDisplay }}</strong></div>
        <div><span>文件 / 上传大小</span><strong>{{ fileList.length }} 个（{{ readableSize }}）；实际分块提交解析后返回</strong></div>
        <div><span>分块 / 调用规模</span><strong>提交解析后确定</strong></div>
      </div>

      <el-upload
        v-model:file-list="fileList"
        class="document-uploader"
        drag
        multiple
        :auto-upload="false"
        :show-file-list="true"
        accept=".pdf,.docx,.txt,.md,.markdown,application/pdf,text/plain,text/markdown,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        :disabled="!canBuild || !llmStatus.hasActiveConfig"
        @change="handleFileChange"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-title">拖入 PDF、DOCX、TXT 或 MD 文档</div>
        <div class="upload-hint">支持多文件；扫描型 PDF、图片和旧版 DOC 暂不生成，系统会提示原因。</div>
      </el-upload>

      <div class="build-options">
        <div class="category-option">
          <el-select v-model="categories" multiple filterable allow-create default-first-option clearable placeholder="生成分类提示（可选）">
            <el-option v-for="category in categoryOptions" :key="category" :label="category" :value="category" />
          </el-select>
          <small>用于提示模型如何归类候选，不是题库筛选条件；不选时使用“通用”。</small>
        </div>
        <el-button
          type="primary"
          :loading="actionLoading === 'start'"
          :disabled="!canBuild || !llmStatus.hasActiveConfig || !fileList.length"
          @click="submitBuild"
        >
          开始生成候选
        </el-button>
      </div>

      <p class="third-party-note">隐私提示：文档文本将发送至当前 Provider 进行“知识原子生成 + 质量监督”；实际调用规模由解析分块数与候选数共同决定。</p>
    </section>

    <section v-if="builds.length" class="build-list-card">
      <div class="section-head"><h3>构建批次</h3><span class="muted-text">失败任务可重试原任务，不会重复创建批次。</span></div>
      <button
        v-for="build in builds"
        :key="build.id"
        type="button"
        class="build-row"
        :class="{ 'is-active': build.id === activeBuild?.id }"
        @click="$emit('select-build', build.id)"
      >
        <span class="build-row__main"><strong>#{{ build.id }}</strong><span>{{ build.sourceFiles?.map((file) => file.originalFilename || file.name).join('、') || '未命名文档' }}</span></span>
        <span class="build-row__meta"><el-tag size="small" :type="statusType(build.status)" effect="plain">{{ build.stage ? stageLabel(build.stage) : statusLabel(build.status) }}</el-tag><span>{{ build.progress }}%</span></span>
      </button>
    </section>

    <section v-if="activeBuild" class="build-detail-card">
      <div class="section-head"><div><h3>批次 #{{ activeBuild.id }} 运行详情</h3><p class="muted-text">阶段：{{ stageLabel(activeBuild.stage) }}</p></div><div class="detail-actions"><el-button v-if="activeBuild.canRetry && activeBuild.jobId" type="warning" plain :loading="actionLoading === 'retry'" @click="$emit('retry')">重试原任务</el-button><el-button type="danger" plain :disabled="isBuildInProgress || isBuildRetained || actionLoading === 'delete'" :loading="actionLoading === 'delete'" :title="deleteHint" @click="$emit('delete')">删除批次</el-button></div></div>
      <el-progress :percentage="activeBuild.progress" :status="activeBuild.status === 'FAILED' ? 'exception' : undefined" />
      <div class="detail-grid"><span>解析文件 {{ activeBuild.fileCount || 0 }}</span><span>分块 {{ activeBuild.completedChunkCount || 0 }}/{{ activeBuild.chunkCount || '-' }}</span><span>候选 {{ activeBuild.candidateCount || 0 }}</span><span>监督通过 {{ activeBuild.autoPassCount || 0 }}</span><span>需人工 {{ activeBuild.needsHumanCount || 0 }}</span><span>自动排除 {{ activeBuild.autoRejectCount || 0 }}</span></div>
      <p v-if="activeBuild.errorMessage" class="error-text">{{ activeBuild.errorMessage }}</p>
      <p v-if="isBuildInProgress" class="muted-text delete-hint">构建运行中，暂不能删除；请等待完成或失败后再操作。</p>
      <p v-if="activeBuild.stage === 'READY_FOR_FINAL_REVIEW'" class="success-note">生成与监督已完成。请到“终审发布”只处理异常项，然后整批发布。</p>
      <p v-else-if="activeBuild.stage === 'PUBLISHED'" class="success-note">终审发布完成，知识原子和检索索引均已入库。</p>
    </section>

    <!-- 旧 JSON 导入入口暂时隐藏；保留组件调用，便于后续按需恢复。
    <QuestionBankJsonImportCard :can-import="canBuild" @import-package="(...args) => $emit('import-package', ...args)" />
    -->
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import { getQuestionBankBuildStageLabel, getQuestionBankBuildStatusLabel, getQuestionBankBuildStatusType, isQuestionBankBuildInProgress } from '@/utils/knowledgeWorkspace'
// 旧 JSON 导入入口暂时隐藏；保留 import 位置，恢复模板入口时一并启用。
// import QuestionBankJsonImportCard from '@/components/knowledge/QuestionBankJsonImportCard.vue'

const props = defineProps({
  position: { type: Object, default: null },
  llmStatus: { type: Object, default: () => ({ resolved: false, hasActiveConfig: false }) },
  canBuild: { type: Boolean, default: false },
  builds: { type: Array, default: () => [] },
  activeBuild: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  actionLoading: { type: String, default: '' },
  categoryOptions: { type: Array, default: () => [] }
})

const emit = defineEmits(['refresh', 'configure-llm', 'start', 'select-build', 'retry', 'delete', 'import-package'])
const fileList = ref([])
const categories = ref([])
const llmDisplay = computed(() => props.llmStatus.hasActiveConfig
  ? `${props.llmStatus.activeProvider || props.llmStatus.activeDisplayName || '-'} / ${props.llmStatus.activeModelName || '-'}`
  : '未配置')
const totalBytes = computed(() => fileList.value.reduce((sum, item) => sum + Number(item.size || item.raw?.size || 0), 0))
const readableSize = computed(() => totalBytes.value > 1024 * 1024 ? `${(totalBytes.value / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.ceil(totalBytes.value / 1024))} KB`)
const statusLabel = (status) => getQuestionBankBuildStatusLabel(status)
const statusType = (status) => getQuestionBankBuildStatusType(status)
const stageLabel = (stage) => getQuestionBankBuildStageLabel(stage)
const isBuildInProgress = computed(() => isQuestionBankBuildInProgress(props.activeBuild))
const isBuildRetained = computed(() => Boolean(props.activeBuild?.finalAtomIds?.length))
const deleteHint = computed(() => isBuildInProgress.value
  ? '构建运行中，完成或失败后才可删除'
  : isBuildRetained.value ? '已进入终审发布的批次需保留来源记录' : '')

const handleFileChange = (file, files) => {
  fileList.value = files.filter((item) => !item.status || item.status !== 'fail')
}

const submitBuild = () => {
  const files = fileList.value.map((item) => item.raw || item).filter(Boolean)
  emit('start', files, categories.value)
}

</script>

<style scoped>
.build-panel {
  display: grid;
  gap: 16px;
  padding-top: 20px;
}

.build-head,
.section-head,
.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.build-head h2,
.section-head h3 {
  margin: 0;
  color: var(--app-text);
}

.section-kicker {
  margin: 0 0 4px;
  color: var(--app-text-muted);
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
}

.section-desc,
.muted-text,
.third-party-note,
.upload-hint,
.success-note {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 0.9rem;
}

.upload-card,
.build-list-card,
.build-detail-card,
.upload-card.is-blocked {
  opacity: 0.72;
}

.llm-blocked-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.preflight-grid,
.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.preflight-grid > div,
.detail-grid > span {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.preflight-grid span,
.detail-grid span {
  color: var(--app-text-muted);
  font-size: 0.8rem;
}

.preflight-grid strong {
  overflow-wrap: anywhere;
}

.preflight-grid small {
  color: var(--app-text-muted);
  font-weight: 400;
}

.document-uploader {
  margin-top: 4px;
}

.upload-icon {
  margin-bottom: 8px;
  color: var(--app-primary);
  font-size: 2rem;
}

.upload-title {
  color: var(--app-text);
  font-weight: 700;
}

.build-options {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
}

.category-option {
  flex: 1;
  min-width: 0;
}

.category-option .el-select {
  width: 100%;
}

.category-option small {
  display: block;
  margin-top: 6px;
  color: var(--app-text-muted);
  font-size: 0.78rem;
}

.third-party-note {
  margin-top: 12px;
}

.build-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 10px;
  border: 0;
  border-top: 1px solid var(--app-border);
  background: transparent;
  color: var(--app-text);
  text-align: left;
  cursor: pointer;
}

.build-row:first-of-type {
  margin-top: 8px;
}

.build-row.is-active {
  background: rgba(58, 56, 139, 0.07);
}

.build-row__main,
.build-row__meta {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.build-row__main span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.build-row__meta {
  flex: 0 0 auto;
}

.detail-grid {
  margin: 14px 0 0;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.error-text {
  margin: 12px 0 0;
  color: var(--app-danger);
  overflow-wrap: anywhere;
}

.success-note {
  color: var(--app-success, #16835b);
}

@media (max-width: 720px) {
  .build-head,
  .section-head {
    flex-direction: column;
  }

  .preflight-grid,
  .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .build-options {
    align-items: stretch;
    flex-direction: column;
  }

  .build-options .el-button {
    width: 100%;
  }
}

@media (max-width: 460px) {
  .preflight-grid,
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .build-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
