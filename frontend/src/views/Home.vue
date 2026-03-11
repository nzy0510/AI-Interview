<template>
  <div class="home-container">
    <el-container>
      <el-header>
        <div class="logo">AI 模拟面试平台</div>
        <div class="user-info">
          <el-button type="danger" size="small" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>
      <el-main>
        <el-card class="welcome-card">
          <h2>欢迎来到 AI 面试工坊！</h2>
          <p>请选择你要进行模拟面试的岗位：</p>
          <div class="role-selector">
            <el-button type="primary" size="large" @click="startInterview('Java后端开发')">Java后端开发</el-button>
            <el-button type="success" size="large" @click="startInterview('Web前端开发')">Web前端开发</el-button>
          </div>
        </el-card>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()

const handleLogout = () => {
  localStorage.removeItem('token')
  ElMessage.success('已退出登录')
  router.push('/login')
}

const startInterview = (role) => {
  router.push({ path: '/interview', query: { role } })
}
</script>

<style scoped>
.home-container {
  height: 100vh;
  background-color: #f5f7fa;
}

.el-header {
  background-color: #409EFF;
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
}

.logo {
  font-size: 20px;
  font-weight: bold;
}

.el-main {
  display: flex;
  justify-content: center;
  align-items: center;
}

.welcome-card {
  width: 600px;
  text-align: center;
  padding: 40px;
}

.role-selector {
  margin-top: 30px;
  display: flex;
  justify-content: center;
  gap: 20px;
}
</style>
