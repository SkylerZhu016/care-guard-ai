import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { tokenStore } from '@/api/http'

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  { path: '/register', name: 'register', component: () => import('@/views/Register.vue'), meta: { public: true } },
  { path: '/403', name: 'forbidden', component: () => import('@/views/Forbidden.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    children: [
      { path: '', redirect: '/p/home' },
      { path: 'account', name: 'account', component: () => import('@/views/Account.vue') },
      // 患者端
      { path: 'p/home', name: 'p-home', component: () => import('@/views/patient/PatientHome.vue'), meta: { roles: ['PATIENT'] } },
      { path: 'p/archive', name: 'p-archive', component: () => import('@/views/patient/PatientArchive.vue'), meta: { roles: ['PATIENT'] } },
      { path: 'p/visit-new', name: 'p-visit-new', component: () => import('@/views/patient/VisitNew.vue'), meta: { roles: ['PATIENT'] } },
      { path: 'p/visits', name: 'p-visits', component: () => import('@/views/patient/VisitList.vue'), meta: { roles: ['PATIENT'] } },
      { path: 'p/visits/:id', name: 'p-visit-detail', component: () => import('@/views/patient/VisitDetail.vue'), meta: { roles: ['PATIENT'] } },
      { path: 'p/visits/:id/supplement', name: 'p-visit-supplement', component: () => import('@/views/patient/VisitSupplement.vue'), meta: { roles: ['PATIENT'] } },
      { path: 'p/followups', name: 'p-followups', component: () => import('@/views/patient/PatientFollowups.vue'), meta: { roles: ['PATIENT'] } },
      { path: 'p/followup-task/:id', name: 'p-followup-task', component: () => import('@/views/patient/FollowupTaskFill.vue'), meta: { roles: ['PATIENT'] } },
      // 医生端
      { path: 'd/workbench', name: 'd-workbench', component: () => import('@/views/doctor/Workbench.vue'), meta: { roles: ['DOCTOR'] } },
      { path: 'd/review/:id', name: 'd-review-detail', component: () => import('@/views/doctor/ReviewDetail.vue'), meta: { roles: ['DOCTOR'] } },
      { path: 'd/followup-plan-new/:visitId', name: 'd-plan-new', component: () => import('@/views/doctor/FollowupPlanNew.vue'), meta: { roles: ['DOCTOR'] } },
      { path: 'd/patients', name: 'd-patients', component: () => import('@/views/doctor/PatientList.vue'), meta: { roles: ['DOCTOR', 'FOLLOWUP'] } },
      // 随访端
      { path: 'f/board', name: 'f-board', component: () => import('@/views/follow/TaskBoard.vue'), meta: { roles: ['FOLLOWUP'] } },
      { path: 'f/task/:id', name: 'f-task', component: () => import('@/views/follow/TaskDetail.vue'), meta: { roles: ['FOLLOWUP'] } },
      { path: 'f/trends', name: 'f-trends', component: () => import('@/views/follow/Trends.vue'), meta: { roles: ['FOLLOWUP', 'DOCTOR'] } },
      // 管理端
      { path: 'a/users', name: 'a-users', component: () => import('@/views/admin/Users.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'a/roles', name: 'a-roles', component: () => import('@/views/admin/Roles.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'a/guidelines', name: 'a-guidelines', component: () => import('@/views/admin/Guidelines.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'a/rules', name: 'a-rules', component: () => import('@/views/admin/Rules.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'a/models', name: 'a-models', component: () => import('@/views/admin/Models.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'a/prompts', name: 'a-prompts', component: () => import('@/views/admin/Prompts.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'a/agent-runs', name: 'a-agent-runs', component: () => import('@/views/admin/AgentRuns.vue'), meta: { roles: ['ADMIN', 'DOCTOR'] } },
      { path: 'a/alerts', name: 'a-alerts', component: () => import('@/views/admin/Alerts.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'a/audit-logs', name: 'a-audit', component: () => import('@/views/admin/AuditLogs.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'a/configs', name: 'a-configs', component: () => import('@/views/admin/Configs.vue'), meta: { roles: ['ADMIN'] } }
    ]
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFound.vue'), meta: { public: true } }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) return true
  if (!tokenStore.access) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  const need = (to.meta.roles as string[] | undefined) || []
  if (need.length && !need.some((r) => auth.roles.includes(r as never))) {
    return { path: '/403' }
  }
  if (to.path === '/') return auth.homePath
  return true
})

export default router
