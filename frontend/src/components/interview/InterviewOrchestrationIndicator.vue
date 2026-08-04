<script setup>
import { computed } from 'vue'
import {
  getFriendlyToolLabels,
  getOrchestrationActionLabel,
  getOrchestrationModePresentation
} from '@/utils/interviewOrchestration'

const props = defineProps({
  decision: {
    type: Object,
    required: true
  },
  dark: {
    type: Boolean,
    default: false
  }
})

const mode = computed(() => getOrchestrationModePresentation(props.decision.mode))
const summary = computed(() => props.decision.summary || getOrchestrationActionLabel(props.decision.action))
const toolLabels = computed(() => getFriendlyToolLabels(props.decision.tools))
</script>

<template>
  <aside
    class="orchestration-indicator"
    :class="[{ 'is-dark': dark }, `is-${decision.mode.toLowerCase().replace('_', '-')}`]"
    role="status"
    aria-live="polite"
    aria-label="当前面试策略"
  >
    <el-tag :type="mode.type" size="small" effect="dark" round>{{ mode.label }}</el-tag>
    <span class="orchestration-indicator__summary">{{ summary }}</span>
    <span v-if="toolLabels.length" class="orchestration-indicator__tools">
      <span class="orchestration-indicator__tool-prefix">参考</span>
      <el-tag
        v-for="tool in toolLabels"
        :key="tool"
        size="small"
        effect="plain"
        round
      >
        {{ tool }}
      </el-tag>
    </span>
  </aside>
</template>

<style scoped>
.orchestration-indicator {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border: 1px solid rgba(58, 56, 139, 0.12);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  color: #35404d;
  box-shadow: 0 6px 18px rgba(23, 26, 31, 0.04);
}

.orchestration-indicator.is-dark {
  border-color: rgba(255, 255, 255, 0.10);
  background: rgba(14, 17, 25, 0.72);
  color: #eef2f7;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18);
}

.orchestration-indicator__summary {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: inherit;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.orchestration-indicator__tools {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
}

.orchestration-indicator__tool-prefix {
  color: #7b8491;
  font-size: 12px;
}

.is-dark .orchestration-indicator__tool-prefix {
  color: rgba(230, 235, 242, 0.68);
}

@media (max-width: 720px) {
  .orchestration-indicator {
    align-items: flex-start;
    flex-wrap: wrap;
    gap: 7px 8px;
    padding: 8px 10px;
  }

  .orchestration-indicator__summary {
    flex-basis: calc(100% - 82px);
    white-space: normal;
  }

  .orchestration-indicator__tools {
    width: 100%;
    flex-wrap: wrap;
    padding-left: 2px;
  }
}
</style>
