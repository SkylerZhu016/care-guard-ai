import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from './stores/session'
import type { Role } from './types'
import LoginView from './views/LoginView.vue'

const PatientView = () => import('./views/PatientView.vue')
const ClinicianView = () => import('./views/ClinicianView.vue')
const FollowupView = () => import('./views/FollowupView.vue')
const AdminView = () => import('./views/AdminView.vue')

export const router = createRouter({ history:createWebHistory(), routes:[
  {path:'/',redirect:'/login'}, {path:'/login',component:LoginView},
  {path:'/patient',component:PatientView,meta:{role:'SIMULATED_PATIENT'}},
  {path:'/clinician',component:ClinicianView,meta:{role:'CLINICIAN'}},
  {path:'/followup',component:FollowupView,meta:{role:'FOLLOWUP_STAFF'}},
  {path:'/admin',component:AdminView,meta:{role:'ADMIN'}}
]})

router.beforeEach(to => {
  const session=useSessionStore(); const role=to.meta.role as Role|undefined
  if(role && !session.authenticated) return {path:'/login',query:{redirect:to.fullPath}}
  if(role && session.user?.role!==role) return session.home()
  if(to.path==='/login' && session.authenticated) return session.home()
})
