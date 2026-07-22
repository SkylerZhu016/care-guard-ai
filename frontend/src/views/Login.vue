<template>
  <div class="login-container">
    <div class="login-bg">
      <div class="bg-shapes">
        <div class="shape shape-1"></div>
        <div class="shape shape-2"></div>
        <div class="shape shape-3"></div>
      </div>
    </div>
    <div class="login-card">
      <div class="login-header">
        <el-icon :size="40" color="#409eff"><MedicalKit /></el-icon>
        <h2>基层医疗预问诊平台</h2>
        <p class="subtitle">教学用辅助系统 · 安全型预问诊与随访</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" class="login-form" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-footer">
        <p class="tip">测试账号:</p>
        <div class="accounts">
          <el-tag size="small" @click="fillAccount('admin','admin123')">admin/admin123</el-tag>
          <el-tag size="small" type="success" @click="fillAccount('doctor1','doc123')">doctor1/doc123</el-tag>
          <el-tag size="small" type="warning" @click="fillAccount('followup1','fol123')">followup1/fol123</el-tag>
          <el-tag size="small" type="info" @click="fillAccount('patient1','pat123')">patient1/pat123</el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: 'admin123',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function fillAccount(username: string, password: string) {
  form.username = username
  form.password = password
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await userStore.login(form.username, form.password)
    if (res.code === 200) {
      ElMessage.success(`欢迎回来，${res.data.displayName}`)
      router.push('/dashboard')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;
}
.shape {
  position: absolute;
  border-radius: 50%;
  opacity: 0.1;
  background: #fff;
}
.shape-1 { width: 600px; height: 600px; top: -200px; right: -100px; }
.shape-2 { width: 400px; height: 400px; bottom: -100px; left: -100px; }
.shape-3 { width: 300px; height: 300px; bottom: 10%; right: 20%; }
.login-card {
  position: relative;
  width: 440px;
  padding: 40px;
  background: rgba(255,255,255,0.95);
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
  backdrop-filter: blur(10px);
}
.login-header {
  text-align: center;
  margin-bottom: 32px;
}
.login-header h2 {
  margin-top: 12px;
  font-size: 22px;
  color: #1a1a2e;
}
.subtitle {
  font-size: 13px;
  color: #909399;
  margin-top: 6px;
}
.login-form {
  margin-top: 8px;
}
.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 10px;
}
.login-footer {
  margin-top: 24px;
  text-align: center;
}
.tip {
  font-size: 12px;
  color: #909399;
  margin-bottom: 10px;
}
.accounts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
.accounts .el-tag {
  cursor: pointer;
  transition: transform 0.2s;
}
.accounts .el-tag:hover {
  transform: scale(1.05);
}
</style>
