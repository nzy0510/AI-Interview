<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const title = computed(() => route.meta?.title || route.name || 'Module')

const backHome = () => {
  router.push('/')
}
</script>

<template>
  <section class="route-placeholder">
    <p class="route-placeholder__eyebrow">Module scaffold</p>
    <h2 class="route-placeholder__title">{{ title }}</h2>
    <p class="route-placeholder__copy">
      This route is wired into the shell and ready for the next worker to fill in.
    </p>
    <el-button type="primary" @click="backHome">Go to Dashboard</el-button>
  </section>
</template>

<style scoped>
.route-placeholder {
  min-height: calc(100vh - 180px);
  display: grid;
  align-content: center;
  justify-items: start;
  gap: 14px;
  padding: 28px;
  border: 1px solid var(--app-border);
  border-radius: 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(248, 250, 252, 0.96)),
    var(--app-surface);
}

.route-placeholder__eyebrow {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 0.82rem;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.route-placeholder__title {
  margin: 0;
  font-size: 1.4rem;
  line-height: 1.2;
}

.route-placeholder__copy {
  margin: 0;
  max-width: 52ch;
  color: var(--app-text-weak);
}
</style>
