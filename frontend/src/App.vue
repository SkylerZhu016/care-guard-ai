<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { FirstAidKit, SwitchButton, UserFilled, WarningFilled } from '@element-plus/icons-vue'
import { useSessionStore } from './stores/session'

const session=useSessionStore(),router=useRouter()
const roleLabels:Record<string,string>={SIMULATED_PATIENT:'模拟患者',CLINICIAN:'医务审核',FOLLOWUP_STAFF:'随访执行',ADMIN:'安全治理'}
const roleLabel=computed(()=>roleLabels[session.user?.role||'']||'')
function logout(){session.logout();router.push('/login')}
function goHome(){router.push(session.home())}
</script>

<template>
  <div class="app-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <header class="topbar">
      <button class="brand" type="button" aria-label="返回角色工作台" @click="goHome">
        <span class="brand-mark"><FirstAidKit /></span>
        <span class="brand-copy"><strong>守望基层医疗</strong><small>安全型预问诊与随访 · 教学平台</small></span>
      </button>
      <div class="top-actions">
        <span class="simulation-badge"><WarningFilled /> 教学模拟 · FAKE</span>
        <template v-if="session.user">
          <span class="user-context"><UserFilled /><span><small>{{roleLabel}}</small><strong>{{ session.user.displayName }}</strong></span></span>
          <button class="icon-button" type="button" aria-label="退出当前账号" @click="logout"><SwitchButton /></button>
        </template>
      </div>
    </header>
    <div v-if="session.user" class="scope-strip" role="note">
      <strong>当前自动规则范围：</strong>成人教学病例中的胸痛、呼吸困难、晕厥与意识异常。其他症状不应被解释为已完成自动安全评估。
    </div>
    <main id="main-content"><router-view /></main>
    <footer><strong>仅用于教学模拟</strong><span>不构成诊断、处方或医疗建议 · 所有病例均为合成数据 · 最终结果必须人工审核</span></footer>
  </div>
</template>
