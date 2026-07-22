import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginResponse, DashboardStats } from '@/types'
import { authApi } from '@/api'

export const useUserStore = defineStore('user', () => {
  const user = ref<LoginResponse | null>(null)
  const token = ref(localStorage.getItem('token') || '')

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => user.value?.role || '')
  const displayName = computed(() => user.value?.displayName || '')

  async function login(username: string, password: string) {
    const res = await authApi.login({ username, password })
    if (res.code === 200) {
      user.value = res.data
      token.value = res.data.token
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('user', JSON.stringify(res.data))
    }
    return res
  }

  function logout() {
    user.value = null
    token.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  function initFromStorage() {
    const saved = localStorage.getItem('user')
    if (saved) {
      user.value = JSON.parse(saved)
    }
  }

  return { user, token, isLoggedIn, role, displayName, login, logout, initFromStorage }
})
