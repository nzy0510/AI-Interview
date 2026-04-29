<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Document,
  Monitor,
  Notebook,
  Setting,
  VideoCamera,
  UserFilled,
  MagicStick
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const navItems = [
  { path: '/', label: 'Dashboard', icon: Monitor },
  { path: '/interview/setup', label: 'Interview Setup', icon: MagicStick },
  { path: '/interview', label: 'Text Interview', icon: Notebook },
  { path: '/video-interview', label: 'Video Interview', icon: VideoCamera },
  { path: '/resume', label: 'Resume', icon: UserFilled },
  { path: '/history', label: 'Reports', icon: Document },
  { path: '/settings', label: 'Settings', icon: Setting }
]

const currentTitle = computed(() => route.meta?.title || 'Workspace')

const isActive = (path) => {
  return route.path === path
}

const navigate = (path) => {
  if (route.path !== path) {
    router.push(path)
  }
}
</script>

<template>
  <div class="app-shell">
    <aside class="app-shell__sidebar">
      <button class="app-shell__brand" type="button" @click="navigate('/')">
        <div class="app-shell__brand-mark">I</div>
        <div class="app-shell__brand-copy">
          <div class="app-shell__brand-name">InterWise</div>
          <div class="app-shell__brand-subtitle">Editorial interview workspace</div>
        </div>
      </button>

      <nav class="app-shell__nav" aria-label="Primary">
        <button
          v-for="item in navItems"
          :key="item.path"
          class="app-shell__nav-item"
          :class="{ 'is-active': isActive(item.path) }"
          type="button"
          :aria-current="isActive(item.path) ? 'page' : undefined"
          @click="navigate(item.path)"
        >
          <el-icon class="app-shell__nav-icon">
            <component :is="item.icon" />
          </el-icon>
          <span class="app-shell__nav-label">{{ item.label }}</span>
        </button>
      </nav>

      <div class="app-shell__sidebar-footer">
        <div class="app-shell__status-dot" />
        <span>Stitch-aligned layout</span>
      </div>
    </aside>

    <div class="app-shell__main">
      <header class="app-shell__topbar">
        <div class="app-shell__title-block">
          <p class="app-shell__eyebrow">Workspace</p>
          <h1 class="app-shell__title">{{ currentTitle }}</h1>
        </div>

        <div class="app-shell__actions">
          <el-button type="primary" :icon="MagicStick" @click="navigate('/interview/setup')">
            Start
          </el-button>
          <el-button :icon="Setting" @click="navigate('/settings')">Settings</el-button>
        </div>
      </header>

      <main class="app-shell__content">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  background: var(--app-bg);
}

.app-shell__sidebar {
  display: flex;
  flex-direction: column;
  gap: 28px;
  padding: 28px 22px;
  border-right: 1px solid var(--app-border);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.78), rgba(255, 255, 255, 0.94)),
    var(--app-surface);
  backdrop-filter: blur(18px);
}

.app-shell__brand {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
  user-select: none;
}

.app-shell__brand-mark {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--app-primary), var(--app-primary-container));
  color: #fff;
  font-weight: 700;
  box-shadow: 0 12px 22px rgba(58, 56, 139, 0.22);
}

.app-shell__brand-copy {
  min-width: 0;
}

.app-shell__brand-name {
  font-size: 1rem;
  font-weight: 700;
  line-height: 1.2;
}

.app-shell__brand-subtitle {
  margin-top: 2px;
  color: var(--app-text-muted);
  font-size: 0.88rem;
}

.app-shell__nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.app-shell__nav-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--app-text-weak);
  text-align: left;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.app-shell__nav-item:hover {
  background: rgba(58, 56, 139, 0.05);
  border-color: rgba(58, 56, 139, 0.08);
  color: var(--app-text);
}

.app-shell__nav-item.is-active {
  background: rgba(58, 56, 139, 0.1);
  border-color: rgba(58, 56, 139, 0.18);
  color: var(--app-primary);
}

.app-shell__nav-icon {
  flex: 0 0 auto;
  font-size: 1rem;
}

.app-shell__nav-label {
  min-width: 0;
  font-weight: 600;
}

.app-shell__sidebar-footer {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  padding-top: 20px;
  color: var(--app-text-muted);
  font-size: 0.88rem;
}

.app-shell__status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--app-success);
  box-shadow: 0 0 0 4px rgba(47, 158, 106, 0.12);
}

.app-shell__main {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.app-shell__topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 28px;
  border-bottom: 1px solid rgba(216, 224, 235, 0.72);
  background: rgba(244, 246, 251, 0.86);
  backdrop-filter: blur(18px);
}

.app-shell__title-block {
  min-width: 0;
}

.app-shell__eyebrow {
  margin: 0 0 4px;
  color: var(--app-text-muted);
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0;
  text-transform: uppercase;
}

.app-shell__title {
  margin: 0;
  color: var(--app-text);
  font-size: 1.4rem;
  line-height: 1.2;
}

.app-shell__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.app-shell__content {
  flex: 1;
  min-width: 0;
  padding: 28px;
}

@media (max-width: 1024px) {
  .app-shell {
    grid-template-columns: 1fr;
  }

  .app-shell__sidebar {
    position: sticky;
    top: 0;
    z-index: 12;
    gap: 16px;
    padding: 18px 16px 14px;
    border-right: 0;
    border-bottom: 1px solid var(--app-border);
  }

  .app-shell__nav {
    flex-direction: row;
    overflow-x: auto;
    padding-bottom: 2px;
  }

  .app-shell__nav-item {
    width: auto;
    flex: 0 0 auto;
    white-space: nowrap;
  }

  .app-shell__sidebar-footer {
    display: none;
  }

  .app-shell__topbar {
    position: static;
    align-items: flex-start;
    padding: 22px 16px 18px;
  }

  .app-shell__content {
    padding: 18px 16px 24px;
  }
}

@media (max-width: 640px) {
  .app-shell__topbar {
    flex-direction: column;
  }

  .app-shell__actions {
    width: 100%;
  }

  .app-shell__actions .el-button {
    flex: 1;
  }
}
</style>
