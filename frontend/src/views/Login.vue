<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <template #header>
        <div style="text-align:center">
          <h2 style="margin:0">基层医疗安全型预问诊与随访平台</h2>
          <div class="muted" style="margin-top:6px">教学用辅助系统</div>
        </div>
      </template>
      <el-alert type="warning" :title="DISCLAIMER" show-icon :closable="false" class="mb-12" />
      <el-form :model="form" @keyup.enter="doLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" size="large" class="w-100" :loading="loading" @click="doLogin">登 录</el-button>
      </el-form>
      <div class="flex-between mt-12">
        <el-link type="primary" @click="$router.push('/register')">注册患者账号</el-link>
      </div>
      <el-divider content-position="left">演示账号</el-divider>
      <div class="demo-accounts">
        <div v-for="a in demoAccounts" :key="a.u" class="demo-account" @click="fill(a.u, a.p)">
          <el-tag size="small">{{ a.r }}</el-tag> {{ a.u }}
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { DISCLAIMER } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const demoAccounts = [
  { u: 'patient1', p: 'Patient@123456', r: '患者' },
  { u: 'doctor1', p: 'Doctor@123456', r: '医生' },
  { u: 'follow1', p: 'Follow@123456', r: '随访' },
  { u: 'admin', p: 'Admin@123456', r: '管理员' }
]

function fill(u: string, p: string) {
  form.username = u
  form.password = p
}

async function doLogin() {
  if (!form.username || !form.password) return ElMessage.warning('请输入用户名和密码')
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push(String(route.query.redirect || auth.homePath))
  } catch { /* 拦截器已提示 */ } finally { loading.value = false }
}
</script>

<style scoped>
.login-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #1a5276 0%, #2874a6 100%); }
.login-card { width: 440px; }
.demo-accounts { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.demo-account { cursor: pointer; font-size: 13px; color: #606266; padding: 4px 8px; border-radius: 4px; }
.demo-account:hover { background: #f0f2f5; }
</style>
