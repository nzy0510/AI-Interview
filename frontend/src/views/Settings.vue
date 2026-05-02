<template>
  <div class="settings-page">
    <header class="settings-header">
      <div class="brand-cluster">
        <el-button :icon="ArrowLeft" class="icon-button" circle @click="router.push('/')" />
        <div class="header-copy">
          <p class="eyebrow">Preferences</p>
          <h1 class="page-title">偏好中心</h1>
          <p class="page-subtitle">管理你的账号信息、面试默认设置与密码安全。</p>
        </div>
      </div>
    </header>

    <el-main class="page-body">
      <!-- Account Section -->
      <section class="surface-card section-shell">
        <div class="section-head">
          <div>
            <p class="section-kicker">Account</p>
            <h2 class="section-title">账号信息</h2>
          </div>
          <el-button type="primary" size="small" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
        </div>
        <div class="settings-grid">
          <div class="settings-block">
            <el-form label-position="top">
              <el-form-item label="用户名">
                <el-input :model-value="profile.username" disabled />
                <span class="field-hint">用户名为账户唯一标识，注册后不可修改</span>
              </el-form-item>
              <el-form-item label="展示昵称">
                <el-input v-model="profile.nickname" placeholder="你的展示名称" maxlength="20" />
              </el-form-item>
              <el-form-item label="联系邮箱">
                <el-input v-model="profile.email" placeholder="you@example.com" />
              </el-form-item>
            </el-form>
          </div>
          <div class="settings-block password-block">
            <h4 class="block-title">修改密码</h4>
            <el-form label-position="top">
              <el-form-item label="旧密码">
                <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="输入旧密码" />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少6位新密码" />
              </el-form-item>
              <el-form-item>
                <el-button type="warning" :loading="changingPwd" @click="changePassword">修改密码</el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </section>

      <!-- Interview Preferences -->
      <section class="surface-card section-shell">
        <div class="section-head">
          <div>
            <p class="section-kicker">Interview</p>
            <h2 class="section-title">面试默认偏好</h2>
            <p class="section-desc">设置后，面试准备页会自动读取这些默认值。</p>
          </div>
          <el-button type="primary" size="small" :loading="savingPref" @click="savePreference">保存偏好</el-button>
        </div>
        <div class="settings-grid three-col">
          <div class="settings-block">
            <div class="settings-label">默认模式</div>
            <el-radio-group v-model="pref.defaultMode">
              <el-radio-button label="text">文字模式</el-radio-button>
              <el-radio-button label="video">视频模式</el-radio-button>
            </el-radio-group>
          </div>
          <div class="settings-block">
            <div class="settings-label">默认岗位</div>
            <el-select v-model="pref.defaultRole" placeholder="选择默认岗位" clearable style="width:100%">
              <el-option v-for="r in roleOptions" :key="r" :label="r" :value="r" />
            </el-select>
          </div>
          <div class="settings-block">
            <div class="settings-label">难度倾向</div>
            <el-radio-group v-model="pref.difficultyLevel">
              <el-radio-button label="junior">应届/0-1年</el-radio-button>
              <el-radio-button label="mid">1-3年</el-radio-button>
              <el-radio-button label="senior">3-5年</el-radio-button>
              <el-radio-button label="principal">5年+</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </section>
    </el-main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getCurrentUserAPI, updateProfileAPI, changePasswordAPI, getPreferenceAPI, updatePreferenceAPI } from '@/api/user'

const router = useRouter()
const savingProfile = ref(false)
const savingPref = ref(false)
const changingPwd = ref(false)

const roleOptions = ['Java 后端开发', 'Web 前端开发', '测试开发', '算法工程师', '产品经理']

const profile = reactive({ username: '', nickname: '', email: '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '' })

const pref = reactive({
  defaultMode: 'text',
  defaultRole: '',
  difficultyLevel: 'mid'
})

const loadData = async () => {
  try {
    const user = await getCurrentUserAPI()
    if (user) {
      profile.username = user.username || ''
      profile.nickname = user.nickname || ''
      profile.email = user.email || ''
    }
  } catch { /* defaults ok */ }
  try {
    const p = await getPreferenceAPI()
    if (p) {
      pref.defaultMode = p.defaultMode || 'text'
      pref.defaultRole = p.defaultRole || ''
      pref.difficultyLevel = p.difficultyLevel || 'mid'
    }
  } catch { /* defaults ok */ }
}

const saveProfile = async () => {
  savingProfile.value = true
  try {
    await updateProfileAPI({ nickname: profile.nickname, email: profile.email })
    import('@/utils/auth').then(({ setNickname }) => setNickname(profile.nickname))
    ElMessage.success('资料已更新')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally { savingProfile.value = false }
}

const changePassword = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写旧密码和新密码')
    return
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少6位')
    return
  }
  changingPwd.value = true
  try {
    await changePasswordAPI(passwordForm)
    ElMessage.success('密码已修改，下次登录请使用新密码')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
  } catch (e) {
    ElMessage.error(e.message || '修改失败')
  } finally { changingPwd.value = false }
}

const savePreference = async () => {
  savingPref.value = true
  try {
    await updatePreferenceAPI({ ...pref })
    ElMessage.success('偏好已保存')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally { savingPref.value = false }
}

onMounted(loadData)
</script>

<style scoped>
.settings-page {
  min-height: 100vh;
  background: #f7f9fb;
  color: #191c1e;
}

.settings-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 18px 32px;
  background: rgba(247, 249, 251, 0.9);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(69, 70, 82, 0.08);
}

.brand-cluster { display: flex; align-items: center; gap: 16px; min-width: 0; }
.icon-button { flex: 0 0 auto; }
.header-copy { min-width: 0; }

.eyebrow, .section-kicker, .settings-label {
  margin: 0 0 4px;
  color: #3a388b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-title { margin: 0; font-size: 24px; line-height: 1.2; font-weight: 800; color: #191c1e; }
.page-subtitle, .section-desc { margin: 6px 0 0; color: #5a6678; font-size: 14px; line-height: 1.6; }

.page-body {
  max-width: 1280px;
  margin: 0 auto;
  padding: 28px 32px 40px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  box-sizing: border-box;
}

.surface-card {
  background: #fff;
  border: 1px solid rgba(69, 70, 82, 0.08);
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(25, 28, 30, 0.04);
}

.section-shell { padding: 24px; }

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.section-title { margin: 0; font-size: 20px; line-height: 1.25; font-weight: 800; color: #191c1e; }

.settings-grid { display: grid; grid-template-columns: 1.2fr 0.8fr; gap: 20px; }
.settings-grid.three-col { grid-template-columns: repeat(3, 1fr); }

.settings-block {
  padding: 18px;
  border-radius: 14px;
  background: #faf9f5;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.block-title { margin: 0 0 12px; font-size: 16px; color: #191c1e; }

:deep(.el-input.is-disabled .el-input__wrapper) { background: #fff; }

.field-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.4;
}

@media (max-width: 960px) {
  .settings-header { flex-direction: column; align-items: stretch; }
  .settings-grid, .settings-grid.three-col { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .settings-header, .page-body { padding-left: 16px; padding-right: 16px; }
}
</style>
