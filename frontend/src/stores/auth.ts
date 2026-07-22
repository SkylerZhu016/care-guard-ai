import { defineStore } from 'pinia'
import http, { tokenStore } from '@/api/http'
import type { Role, UserInfo, LoginResp } from '@/types'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as UserInfo | null
  }),
  getters: {
    isLoggedIn: (s) => !!tokenStore.access && !!s.user,
    roles: (s) => s.user?.roles || [],
    permissions: (s) => s.user?.permissions || [],
    homePath(): string {
      const r = this.roles as Role[]
      if (r.includes('ADMIN')) return '/a/users'
      if (r.includes('DOCTOR')) return '/d/workbench'
      if (r.includes('FOLLOWUP')) return '/f/board'
      return '/p/home'
    }
  },
  actions: {
    hasRole(...roles: Role[]) {
      return roles.some((r) => this.roles.includes(r))
    },
    hasPermission(code: string) {
      if (this.roles.includes('ADMIN')) return true
      return this.permissions.includes(code)
    },
    async login(username: string, password: string) {
      const { data } = await http.post<LoginResp>('/auth/login', { username, password })
      tokenStore.set(data.accessToken, data.refreshToken)
      this.user = data.user
      return data
    },
    async fetchMe() {
      const { data } = await http.get<UserInfo>('/auth/me')
      this.user = data
    },
    async logout() {
      try {
        await http.post('/auth/logout', { refreshToken: tokenStore.refresh })
      } catch { /* ignore */ }
      tokenStore.clear()
      this.user = null
    }
  }
})
