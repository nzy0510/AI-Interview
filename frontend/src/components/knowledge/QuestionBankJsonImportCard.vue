<template>
  <section class="json-import-card">
    <div>
      <h3>已有 JSON 导入包</h3>
      <p class="muted-text">选择受控导入包，先校验，再导入为草稿；不会自动发布。</p>
    </div>
    <input ref="jsonInput" class="visually-hidden" type="file" accept=".json,application/json" @change="handleJsonFile">
    <el-button plain :disabled="!canImport" @click="jsonInput?.click()">选择 JSON 导入包</el-button>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { parseImportPackageText } from '@/utils/knowledgeWorkspace'

defineProps({
  canImport: { type: Boolean, default: false }
})

const emit = defineEmits(['import-package'])
const jsonInput = ref(null)

const handleJsonFile = async (event) => {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  try {
    emit('import-package', parseImportPackageText(await file.text()), file.name)
  } catch (error) {
    ElMessage.error(error.message || '读取 JSON 导入包失败')
  }
}
</script>

<style scoped>
.json-import-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-2);
}

.json-import-card h3 {
  margin: 0;
  color: var(--app-text);
}

.muted-text {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 0.9rem;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}

@media (max-width: 720px) {
  .json-import-card {
    flex-direction: column;
  }

  .json-import-card .el-button {
    width: 100%;
  }
}
</style>
