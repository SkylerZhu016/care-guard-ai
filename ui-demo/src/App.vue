<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, Avatar, Calendar, DataAnalysis, FirstAidKit, Lock, UserFilled } from '@element-plus/icons-vue'
import AppShell, { type NavItem } from './components/AppShell.vue'
import SecondaryPanel from './components/SecondaryPanel.vue'
import AdminDemo from './views/AdminDemo.vue'
import ClinicianDemo from './views/ClinicianDemo.vue'
import FollowupDemo from './views/FollowupDemo.vue'
import PatientDemo from './views/PatientDemo.vue'
import { roles } from './mock'
import type { Role } from './types'

const started = ref(false)
const role = ref<Role>('patient')
const activeNav = ref('intake')
const mobileNavOpen = ref(false)

const roleIcons = { patient: UserFilled, clinician: Avatar, followup: Calendar, admin: DataAnalysis }
const navByRole: Record<Role, NavItem[]> = {
  patient: [
    { id: 'intake', label: '新建预问诊', icon: 'FirstAidKit' },
    { id: 'records', label: '问诊记录', icon: 'FolderOpened', badge: '3' },
    { id: 'patient-tasks', label: '我的随访', icon: 'Bell', badge: '2' },
    { id: 'profile', label: '健康资料', icon: 'User' },
  ],
  clinician: [
    { id: 'review', label: '审核队列', icon: 'Operation', badge: '12' },
    { id: 'reviewed', label: '已审核记录', icon: 'Document' },
    { id: 'plans', label: '随访计划', icon: 'Calendar' },
    { id: 'evidence', label: '证据查询', icon: 'FolderOpened' },
  ],
  followup: [
    { id: 'tasks', label: '今日任务', icon: 'Tickets', badge: '8' },
    { id: 'calendar', label: '任务日程', icon: 'Calendar' },
    { id: 'completed', label: '完成记录', icon: 'Document' },
  ],
  admin: [
    { id: 'overview', label: '运行概览', icon: 'HomeFilled' },
    { id: 'alerts', label: '安全告警', icon: 'Bell', badge: '3' },
    { id: 'knowledge', label: '知识来源', icon: 'FolderOpened' },
    { id: 'audit', label: '审计记录', icon: 'Lock' },
    { id: 'settings', label: '系统配置', icon: 'Setting' },
  ],
}
const primaryByRole: Record<Role, string> = { patient: 'intake', clinician: 'review', followup: 'tasks', admin: 'overview' }
const currentNav = computed(() => navByRole[role.value])
const currentNavLabel = computed(() => currentNav.value.find((item) => item.id === activeNav.value)?.label || '')
const isPrimary = computed(() => activeNav.value === primaryByRole[role.value])

function enter(nextRole: Role) { role.value = nextRole; activeNav.value = primaryByRole[nextRole]; started.value = true; window.scrollTo({ top: 0 }) }
function switchRole(nextRole: Role) { role.value = nextRole; activeNav.value = primaryByRole[nextRole]; mobileNavOpen.value = false }
function navigate(id: string) { activeNav.value = id; mobileNavOpen.value = false; window.scrollTo({ top: 0, behavior: 'smooth' }) }
</script>

<template>
  <section v-if="!started" class="demo-gate">
    <header class="gate-topbar"><div class="brand"><span class="brand-icon"><FirstAidKit /></span><span><strong>守望基层医疗</strong><small>独立静态 UI Demo</small></span></div><span class="demo-badge"><i /> 不连接真实后端</span></header>
    <main class="gate-main">
      <section class="gate-copy"><span class="eyebrow">DESIGN SANDBOX · 2026</span><h1>规范采集信息，<br><em>支持高效协同。</em></h1><p>这是与现有项目完全分离的前端设计环境。选择一个角色，预览重新组织后的页面层级、交互流程与响应式体验。</p><div class="gate-points"><span><Lock />模拟数据</span><span><FirstAidKit />安全优先</span><span><DataAnalysis />四端统一</span></div></section>
      <section class="role-launcher" aria-labelledby="launcher-title"><div><span class="step-label">快速预览</span><h2 id="launcher-title">选择演示角色</h2><p>无需登录，所有交互仅保存在当前页面。</p></div><div class="launcher-grid"><button v-for="item in roles" :key="item.id" type="button" @click="enter(item.id)"><span class="launcher-icon"><component :is="roleIcons[item.id]" /></span><span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span><ArrowRight /></button></div><footer><Lock /><span>请勿输入真实姓名、电话、身份证号或真实病历资料</span></footer></section>
    </main>
    <footer class="gate-footer"><strong>设计目标</strong><span>页面层级更清楚 · 关键状态更醒目 · 桌面与移动端一致</span></footer>
  </section>

  <AppShell v-else :role="role" :nav-items="currentNav" :active-nav="activeNav" :mobile-nav-open="mobileNavOpen" @nav="navigate" @role="switchRole" @toggle-menu="mobileNavOpen = !mobileNavOpen" @exit="started = false">
    <template v-if="isPrimary">
      <PatientDemo v-if="role === 'patient'" />
      <ClinicianDemo v-else-if="role === 'clinician'" />
      <FollowupDemo v-else-if="role === 'followup'" />
      <AdminDemo v-else />
    </template>
    <SecondaryPanel v-else :role="role" :title="currentNavLabel" />
  </AppShell>
</template>
