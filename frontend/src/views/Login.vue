<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2 class="title">AI 面试能力提升平台</h2>
      
      <el-tabs v-model="activeTab">
        <!-- Login Tab -->
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" :rules="loginRules" ref="loginFormRef" label-width="0">
            <el-form-item prop="username">
              <el-input 
                v-model="loginForm.username" 
                placeholder="请输入邮箱或用户名" 
                prefix-icon="Message" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input 
                v-model="loginForm.password" 
                type="password" 
                placeholder="请输入密码" 
                prefix-icon="Lock" 
                show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" class="submit-btn" :loading="loading" @click="handleLogin">登录</el-button>
            </el-form-item>
            <div class="extra-links">
              <el-link type="primary" @click="activeTab = 'forgot'">忘记密码？</el-link>
            </div>
          </el-form>
        </el-tab-pane>

        <!-- Register Tab -->
        <el-tab-pane label="注册" name="register">
          <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef" label-width="0">
            <el-form-item prop="username">
              <el-input 
                v-model="registerForm.username" 
                placeholder="请输入用户名" 
                prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="email">
              <el-input 
                v-model="registerForm.email" 
                placeholder="请输入邮箱" 
                prefix-icon="Message">
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
                maxlength="6" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input 
                v-model="registerForm.password" 
                type="password" 
                placeholder="请设置密码（至少6位）" 
                prefix-icon="Lock" 
                show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="success" class="submit-btn" :loading="loading" @click="handleRegister">注册账号</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- Forgot Password Tab -->
        <el-tab-pane label="找回密码" name="forgot">
          <el-form :model="forgotForm" :rules="forgotRules" ref="forgotFormRef" label-width="0">
            <el-form-item prop="email">
              <el-input 
                v-model="forgotForm.email" 
                placeholder="请输入注册时的邮箱" 
                prefix-icon="Message">
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
                maxlength="6" />
            </el-form-item>
            <el-form-item prop="newPassword">
              <el-input 
                v-model="forgotForm.newPassword" 
                type="password" 
                placeholder="请设置新密码（至少6位）" 
                prefix-icon="Lock" 
                show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="warning" class="submit-btn" :loading="loading" @click="handleResetPassword">重置密码</el-button>
            </el-form-item>
            <div class="extra-links">
              <el-link type="primary" @click="activeTab = 'login'">← 返回登录</el-link>
            </div>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #e0f2fe 0%, #ccfbf1 50%, #fef3c7 100%);
}

.login-card {
  width: 440px;
  padding: 20px;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.title {
  text-align: center;
  margin-bottom: 24px;
  color: #303133;
}

.submit-btn {
  width: 100%;
}

.extra-links {
  text-align: center;
  margin-top: -8px;
}
</style>
