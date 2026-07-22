<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <h3 class="page-title" style="margin:0;flex:1">用户管理</h3>
        <el-select v-model="filters.role" placeholder="角色" clearable style="width: 130px" @change="load(1)">
          <el-option v-for="(v, k) in roleNameMap" :key="k" :label="v" :value="k" />
        </el-select>
        <el-input v-model="filters.keyword" placeholder="用户名/姓名" style="width: 200px" clearable @change="load(1)" />
        <el-button type="primary" @click="openEdit()">新建用户</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="r in row.roles" :key="r" size="small" style="margin-right:4px">{{ roleNameMap[r] || r }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="(v: boolean) => toggle(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="warning" @click="resetPwd(row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="total, prev, pager, next" class="mt-12" @current-change="load" />
    </div>

    <el-dialog v-model="dlg" :title="editForm.id ? '编辑用户' : '新建用户'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="用户名" required><el-input v-model="editForm.username" :disabled="!!editForm.id" /></el-form-item>
        <el-form-item label="姓名" required><el-input v-model="editForm.realName" /></el-form-item>
        <el-form-item v-if="!editForm.id" label="初始密码" required><el-input v-model="editForm.password" type="password" show-password /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editForm.roles" multiple class="w-100">
            <el-option v-for="(v, k) in roleNameMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="电话"><el-input v-model="editForm.phone" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '@/api/modules'
import type { Role, UserInfo } from '@/types'

const roleNameMap: Record<string, string> = { PATIENT: '患者', DOCTOR: '医务人员', FOLLOWUP: '随访人员', ADMIN: '管理员' }
const loading = ref(false)
const saving = ref(false)
const rows = ref<(UserInfo & { enabled: boolean })[]>([])
const page = ref(1)
const total = ref(0)
const filters = reactive({ role: '', keyword: '' })
const dlg = ref(false)
const editForm = reactive<{ id?: number; username: string; realName: string; password: string; roles: Role[]; phone: string }>({
  username: '', realName: '', password: '', roles: [], phone: ''
})

async function load(p = page.value) {
  page.value = p
  loading.value = true
  try {
    const { data } = await userApi.page({ page: p - 1, size: 10, role: filters.role || undefined, keyword: filters.keyword || undefined })
    rows.value = data.content
    total.value = data.totalElements
  } finally { loading.value = false }
}

function openEdit(row?: UserInfo & { enabled: boolean }) {
  Object.assign(editForm, row
    ? { id: row.id, username: row.username, realName: row.realName, password: '', roles: [...row.roles], phone: '' }
    : { id: undefined, username: '', realName: '', password: '', roles: [], phone: '' })
  dlg.value = true
}

async function save() {
  saving.value = true
  try {
    if (editForm.id) await userApi.update(editForm.id, { realName: editForm.realName, roles: editForm.roles })
    else await userApi.create({ username: editForm.username, realName: editForm.realName, password: editForm.password, roles: editForm.roles, phone: editForm.phone || undefined })
    ElMessage.success('已保存')
    dlg.value = false
    load()
  } finally { saving.value = false }
}

async function toggle(row: UserInfo & { enabled: boolean }, enabled: boolean) {
  await userApi.setStatus(row.id, enabled)
  row.enabled = enabled
  ElMessage.success(enabled ? '已启用' : '已禁用')
}

async function resetPwd(row: UserInfo) {
  const { value } = await ElMessageBox.prompt(`为 ${row.realName} 设置新密码`, '重置密码', { inputType: 'password' })
  if (value) {
    await userApi.resetPassword(row.id, value)
    ElMessage.success('已重置')
  }
}

onMounted(() => load(1))
</script>
