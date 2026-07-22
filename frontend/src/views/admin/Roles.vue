<template>
  <div class="page">
    <div class="page-card">
      <h3 class="page-title">角色权限管理</h3>
      <el-row :gutter="14">
        <el-col :span="6" v-for="role in roles" :key="role.code">
          <el-card shadow="hover">
            <template #header>
              <div class="flex-between">
                <b>{{ role.name }}</b>
                <el-tag size="small">{{ role.code }}</el-tag>
              </div>
            </template>
            <el-checkbox-group v-model="checked[role.code]">
              <el-checkbox v-for="p in allPermissions" :key="p.id" :value="p.id" style="display:block">
                {{ p.name }} <span class="muted mono">{{ p.code }}</span>
              </el-checkbox>
            </el-checkbox-group>
            <el-button type="primary" size="small" class="mt-12" :loading="savingCode === role.code" @click="save(role.code)">
              保存权限
            </el-button>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { roleApi } from '@/api/modules'

interface Perm { id: number; code: string; name: string }
interface Role { id: number; code: string; name: string; permissions?: Perm[] }

const roles = ref<Role[]>([])
const allPermissions = ref<Perm[]>([])
const checked = reactive<Record<string, number[]>>({})
const savingCode = ref('')

onMounted(async () => {
  const { data } = await roleApi.list()
  roles.value = data
  const permSet = new Map<number, Perm>()
  data.forEach((r) => {
    r.permissions?.forEach((p) => permSet.set(p.id, p))
    checked[r.code] = (r.permissions || []).map((p) => p.id)
  })
  allPermissions.value = [...permSet.values()]
})

async function save(code: string) {
  savingCode.value = code
  try {
    await roleApi.updatePermissions(code, checked[code] || [])
    ElMessage.success('已保存')
  } finally { savingCode.value = '' }
}
</script>
