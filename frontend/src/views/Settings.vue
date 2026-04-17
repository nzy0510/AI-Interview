<template>
  <div class="settings-page">
    <header class="settings-header">
      <div class="brand-cluster">
        <el-button :icon="ArrowLeft" class="icon-button" circle @click="router.push('/history')" />
        <div class="header-copy">
          <p class="eyebrow">Architectural Intelligence</p>
          <h1 class="page-title">偏好中心</h1>
          <p class="page-subtitle">这里先保留前端占位，后续接入保存逻辑和用户设置接口。</p>
        </div>
      </div>

      <div class="header-actions">
        <el-tag effect="plain" type="info" class="status-pill">待接入</el-tag>
        <el-button plain class="secondary-cta" @click="router.push('/history')">返回报告中心</el-button>
        <el-button type="primary" class="primary-cta" disabled>保存设置</el-button>
      </div>
    </header>

    <el-main class="page-body">
      <section class="surface-card hero-shell">
        <div class="hero-copy">
          <p class="section-kicker">Settings Preview</p>
          <h2 class="hero-title">把账号、面试偏好和隐私策略放到同一处管理</h2>
          <p class="hero-desc">现在先给 UI 结构和模块位置，动作入口保持禁用，等后续再接真实接口。</p>
        </div>
        <div class="hero-side">
          <div class="recent-box">
            <div class="recent-label">当前状态</div>
            <div class="recent-main">
              <strong>3</strong>
              <span>个模块预留</span>
            </div>
            <div class="recent-sub">
              <span>账号偏好 / 面试偏好 / 通知隐私</span>
              <span>全部暂不提交到后端</span>
            </div>
          </div>
        </div>
      </section>

      <section
        v-for="section in reportCenter.settingsSections"
        :key="section.key"
        class="surface-card section-shell"
      >
        <div class="section-head">
          <div>
            <p class="section-kicker">{{ section.kicker }}</p>
            <h2 class="section-title">{{ section.title }}</h2>
            <p class="section-desc">{{ section.description }}</p>
          </div>
          <el-tag effect="plain" type="info">{{ section.badge }}</el-tag>
        </div>

        <div v-if="section.key === 'account'" class="settings-grid">
          <div class="settings-block">
            <el-form label-position="top">
              <el-form-item label="展示名称">
                <el-input model-value="InterWise 用户" disabled />
              </el-form-item>
              <el-form-item label="联系邮箱">
                <el-input model-value="user@example.com" disabled />
              </el-form-item>
              <el-form-item label="时区">
                <el-select model-value="Asia/Shanghai" disabled>
                  <el-option label="Asia/Shanghai" value="Asia/Shanghai" />
                </el-select>
              </el-form-item>
            </el-form>
          </div>

          <div class="settings-block">
            <div class="settings-row">
              <div>
                <strong>个性化欢迎语</strong>
                <p>根据历史记录生成开场提示。</p>
              </div>
              <el-switch disabled :model-value="true" />
            </div>
            <div class="settings-row">
              <div>
                <strong>最近活跃展示</strong>
                <p>在首页显示最近一次训练时间。</p>
              </div>
              <el-switch disabled :model-value="false" />
            </div>
            <el-button plain disabled>更换头像</el-button>
          </div>
        </div>

        <div v-else-if="section.key === 'interview'" class="settings-grid">
          <div class="settings-block">
            <div class="settings-label">默认模式</div>
            <el-radio-group model-value="text" disabled>
              <el-radio-button label="text">文字模式</el-radio-button>
              <el-radio-button label="video">视频模式</el-radio-button>
            </el-radio-group>
            <div class="settings-note">用于后续快速进入默认的面试状态。</div>
          </div>
          <div class="settings-block">
            <div class="settings-label">难度倾向</div>
            <el-slider :model-value="68" disabled show-input />
            <div class="settings-note">后续可映射为题目深度和追问频率。</div>
          </div>
          <div class="settings-block">
            <div class="settings-label">重点方向</div>
            <div class="chip-row">
              <el-tag effect="plain">Java 后端</el-tag>
              <el-tag effect="plain">Vue 3</el-tag>
              <el-tag effect="plain">系统设计</el-tag>
            </div>
            <el-button plain disabled>编辑重点方向</el-button>
          </div>
        </div>

        <div v-else class="settings-grid">
          <div class="settings-block">
            <div class="settings-row">
              <div>
                <strong>消息提醒</strong>
                <p>新报告、复盘完成与训练建议。</p>
              </div>
              <el-switch disabled :model-value="true" />
            </div>
            <div class="settings-row">
              <div>
                <strong>摘要邮件</strong>
                <p>每周生成一次进度摘要。</p>
              </div>
              <el-switch disabled :model-value="false" />
            </div>
          </div>
          <div class="settings-block">
            <div class="settings-label">数据保留时长</div>
            <el-select model-value="90d" disabled>
              <el-option label="90 天" value="90d" />
            </el-select>
            <div class="settings-note">后续接入真实保存策略后再开放修改。</div>
          </div>
          <div class="settings-block">
            <div class="settings-label">隐私与导出</div>
            <el-button plain disabled>导出个人数据</el-button>
            <el-button plain disabled>清理本地缓存</el-button>
          </div>
        </div>
      </section>
    </el-main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { reportCenterConfig } from '@/mock/reports'

const router = useRouter()
const reportCenter = reportCenterConfig
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

.brand-cluster {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.header-copy {
  min-width: 0;
}

.eyebrow,
.section-kicker,
.settings-label {
  margin: 0 0 4px;
  color: #3a388b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.page-title {
  margin: 0;
  font-size: 24px;
  line-height: 1.2;
  font-weight: 800;
  color: #191c1e;
}

.page-subtitle,
.section-desc,
.hero-desc,
.settings-note,
.settings-row p {
  margin: 6px 0 0;
  color: #5a6678;
  font-size: 14px;
  line-height: 1.6;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 0 0 auto;
}

.status-pill {
  border-color: rgba(58, 56, 139, 0.12);
  color: #3a388b;
  background: #eef0ff;
}

.primary-cta,
.secondary-cta {
  border-radius: 12px;
}

.secondary-cta {
  border-color: rgba(58, 56, 139, 0.2);
  color: #3a388b;
  background: #f4f3ff;
}

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
  background: #ffffff;
  border: 1px solid rgba(69, 70, 82, 0.08);
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(25, 28, 30, 0.04);
}

.hero-shell,
.section-shell {
  padding: 24px;
}

.hero-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(280px, 0.75fr);
  gap: 20px;
}

.hero-title {
  margin: 0;
  font-size: 28px;
  line-height: 1.15;
  font-weight: 800;
  color: #191c1e;
}

.hero-side {
  min-width: 0;
}

.recent-box {
  padding: 18px;
  border-radius: 14px;
  background: #faf9f5;
  border: 1px solid rgba(69, 70, 82, 0.08);
}

.recent-label {
  font-size: 12px;
  color: #5a6678;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 8px;
}

.recent-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.recent-main strong {
  font-size: 38px;
  line-height: 1;
  color: #3a388b;
}

.recent-main span {
  color: #5a6678;
  font-size: 14px;
}

.recent-sub {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 10px;
  color: #5a6678;
  font-size: 13px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.section-title {
  margin: 0;
  font-size: 20px;
  line-height: 1.25;
  font-weight: 800;
  color: #191c1e;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.settings-block {
  padding: 18px;
  border-radius: 14px;
  background: #faf9f5;
  border: 1px solid rgba(69, 70, 82, 0.08);
  display: grid;
  gap: 14px;
}

.settings-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.settings-row strong {
  display: block;
  font-size: 15px;
  color: #191c1e;
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

:deep(.el-dialog) {
  border-radius: 16px;
}

:deep(.el-input.is-disabled .el-input__wrapper),
:deep(.el-select.is-disabled .el-select__wrapper),
:deep(.el-slider.is-disabled) {
  background: #ffffff;
}

@media (max-width: 960px) {
  .settings-header,
  .section-head,
  .hero-shell {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .settings-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .settings-header,
  .page-body {
    padding-left: 16px;
    padding-right: 16px;
  }

  .page-body {
    padding-top: 20px;
  }

  .hero-shell,
  .section-shell {
    padding: 18px 16px;
  }

  .page-title,
  .hero-title {
    font-size: 20px;
  }
}
</style>
