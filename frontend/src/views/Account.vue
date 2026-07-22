<template>
  <div class="page">
    <div class="page-card" style="max-width: 640px">
      <h3 class="page-title">个人中心</h3>
      <el-descriptions :column="1" border class="mb-12">
        <el-descriptions-item label="用户名">{{ auth.user?.username }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ auth.user?.realName }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag v-for="r in auth.roles" :key="r" size="small" style="margin-right:4px">{{ roleNameMap[r] || r }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">修改密码</el-divider>
      <el-form label-width="90px">
        <el-form-item label="原密码" required><el-input v-model="pwd.oldPassword" type="password" show-password /></el-form-item>
        <el-form-item label="新密码" required><el-input v-model="pwd.newPassword" type="password" show-password placeholder="至少 8 位" /></el-form-item>
        <el-form-item label="确认新密码" required><el-input v-model="confirm" type="password" show-password /></el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="changePwd">确认修改</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const saving = ref(false)
const confirm = ref('')
const pwd = reactive({ oldPassword: '', newPassword: '' })
const roleNameMap: Record<string, string> = { PATIENT: '患者', DOCTOR: '医务人员', FOLLOWUP: '随访人员', ADMIN: '管理员' }

async function changePwd() {
  if (!pwd.oldPassword || !pwd.newPassword) return ElMessage.warning('请填写完整')
  if (pwd.newPassword.length < 8) return ElMessage.warning('新密码至少 8 位')
  if (pwd.newPassword !== confirm.value) return ElMessage.warning('两次密码不一致')
  saving.value = true
  try {
    await authApi.changePassword({ ...pwd })
    ElMessage.success('密码已修改')
    pwd.oldPassword = pwd.newPassword = confirm.value = ''
  } catch { /* 拦截器已提示 */ } finally { saving.value = false }
}
</script>
