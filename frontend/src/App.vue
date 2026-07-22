<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useSessionStore } from './stores/session'
const session=useSessionStore(); const router=useRouter()
function logout(){session.logout();router.push('/login')}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand"><span class="brand-mark">十</span><div><strong>守望基层医疗</strong><small>安全型预问诊与随访 · 教学平台</small></div></div>
      <div class="top-actions"><span class="simulation-badge">● 教学模拟 · FAKE PROVIDER</span><template v-if="session.user"><span>{{ session.user.displayName }}</span><button class="text-button" @click="logout">退出</button></template></div>
    </header>
    <main><router-view /></main>
    <footer>本系统仅用于教学模拟，不构成诊断、处方或医疗建议 · 所有病例均为合成数据</footer>
  </div>
</template>

