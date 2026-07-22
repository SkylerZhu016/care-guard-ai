<template>
  <div class="page">
    <div class="page-card">
      <h3 class="page-title">系统配置</h3>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="configKey" label="键" width="220" />
        <el-table-column label="值" min-width="280">
          <template #default="{ row }">
            <el-input v-model="row.configValue" size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="200" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="save(row)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api/modules'

interface ConfigRow { id: number; configKey: string; configValue: string; description?: string }
const loading = ref(false)
const rows = ref<ConfigRow[]>([])

async function load() {
  loading.value = true
  try {
    const { data } = await adminApi.configs()
    rows.value = data
  } finally { loading.value = false }
}

async function save(row: ConfigRow) {
  await adminApi.saveConfig(row.configKey, row.configValue)
  ElMessage.success(`已保存 ${row.configKey}`)
}

onMounted(load)
</script>
