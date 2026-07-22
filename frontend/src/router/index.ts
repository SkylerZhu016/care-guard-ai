import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      component: () => import('@/views/Layout.vue'),
      meta: { requiresAuth: true },
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/Dashboard.vue'),
          meta: { title: '工作台' },
        },
        {
          path: 'pre-consult',
          name: 'PreConsult',
          component: () => import('@/views/PreConsult.vue'),
          meta: { title: '预问诊', roles: ['ROLE_PATIENT', 'ROLE_ADMIN'] },
        },
        {
          path: 'doctor-workspace',
          name: 'DoctorWorkspace',
          component: () => import('@/views/DoctorWorkspace.vue'),
          meta: { title: '医生工作台', roles: ['ROLE_DOCTOR', 'ROLE_ADMIN'] },
        },
        {
          path: 'followup',
          name: 'Followup',
          component: () => import('@/views/Followup.vue'),
          meta: { title: '随访管理', roles: ['ROLE_FOLLOWUP', 'ROLE_DOCTOR', 'ROLE_ADMIN'] },
        },
        {
          path: 'safety',
          name: 'Safety',
          component: () => import('@/views/Safety.vue'),
          meta: { title: '安全监控', roles: ['ROLE_ADMIN', 'ROLE_DOCTOR'] },
        },
        {
          path: 'guidelines',
          name: 'Guidelines',
          component: () => import('@/views/Guidelines.vue'),
          meta: { title: '指南检索' },
        },
      ],
    },
  ],
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
