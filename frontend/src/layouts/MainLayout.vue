<template>
  <el-container style="height: 100vh">
    <el-aside width="220px" class="aside">
      <div class="logo">预问诊与随访平台</div>
      <el-menu :default-active="$route.path" router :unique-opened="true" background-color="#001529" text-color="#a6adb4" active-text-color="#fff">
        <template v-for="m in menus" :key="m.path">
          <el-menu-item v-if="!m.children" :index="m.path">
            <el-icon><component :is="m.icon" /></el-icon><span>{{ m.title }}</span>
          </el-menu-item>
          <el-sub-menu v-else :index="m.path">
            <template #title><el-icon><component :is="m.icon" /></el-icon><span>{{ m.title }}</span></template>
            <el-menu-item v-for="c in m.children" :key="c.path" :index="c.path">{{ c.title }}</el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-disclaimer">教学用辅助系统 · AI 内容不能替代医生诊断</div>
        <el-dropdown @command="onCommand">
          <span class="user-chip">
            <el-icon><User /></el-icon>
            {{ auth.user?.realName || auth.user?.username }}
            <el-tag v-for="r in auth.roles" :key="r" size="small" style="margin-left:4px">{{ roleName(r) }}</el-tag>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="account">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main style="padding: 0; overflow-y: auto">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const allMenus = [
  { path: '/p/home', title: '首页', icon: 'HomeFilled', roles: ['PATIENT'] },
  { path: '/p/archive', title: '模拟患者档案', icon: 'FolderOpened', roles: ['PATIENT'] },
  { path: '/p/visit-new', title: '新建预问诊', icon: 'EditPen', roles: ['PATIENT'] },
  { path: '/p/visits', title: '我的预问诊', icon: 'Document', roles: ['PATIENT'] },
  { path: '/p/followups', title: '我的随访', icon: 'Calendar', roles: ['PATIENT'] },
  { path: '/d/workbench', title: '医务工作台', icon: 'Monitor', roles: ['DOCTOR'] },
  { path: '/d/patients', title: '患者档案查询', icon: 'Search', roles: ['DOCTOR', 'FOLLOWUP'] },
  { path: '/f/trends', title: '随访趋势', icon: 'TrendCharts', roles: ['DOCTOR', 'FOLLOWUP'] },
  { path: '/f/board', title: '随访任务看板', icon: 'Tickets', roles: ['FOLLOWUP'] },
  {
    path: '/a', title: '系统管理', icon: 'Setting', roles: ['ADMIN'],
    children: [
      { path: '/a/users', title: '用户管理' }, { path: '/a/roles', title: '角色权限' },
      { path: '/a/guidelines', title: '指南与知识库' }, { path: '/a/rules', title: '规则管理' },
      { path: '/a/models', title: '模型管理' }, { path: '/a/prompts', title: 'Prompt 版本' },
      { path: '/a/agent-runs', title: 'Agent 运行记录' }, { path: '/a/alerts', title: '安全告警' },
      { path: '/a/audit-logs', title: '审计日志' }, { path: '/a/configs', title: '系统配置' }
    ]
  },
  { path: '/a/agent-runs', title: 'Agent 运行记录', icon: 'Cpu', roles: ['DOCTOR'] }
]

const menus = computed(() =>
  allMenus
    .filter((m) => m.roles.some((r) => auth.roles.includes(r as never)))
    .map((m) => (m.children ? { ...m, children: m.children } : m))
)

function roleName(r: string) {
  return { PATIENT: '患者', DOCTOR: '医务人员', FOLLOWUP: '随访人员', ADMIN: '管理员' }[r] || r
}

async function onCommand(cmd: string) {
  if (cmd === 'logout') {
    await auth.logout()
    router.push('/login')
  } else if (cmd === 'account') {
    router.push('/account')
  }
}
</script>

<style scoped>
.aside { background: #001529; }
.logo { color: #fff; font-weight: 700; padding: 18px 16px; font-size: 15px; }
.aside :deep(.el-menu) { border-right: none; }
.header { background: #fff; display: flex; align-items: center; justify-content: space-between; box-shadow: 0 1px 4px rgba(0,0,0,.08); }
.header-disclaimer { color: #b45309; font-size: 13px; }
.user-chip { cursor: pointer; display: flex; align-items: center; gap: 6px; }
</style>
