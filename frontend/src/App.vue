<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '@/components/layout/AppShell.vue'

const route = useRoute()
const isLoginRoute = computed(() => route.path === '/login')
</script>

<template>
  <router-view v-if="isLoginRoute" />
  <AppShell v-else>
    <router-view />
  </AppShell>
</template>
