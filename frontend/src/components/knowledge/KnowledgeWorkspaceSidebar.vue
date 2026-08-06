<template>
  <section class="sidebar surface-card" :class="{ 'is-collapsed': collapsed }">
    <div class="sidebar-head">
      <div>
        <p class="section-kicker">Positions</p>
        <h2 class="section-title">岗位空间</h2>
      </div>
      <el-tag effect="plain">{{ positions.length }} 个岗位</el-tag>
    </div>

    <el-empty v-if="!loading && !positions.length" :description="emptyDescription">
      <el-button v-if="canCreate" type="primary" @click="$emit('create')">创建私有岗位</el-button>
    </el-empty>

    <div v-else class="position-list">
      <button
        v-for="position in positions"
        :key="position.id"
        type="button"
        class="position-item"
        :class="{ 'is-active': position.id === activeId, 'is-archived': position.status === 'ARCHIVED' }"
        @click="$emit('select', position)"
      >
        <span class="position-item__title">{{ position.name }}</span>
        <span class="position-item__meta">
          <el-tag size="small" :type="position.scope === 'PUBLIC' ? 'info' : 'success'" effect="plain">
            {{ position.scope === 'PUBLIC' ? '公共岗位' : '我的岗位' }}
          </el-tag>
          <el-tag size="small" :type="position.status === 'ARCHIVED' ? 'info' : 'success'" effect="plain">
            {{ position.status === 'ARCHIVED' ? '已归档' : '可用' }}
          </el-tag>
        </span>
      </button>
    </div>
  </section>
</template>

<script setup>
defineProps({
  positions: { type: Array, default: () => [] },
  activeId: { type: [Number, String], default: null },
  loading: { type: Boolean, default: false },
  canCreate: { type: Boolean, default: false },
  collapsed: { type: Boolean, default: false },
  emptyDescription: { type: String, default: '暂无可用岗位' }
})

defineEmits(['select', 'create'])
</script>

<style scoped>
.sidebar {
  min-width: 0;
  padding: 20px;
}

.sidebar-head,
.position-item__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.sidebar-head {
  margin-bottom: 18px;
}

.section-kicker {
  margin: 0 0 4px;
  color: var(--app-text-muted);
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
}

.section-title {
  margin: 0;
  color: var(--app-text);
  font-size: 1.15rem;
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
  overflow-wrap: anywhere;
}

.position-item__meta {
  justify-content: flex-start;
  flex-wrap: wrap;
}

@media (max-width: 1180px) {
  .sidebar.is-collapsed {
    display: none;
  }
}
</style>
