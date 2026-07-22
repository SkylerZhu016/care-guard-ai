import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const TOKEN_KEY = 'mp_access_token'
const REFRESH_KEY = 'mp_refresh_token'

export const tokenStore = {
  get access() { return localStorage.getItem(TOKEN_KEY) || '' },
  get refresh() { return localStorage.getItem(REFRESH_KEY) || '' },
  set(access: string, refresh: string) {
    localStorage.setItem(TOKEN_KEY, access)
    localStorage.setItem(REFRESH_KEY, refresh)
  },
  clear() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
  }
}

const http = axios.create({ baseURL: '/api/v1', timeout: 30000 })

http.interceptors.request.use((config) => {
  const token = tokenStore.access
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let refreshing: Promise<boolean> | null = null

async function doRefresh(): Promise<boolean> {
  if (!tokenStore.refresh) return false
  try {
    const resp = await axios.post('/api/v1/auth/refresh', { refreshToken: tokenStore.refresh })
    tokenStore.set(resp.data.accessToken, resp.data.refreshToken)
    return true
  } catch {
    tokenStore.clear()
    return false
  }
}

http.interceptors.response.use(
  (resp) => resp,
  async (error: AxiosError<{ code?: number; message?: string }>) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined
    const status = error.response?.status
    if (status === 401 && original && !original._retried && !String(original.url).includes('/auth/')) {
      original._retried = true
      refreshing = refreshing || doRefresh().finally(() => { refreshing = null })
      const ok = await refreshing
      if (ok) {
        original.headers = { ...original.headers, Authorization: `Bearer ${tokenStore.access}` }
        return http.request(original)
      }
      tokenStore.clear()
      router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      return Promise.reject(error)
    }
    const msg = error.response?.data?.message || error.message || '请求失败'
    if (!original || !(original as unknown as { silent?: boolean }).silent) {
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default http
