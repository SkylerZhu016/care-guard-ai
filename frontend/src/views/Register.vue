<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <template #header><h2 style="margin:0;text-align:center">注册患者账号</h2></template>
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户名" required><el-input v-model="form.username" placeholder="4-20 位字母数字" /></el-form-item>
        <el-form-item label="姓名" required><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="密码" required><el-input v-model="form.password" type="password" show-password placeholder="至少 8 位，含大小写与数字" /></el-form-item>
        <el-form-item label="确认密码" required><el-input v-model="confirm" type="password" show-password /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item>
          <el-button type="primary" class="w-100" :loading="loading" @click="doRegister">注 册</el-button>
        </el-form-item>
        <el-form-item>
          <el-link type="primary" @click="$router.push('/login')">已有账号？去登录</el-link>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/modules'

const router = useRouter()
const loading = ref(false)
const confirm = ref('')
const form = reactive({ username: '', realName: '', password: '', phone: '' })

async function doRegister() {
  if (!form.username || !form.realName || !form.password) return ElMessage.warning('请填写完整信息')
  if (form.password.length < 8) return ElMessage.warning('密码至少 8 位')
  if (form.password !== confirm.value) return ElMessage.warning('两次密码不一致')
  loading.value = true
  try {
    await authApi.register({ ...form, phone: form.phone || undefined })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch { /* 拦截器已提示 */ } finally { loading.value = false }
}
</script>

<style scoped>
.login-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #1a5276 0%, #2874a6 100%); }
.login-card { width: 480px; }
</style>
