<script setup>
defineProps({
  record: { type: String, required: true },
})
</script>

<template>
  <footer class="icp-footer" aria-label="网站备案信息">
    <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">
      {{ record }}
    </a>
  </footer>
</template>

<style scoped>
.icp-footer {
  flex: 0 0 auto;
  padding: 8px 16px calc(8px + env(safe-area-inset-bottom, 0px));
  border-top: 1px solid var(--app-border);
  background: var(--app-bg);
  color: var(--app-text-weak);
  font-size: 12px;
  line-height: 20px;
  text-align: center;
}

.icp-footer a {
  color: inherit;
  overflow-wrap: anywhere;
}

.icp-footer a:hover,
.icp-footer a:focus-visible {
  color: var(--app-primary);
  text-decoration: underline;
}
</style>
