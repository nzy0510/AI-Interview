<template>
  <div class="atom-panel">
    <div class="atom-head">
      <div><p class="section-kicker">Knowledge Atoms</p><h2>题库原子</h2><p class="section-desc">查看生命周期、审核、向量和来源；发布只对通过审核的草稿开放。</p></div>
      <el-button :loading="loading" @click="$emit('refresh')">刷新原子</el-button>
    </div>

    <div class="atom-filters">
      <el-input :model-value="filters.keyword" clearable placeholder="关键词 / Atom ID / 内容" @update:model-value="updateFilter('keyword', $event)" />
      <el-input :model-value="filters.category" clearable placeholder="分类" @update:model-value="updateFilter('category', $event)" />
      <el-select :model-value="filters.status" clearable placeholder="生命周期" @update:model-value="updateFilter('status', $event)"><el-option label="草稿" value="DRAFT" /><el-option label="已发布" value="PUBLISHED" /><el-option label="已归档" value="ARCHIVED" /></el-select>
      <el-button type="primary" plain @click="$emit('search')">查询</el-button>
    </div>

    <div class="atom-actions">
      <span class="muted-text">已选 {{ selectedIds.length }} 条；可发布 {{ eligibleSelectedCount }} 条</span>
      <el-button :disabled="!canPublish || !eligibleSelectedCount" :loading="actionLoading === 'publish'" @click="$emit('publish', eligibleSelectedIds)">发布所选</el-button>
      <el-button :disabled="!canReindex || !selectedIds.length" :loading="actionLoading === 'reindex'" @click="$emit('reindex', selectedIds)">重建索引</el-button>
      <el-button type="warning" plain :disabled="!canPublish || !total" :loading="actionLoading === 'publishAll'" @click="$emit('publish-all')">一键发布全部草稿</el-button>
      <el-button type="danger" plain :disabled="!canArchive || !selectedIds.length" :loading="actionLoading === 'archive'" @click="$emit('archive', selectedIds)">归档所选</el-button>
      <el-button type="danger" plain :disabled="!canArchive || !total" :loading="actionLoading === 'archiveAll'" @click="$emit('archive-all')">一键归档全部</el-button>
    </div>

    <el-table :data="atoms" class="atom-table" empty-text="当前知识库还没有原子" @selection-change="handleSelection">
      <el-table-column type="selection" width="44" />
      <el-table-column prop="atomId" label="Atom ID" min-width="170" />
      <el-table-column prop="subject" label="考点" min-width="170" />
      <el-table-column prop="category" label="分类" min-width="100" />
      <el-table-column prop="difficulty" label="难度" width="84" />
      <el-table-column label="生命周期" width="100"><template #default="{ row }"><el-tag size="small" :type="row.status === 'ARCHIVED' ? 'info' : row.status === 'PUBLISHED' ? 'success' : 'warning'" effect="plain">{{ lifecycleLabel(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="审核" width="100"><template #default="{ row }"><el-tag size="small" :type="row.reviewStatus === 'PASS' ? 'success' : 'warning'" effect="plain">{{ reviewLabel(row.reviewStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="向量" width="100"><template #default="{ row }">{{ row.vectorStatus || '-' }}</template></el-table-column>
      <el-table-column prop="sourceRef" label="来源" min-width="150" />
      <el-table-column v-if="canEdit" label="操作" width="84" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="$emit('edit', row.id)">编辑</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-if="total > page.size" v-model:current-page="page.page" v-model:page-size="page.size" class="atom-pagination" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50]" :total="total" @current-change="$emit('page-change')" @size-change="$emit('page-change')" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { isQuestionBankAtomPublishEligible } from '@/utils/knowledgeWorkspace'

const props = defineProps({
  atoms: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  filters: { type: Object, default: () => ({ keyword: '', category: '', status: '' }) },
  page: { type: Object, default: () => ({ page: 1, size: 20 }) },
  selectedIds: { type: Array, default: () => [] },
  canEdit: { type: Boolean, default: false },
  canPublish: { type: Boolean, default: false },
  canReindex: { type: Boolean, default: false },
  canArchive: { type: Boolean, default: false },
  actionLoading: { type: String, default: '' }
})

const emit = defineEmits(['refresh', 'search', 'update-filter', 'selection-change', 'edit', 'publish', 'publish-all', 'reindex', 'archive', 'archive-all', 'page-change'])
const selectedAtoms = computed(() => props.atoms.filter((atom) => props.selectedIds.includes(atom.atomId)))
const eligibleSelectedIds = computed(() => selectedAtoms.value.filter(isQuestionBankAtomPublishEligible).map((atom) => atom.atomId))
const eligibleSelectedCount = computed(() => eligibleSelectedIds.value.length)
const updateFilter = (key, value) => emit('update-filter', key, value)
const handleSelection = (selection) => emit('selection-change', selection.map((atom) => atom.atomId).filter(Boolean))
const lifecycleLabel = (status) => ({ DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' }[status] || status || '-')
const reviewLabel = (status) => ({ PASS: '已通过', NEEDS_REVIEW: '待复核', REJECT: '已拒绝' }[status] || status || '-')
</script>

<style scoped>
.atom-panel {
  display: grid;
  gap: 16px;
  padding-top: 20px;
}

.atom-head,
.atom-actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.atom-head h2 {
  margin: 0;
  color: var(--app-text);
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

.atom-filters {
  display: grid;
  grid-template-columns: minmax(180px, 1.5fr) minmax(130px, 0.8fr) minmax(130px, 0.8fr) auto;
  gap: 10px;
}

.atom-actions {
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.atom-actions .muted-text {
  margin: 0 auto 0 0;
}

.atom-table {
  width: 100%;
}

.atom-pagination {
  justify-content: flex-end;
}

@media (max-width: 720px) {
  .atom-head,
  .atom-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .atom-filters {
    grid-template-columns: 1fr;
  }

  .atom-actions .muted-text {
    margin: 0;
  }

  .atom-actions .el-button {
    width: 100%;
    margin-left: 0;
  }

  .atom-table {
    overflow-x: auto;
  }
}
</style>
