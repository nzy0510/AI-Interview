<template>
  <div class="auth-shell">
    <div class="auth-frame">
      <section class="auth-hero">
        <div class="brand-lockup">
          <div class="brand-mark">
            <el-icon><Operation /></el-icon>
          </div>
          <div>
            <p class="brand-kicker">Architectural Intelligence</p>
            <h1>面试不是筛选，而是架构对话。</h1>
          </div>
        </div>
        <p class="hero-copy">
          将简历、岗位画像与实时追问整合为一条清晰的面试路径，让每一次练习都更接近真实的技术评审。
        </p>

        <div class="hero-cards">
          <article class="hero-card">
            <div class="hero-card-icon primary">
              <el-icon><Message /></el-icon>
            </div>
            <div>
              <h3>统一入口</h3>
              <p>登录、注册与找回密码在同一界面完成，流程更顺手。</p>
            </div>
          </article>
          <article class="hero-card">
            <div class="hero-card-icon tertiary">
              <el-icon><DataLine /></el-icon>
            </div>
            <div>
              <h3>面试画像</h3>
              <p>简历解析、岗位选择与能力提问保持同一上下文。</p>
            </div>
          </article>
          <article class="hero-card">
            <div class="hero-card-icon neutral">
              <el-icon><Lock /></el-icon>
            </div>
            <div>
              <h3>安全收敛</h3>
              <p>验证码、密码重置与跳转逻辑保留原有业务闭环。</p>
            </div>
          </article>
        </div>
      </section>

      <section class="auth-panel">
        <div class="panel-shell">
          <div class="panel-topline">
            <span class="panel-eyebrow">面试导师</span>
            <span class="panel-meta">AI 面试能力提升平台</span>
          </div>

          <el-tabs v-model="activeTab" class="auth-tabs">
            <el-tab-pane label="登录" name="login">
              <el-form :model="loginForm" :rules="loginRules" ref="loginFormRef" label-width="0" class="auth-form">
                <el-form-item prop="username">
                  <el-input
                    v-model="loginForm.username"
                    placeholder="请输入邮箱或用户名"
                    prefix-icon="Message"
                    size="large"
                  />
                </el-form-item>
                <el-form-item prop="password">
                  <el-input
                    v-model="loginForm.password"
                    type="password"
                    placeholder="请输入密码"
                    prefix-icon="Lock"
                    show-password
                    size="large"
                  />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" class="submit-btn" :loading="loading" @click="handleLogin">登录</el-button>
                </el-form-item>
                <div class="extra-links">
                  <el-link type="primary" @click="activeTab = 'forgot'">忘记密码？</el-link>
                </div>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="注册" name="register">
              <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef" label-width="0" class="auth-form">
                <el-form-item prop="username">
                  <el-input
                    v-model="registerForm.username"
                    placeholder="请输入用户名"
                    prefix-icon="User"
                    size="large"
                  />
                </el-form-item>
                <el-form-item prop="email">
                  <el-input
                    v-model="registerForm.email"
                    placeholder="请输入邮箱"
                    prefix-icon="Message"
                    size="large"
                  >
                    <template #append>
                      <el-button :disabled="regCooldown > 0" :loading="sendingRegCode" @click="sendRegCode">
                        {{ regCooldown > 0 ? `${regCooldown}s` : '发送验证码' }}
                      </el-button>
                    </template>
                  </el-input>
                </el-form-item>
                <el-form-item prop="code">
                  <el-input
                    v-model="registerForm.code"
                    placeholder="请输入6位验证码"
                    prefix-icon="Key"
                    maxlength="6"
                    size="large"
                  />
                </el-form-item>
                <el-form-item prop="password">
                  <el-input
                    v-model="registerForm.password"
                    type="password"
                    placeholder="请设置密码（至少6位）"
                    prefix-icon="Lock"
                    show-password
                    size="large"
                  />
                </el-form-item>
                <el-form-item>
                  <el-button type="success" class="submit-btn" :loading="loading" @click="handleRegister">注册账号</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="找回密码" name="forgot">
              <el-form :model="forgotForm" :rules="forgotRules" ref="forgotFormRef" label-width="0" class="auth-form">
                <el-form-item prop="email">
                  <el-input
                    v-model="forgotForm.email"
                    placeholder="请输入注册时的邮箱"
                    prefix-icon="Message"
                    size="large"
                  >
                    <template #append>
                      <el-button :disabled="forgotCooldown > 0" :loading="sendingForgotCode" @click="sendForgotCode">
                        {{ forgotCooldown > 0 ? `${forgotCooldown}s` : '发送验证码' }}
                      </el-button>
                    </template>
                  </el-input>
                </el-form-item>
                <el-form-item prop="code">
                  <el-input
                    v-model="forgotForm.code"
                    placeholder="请输入6位验证码"
                    prefix-icon="Key"
                    maxlength="6"
                    size="large"
                  />
                </el-form-item>
                <el-form-item prop="newPassword">
                  <el-input
                    v-model="forgotForm.newPassword"
                    type="password"
                    placeholder="请设置新密码（至少6位）"
                    prefix-icon="Lock"
                    show-password
                    size="large"
                  />
                </el-form-item>
                <el-form-item>
                  <el-button type="warning" class="submit-btn" :loading="loading" @click="handleResetPassword">重置密码</el-button>
                </el-form-item>
                <div class="extra-links">
                  <el-link type="primary" @click="activeTab = 'login'">返回登录</el-link>
                </div>
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Operation, Message, DataLine, Lock } from '@element-plus/icons-vue'
import { loginAPI, registerAPI, sendCodeAPI, forgotPasswordAPI, resetPasswordAPI } from '@/api/user'

const router = useRouter()
const activeTab = ref('login')
const loading = ref(false)

const loginFormRef = ref(null)
const registerFormRef = ref(null)
const forgotFormRef = ref(null)

const loginForm = ref({ username: '', password: '' })
const registerForm = ref({ username: '', password: '', email: '', code: '' })
const forgotForm = ref({ email: '', code: '', newPassword: '' })

// 验证码冷却
const regCooldown = ref(0)
const forgotCooldown = ref(0)
const sendingRegCode = ref(false)
const sendingForgotCode = ref(false)

let regTimer = null
let forgotTimer = null

const startCooldown = (type) => {
  const seconds = 60
  if (type === 'reg') {
    regCooldown.value = seconds
    regTimer = setInterval(() => {
      regCooldown.value--
      if (regCooldown.value <= 0) clearInterval(regTimer)
    }, 1000)
  } else {
    forgotCooldown.value = seconds
    forgotTimer = setInterval(() => {
      forgotCooldown.value--
      if (forgotCooldown.value <= 0) clearInterval(forgotTimer)
    }, 1000)
  }
}

const loginRules = {
  username: [{ required: true, message: '请输入邮箱', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度 3-20 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为6位数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ]
}

const forgotRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为6位数字', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请设置新密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ]
}

const sendRegCode = async () => {
  if (!registerForm.value.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  sendingRegCode.value = true
  try {
    await sendCodeAPI({ email: registerForm.value.email, purpose: '注册' })
    ElMessage.success('验证码已发送，请查收邮箱')
    startCooldown('reg')
  } catch (e) {
    // interceptor handles error
  } finally {
    sendingRegCode.value = false
  }
}

const sendForgotCode = async () => {
  if (!forgotForm.value.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  sendingForgotCode.value = true
  try {
    await forgotPasswordAPI({ email: forgotForm.value.email })
    ElMessage.success('重置验证码已发送，请查收邮箱')
    startCooldown('forgot')
  } catch (e) {
    // interceptor handles error
  } finally {
    sendingForgotCode.value = false
  }
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const token = await loginAPI(loginForm.value)
        localStorage.setItem('token', token)
        ElMessage.success('登录成功！')
        router.push('/')
      } catch (error) {
        // Error handled in interceptor
      } finally {
        loading.value = false
      }
    }
  })
}

const handleRegister = async () => {
  if (!registerFormRef.value) return
  await registerFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await registerAPI(registerForm.value)
        ElMessage.success('注册成功，请切换至登录页面登录')
        activeTab.value = 'login'
        registerForm.value = { username: '', password: '', email: '', code: '' }
      } catch (error) {
        // Error handled in interceptor
      } finally {
        loading.value = false
      }
    }
  })
}

const handleResetPassword = async () => {
  if (!forgotFormRef.value) return
  await forgotFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await resetPasswordAPI(forgotForm.value)
        ElMessage.success('密码重置成功，请使用新密码登录')
        activeTab.value = 'login'
        forgotForm.value = { email: '', code: '', newPassword: '' }
      } catch (error) {
        // Error handled in interceptor
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.auth-shell {
  min-height: 100vh;
  padding: 28px;
  background:
    linear-gradient(180deg, rgba(247, 249, 251, 0.82), rgba(247, 249, 251, 0.92)),
    radial-gradient(circle at top left, rgba(58, 56, 139, 0.12), transparent 36%),
    radial-gradient(circle at bottom right, rgba(108, 159, 255, 0.14), transparent 30%),
    #f7f9fb;
}

.auth-frame {
  min-height: calc(100vh - 56px);
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(380px, 460px);
  gap: 32px;
  align-items: center;
  max-width: 1240px;
  margin: 0 auto;
}

.auth-hero {
  display: flex;
  flex-direction: column;
  gap: 28px;
  padding: 32px 12px;
}

.brand-lockup {
  display: flex;
  align-items: center;
  gap: 16px;
}

.brand-mark {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  color: #ffffff;
  background: linear-gradient(135deg, #3a388b 0%, #5250a4 100%);
  box-shadow: 0 16px 36px rgba(58, 56, 139, 0.18);
}

.brand-mark :deep(.el-icon) {
  font-size: 24px;
}

.brand-kicker {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #5e5d59;
}

.brand-lockup h1 {
  margin: 0;
  max-width: 720px;
  font-size: clamp(34px, 4vw, 58px);
  line-height: 1.08;
  color: #141413;
  letter-spacing: 0;
}

.hero-copy {
  max-width: 640px;
  margin: 0;
  font-size: 18px;
  line-height: 1.65;
  color: #5e5d59;
}

.hero-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.hero-card {
  background: rgba(250, 249, 245, 0.92);
  border: 1px solid #e8e6dc;
  border-radius: 16px;
  padding: 18px;
  box-shadow: 0 12px 34px rgba(20, 20, 19, 0.05);
}

.hero-card-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  margin-bottom: 14px;
}

.hero-card-icon.primary {
  color: #ffffff;
  background: #3a388b;
}

.hero-card-icon.tertiary {
  color: #ffffff;
  background: #004c45;
}

.hero-card-icon.neutral {
  color: #141413;
  background: #e8e6dc;
}

.hero-card h3 {
  margin: 0 0 8px;
  font-size: 18px;
  line-height: 1.2;
  color: #141413;
}

.hero-card p {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: #5e5d59;
}

.auth-panel {
  display: flex;
  justify-content: flex-end;
}

.panel-shell {
  width: 100%;
  max-width: 440px;
  background: rgba(250, 249, 245, 0.96);
  border: 1px solid #e8e6dc;
  border-radius: 24px;
  padding: 28px;
  box-shadow: 0 24px 80px rgba(20, 20, 19, 0.12);
  backdrop-filter: blur(14px);
}

.panel-topline {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 20px;
}

.panel-eyebrow {
  font-size: 14px;
  font-weight: 700;
  color: #141413;
}

.panel-meta {
  font-size: 12px;
  color: #87867f;
}

.auth-tabs :deep(.el-tabs__header) {
  margin: 0 0 24px;
}

.auth-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: #e8e6dc;
}

.auth-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  color: #5e5d59;
}

.auth-tabs :deep(.el-tabs__item.is-active) {
  color: #3a388b;
}

.auth-form {
  padding-top: 4px;
}

.auth-form :deep(.el-input__wrapper) {
  border-radius: 12px;
  box-shadow: 0 0 0 1px #dedc01 inset;
  background: #ffffff;
}

.auth-form :deep(.el-input-group__append) {
  background: #e8e6dc;
  color: #4d4c48;
  border-left: 1px solid #d1cfc5;
}

.submit-btn {
  width: 100%;
  height: 46px;
  border-radius: 12px;
  font-weight: 700;
}

.extra-links {
  text-align: right;
  margin-top: -10px;
}

@media (max-width: 1024px) {
  .auth-frame {
    grid-template-columns: 1fr;
  }

  .auth-panel {
    justify-content: stretch;
  }

  .panel-shell {
    max-width: none;
  }
}

@media (max-width: 768px) {
  .auth-shell {
    padding: 16px;
  }

  .auth-hero {
    padding: 12px 0 0;
  }

  .brand-lockup {
    align-items: flex-start;
  }

  .hero-cards {
    grid-template-columns: 1fr;
  }

  .panel-shell {
    padding: 20px;
    border-radius: 20px;
  }
}
</style>
