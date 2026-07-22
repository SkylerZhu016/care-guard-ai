<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { FirstAidKit, SwitchButton, UserFilled } from '@element-plus/icons-vue'
import { useSessionStore } from './stores/session'

const session=useSessionStore(),router=useRouter()
const roleLabels:Record<string,string>={PATIENT:'患者',CLINICIAN:'医务审核',FOLLOWUP_STAFF:'随访执行',ADMIN:'安全治理'}
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
        <span class="brand-copy"><strong>守望基层医疗</strong><small>预问诊与随访信息服务</small></span>
      </button>
      <div class="top-actions">
        <template v-if="session.user">
          <span class="user-context"><UserFilled /><span><small>{{roleLabel}}</small><strong>{{ session.user.displayName }}</strong></span></span>
          <button class="icon-button" type="button" aria-label="退出当前账号" @click="logout"><SwitchButton /></button>
        </template>
      </div>
    </header>
    <div v-if="session.user" class="scope-strip" role="note">
      <strong>安全提示：</strong>系统可记录常见不适；确定性自动规则仅覆盖部分胸痛、呼吸困难、晕厥与意识异常组合。未覆盖内容会转交人工复核。
    </div>
    <main id="main-content"><router-view /></main>
    <footer><strong>信息整理服务</strong><span>不构成诊断、处方或医疗建议 · 自动结果必须人工审核 · 敏感字段写库前脱敏</span></footer>
  </div>
</template>
