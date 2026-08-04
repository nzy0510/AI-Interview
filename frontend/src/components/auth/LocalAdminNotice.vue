<template>
  <aside class="local-admin-notice" aria-label="本地 Docker 默认账号">
    <div class="local-admin-notice__header">
      <div>
        <p class="local-admin-notice__eyebrow">本地 Docker 体验模式</p>
        <h2>使用默认管理员账号登录</h2>
      </div>
      <span class="local-admin-notice__badge">仅限本机</span>
    </div>

    <dl class="local-admin-notice__credentials">
      <div>
        <dt>用户名</dt>
        <dd><code>{{ credentials.username }}</code></dd>
      </div>
      <div>
        <dt>密码</dt>
        <dd><code>{{ credentials.password }}</code></dd>
      </div>
    </dl>

    <el-button
      type="primary"
      plain
      class="local-admin-notice__fill"
      @click="emit('fill', credentials)"
    >
      填入默认账号
    </el-button>

    <p class="local-admin-notice__hint">
      登录后请前往“大模型配置”，填写你自己的 DeepSeek API Key 后再开始使用 AI 功能。
    </p>
  </aside>
</template>

<script setup>
const emit = defineEmits(['fill'])

const credentials = Object.freeze({
  username: 'admin',
  password: 'admin123'
})
</script>

<style scoped>
.local-admin-notice {
  margin-bottom: 20px;
  padding: 16px;
  border: 1px solid #d8d6ed;
  border-radius: 16px;
  background: #f4f3fb;
}

.local-admin-notice__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.local-admin-notice__eyebrow {
  margin: 0 0 4px;
  color: #555399;
  font-size: 12px;
  font-weight: 700;
}

.local-admin-notice h2 {
  margin: 0;
  color: #24233d;
  font-size: 16px;
  line-height: 1.4;
}

.local-admin-notice__badge {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 999px;
  color: #3a388b;
  background: #ffffff;
  font-size: 11px;
  font-weight: 700;
}

.local-admin-notice__credentials {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 14px 0;
}

.local-admin-notice__credentials div {
  min-width: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: #ffffff;
}

.local-admin-notice__credentials dt {
  margin-bottom: 4px;
  color: #77758b;
  font-size: 11px;
}

.local-admin-notice__credentials dd {
  margin: 0;
}

.local-admin-notice__credentials code {
  color: #24233d;
  font-size: 14px;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.local-admin-notice__fill {
  width: 100%;
}

.local-admin-notice__hint {
  margin: 12px 0 0;
  color: #666477;
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 480px) {
  .local-admin-notice__header {
    display: block;
  }

  .local-admin-notice__badge {
    display: inline-block;
    margin-top: 8px;
  }

  .local-admin-notice__credentials {
    grid-template-columns: 1fr;
  }
}
</style>
