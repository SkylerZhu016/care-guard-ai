import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { Role, User } from '../types'

const homeByRole: Record<Role,string> = { PATIENT:'/patient', CLINICIAN:'/clinician', FOLLOWUP_STAFF:'/followup', ADMIN:'/admin' }

export const useSessionStore = defineStore('session', () => {
  const token = ref<string|null>(null); const user = ref<User|null>(null)
  const authenticated = computed(() => !!token.value && !!user.value)
  function establish(accessToken:string, profile:User){ token.value=accessToken; user.value=profile }
  function logout(){ token.value=null; user.value=null }
  function home(){ return user.value ? homeByRole[user.value.role] : '/login' }
  return { token, user, authenticated, establish, logout, home }
})

