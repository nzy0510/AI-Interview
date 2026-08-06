<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑知识原子"
    width="min(92vw, 720px)"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form label-position="top">
      <div class="form-grid">
        <el-form-item label="考点"><el-input v-model="form.subject" maxlength="160" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="form.category" maxlength="80" /></el-form-item>
        <el-form-item label="难度"><el-select v-model="form.difficulty"><el-option label="初级" value="junior" /><el-option label="中级" value="mid" /><el-option label="高级" value="senior" /><el-option label="专家" value="principal" /></el-select></el-form-item>
        <el-form-item label="标签（逗号分隔）"><el-input v-model="form.tagsText" /></el-form-item>
      </div>
      <el-form-item label="核心原理"><el-input v-model="form.principles" type="textarea" :rows="5" /></el-form-item>
      <el-form-item label="常见误区"><el-input v-model="form.pitfalls" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="追问路径（每行一条）"><el-input v-model="form.followUpPathsText" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存为已审核草稿</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  atom: { type: Object, default: null },
  saving: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'save'])
const form = reactive({ subject: '', category: '', difficulty: 'mid', tagsText: '', principles: '', pitfalls: '', followUpPathsText: '' })

const difficultyAliases = {
  easy: 'junior',
  medium: 'mid',
  hard: 'senior',
  简单: 'junior',
  中等: 'mid',
  困难: 'senior',
  p6: 'senior',
  p7: 'principal'
}

const normalizeDifficulty = (value) => {
  const normalized = String(value || 'mid').trim().toLowerCase()
  return difficultyAliases[normalized] || (['junior', 'mid', 'senior', 'principal'].includes(normalized) ? normalized : 'mid')
}

watch(() => props.atom, (atom) => {
  form.subject = atom?.subject || ''
  form.category = atom?.category || ''
  form.difficulty = normalizeDifficulty(atom?.difficulty)
  form.tagsText = Array.isArray(atom?.tags) ? atom.tags.join(', ') : ''
  form.principles = atom?.principles || ''
  form.pitfalls = atom?.pitfalls || ''
  form.followUpPathsText = Array.isArray(atom?.followUpPaths) ? atom.followUpPaths.join('\n') : ''
}, { immediate: true })

const splitList = (value, pattern) => String(value || '').split(pattern).map((item) => item.trim()).filter(Boolean)
const submit = () => {
  if (!form.subject.trim()) return ElMessage.warning('请填写考点')
  if (!form.category.trim()) return ElMessage.warning('请填写分类')
  if (!form.principles.trim()) return ElMessage.warning('请填写核心原理')
  const followUpPaths = splitList(form.followUpPathsText, /\n/)
  if (followUpPaths.length < 2) return ElMessage.warning('请至少填写两条追问路径')
  emit('save', {
    subject: form.subject.trim(),
    category: form.category.trim(),
    difficulty: form.difficulty,
    tags: splitList(form.tagsText, /[,，\n]/),
    principles: form.principles.trim(),
    pitfalls: form.pitfalls.trim(),
    followUpPaths
  })
}
</script>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
