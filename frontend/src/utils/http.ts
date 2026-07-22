import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types'

const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Override get/post/put/delete return type to unwrap ApiResponse
type HttpPromise<T> = Promise<ApiResponse<T>>

http.interceptors.response.use(
  (response: AxiosResponse<ApiResponse<any>>) => {
    const data = response.data
    if (data.code !== 200) {
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }
    ElMessage.error(error.response?.data?.message || error.message || '网络错误')
    return Promise.reject(error)
  }
)

// Wrapper to match the interceptor unwrap
async function get<T = any>(url: string, config?: any): Promise<ApiResponse<T>> {
  const res = await http.get<ApiResponse<T>>(url, config)
  return res.data
}

async function post<T = any>(url: string, data?: any, config?: any): Promise<ApiResponse<T>> {
  const res = await http.post<ApiResponse<T>>(url, data, config)
  return res.data
}

async function put<T = any>(url: string, data?: any, config?: any): Promise<ApiResponse<T>> {
  const res = await http.put<ApiResponse<T>>(url, data, config)
  return res.data
}

async function del<T = any>(url: string, config?: any): Promise<ApiResponse<T>> {
  const res = await http.delete<ApiResponse<T>>(url, config)
  return res.data
}

export { get, post, put, del }
export default { get, post, put, del }

