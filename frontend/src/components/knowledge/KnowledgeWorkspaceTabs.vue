<template>
  <nav class="workspace-tabs" aria-label="题库工作区导航">
    <button
      v-for="tab in visibleTabs"
      :key="tab.key"
      type="button"
      class="workspace-tab"
      :class="{ 'is-active': tab.key === modelValue }"
      :disabled="tab.privateOnly && !canBuild"
      @click="$emit('update:modelValue', tab.key)"
    >
      <span>{{ tab.label }}</span>
      <el-badge v-if="tab.key === 'candidates' && candidateCount > 0" :value="candidateCount" :max="99" />
    </button>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { QUESTION_BANK_TABS } from '@/utils/knowledgeWorkspace'

const props = defineProps({
  modelValue: { type: String, default: 'overview' },
  canBuild: { type: Boolean, default: false },
  candidateCount: { type: Number, default: 0 },
  isPublic: { type: Boolean, default: false }
})

defineEmits(['update:modelValue'])

const visibleTabs = computed(() => QUESTION_BANK_TABS.filter((tab) => !tab.privateOnly || (!props.isPublic && props.canBuild)))
</script>

<style scoped>
.workspace-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-2);
  overflow-x: auto;
}

.workspace-tab {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 0 14px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--app-text-muted);
  cursor: pointer;
  white-space: nowrap;
}

.workspace-tab:hover,
.workspace-tab.is-active {
  background: var(--app-surface);
  color: var(--app-text);
}

.workspace-tab.is-active {
  box-shadow: var(--app-shadow-sm);
  font-weight: 700;
}
</style>
