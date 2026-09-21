<script setup>
import { computed } from 'vue'
import publicSecurityIcon from '@/assets/head-logo.png'

const props = defineProps({
  record: { type: String, required: true },
  publicSecurityRecord: { type: String, default: '' },
})

const publicSecurityUrl = computed(() =>
  `https://beian.mps.gov.cn/#/query/webSearch?code=${props.publicSecurityRecord.replace(/\D/g, '')}`,
)
</script>

<template>
  <footer class="icp-footer" aria-label="网站备案信息">
    <a v-if="record" href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">
      {{ record }}
    </a>
    <a v-if="publicSecurityRecord" :href="publicSecurityUrl" target="_blank" rel="noopener noreferrer">
      <img :src="publicSecurityIcon" width="20" height="22" alt="" />
      <span>{{ publicSecurityRecord }}</span>
    </a>
  </footer>
</template>

<style scoped>
.icp-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 4px 16px;
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
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  color: inherit;
  overflow-wrap: anywhere;
}

.icp-footer img {
  flex-shrink: 0;
}

.icp-footer a:hover,
.icp-footer a:focus-visible {
  color: var(--app-primary);
  text-decoration: underline;
}
</style>
