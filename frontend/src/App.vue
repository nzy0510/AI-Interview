<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '@/components/layout/AppShell.vue'
import IcpFooter from '@/components/layout/IcpFooter.vue'

const route = useRoute()
const isLoginRoute = computed(() => route.path === '/login')
const icpRecord = (import.meta.env.VITE_ICP_RECORD || '').trim()
</script>

<template>
  <div class="app-root" :class="{ 'app-root--with-footer': icpRecord }">
    <div class="app-root__page">
      <router-view v-if="isLoginRoute" />
      <AppShell v-else>
        <router-view />
      </AppShell>
    </div>
    <IcpFooter v-if="icpRecord" :record="icpRecord" />
  </div>
</template>

<style scoped>
.app-root--with-footer {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
}

.app-root--with-footer .app-root__page {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: auto;
}

.app-root--with-footer :deep(.app-shell) {
  height: 100%;
}

.app-root--with-footer :deep(.auth-shell) {
  display: grid;
  min-height: 100%;
}

.app-root--with-footer :deep(.auth-frame) {
  width: 100%;
  min-height: 0;
}
</style>
