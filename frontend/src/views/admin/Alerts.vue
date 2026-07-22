<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <h3 class="page-title" style="margin:0;flex:1">安全告警</h3>
        <el-select v-model="filters.type" placeholder="类型" clearable style="width: 160px" @change="load(1)">
          <el-option v-for="(v, k) in alertTypeMap" :key="k" :label="v" :value="k" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 130px" @change="load(1)">
          <el-option label="未处理" value="OPEN" /><el-option label="已确认" value="ACK" /><el-option label="已关闭" value="CLOSED" />
        </el-select>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="类型" width="140">
          <template #default="{ row }">{{ alertTypeMap[row.type] || row.type }}</template>
        </el-table-column>
        <el-table-column label="级别" width="90">
          <template #default="{ row }"><StatusTag kind="risk" :value="row.level" /></template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'OPEN' ? 'danger' : row.status === 'ACK' ? 'warning' : 'info'">
              {{ { OPEN: '未处理', ACK: '已确认', CLOSED: '已关闭' }[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <template v-if="row.status === 'OPEN'">
              <el-button size="small" @click="handle(row, 'ACK')">确认</el-button>
              <el-button size="small" type="success" @click="handle(row, 'CLOSE')">关闭</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="total, prev, pager, next" class="mt-12" @current-change="load" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { alertApi } from '@/api/modules'
import { StatusTag } from '@/components/Tags'
import type { SafetyAlert } from '@/types'
import { alertTypeMap, fmtTime } from '@/utils/format'

const loading = ref(false)
const rows = ref<SafetyAlert[]>([])
const page = ref(1)
const total = ref(0)
const filters = reactive({ type: '', status: '' })

async function load(p = page.value) {
  page.value = p
  loading.value = true
  try {
    const { data } = await alertApi.page({ page: p - 1, size: 10, type: filters.type || undefined, status: filters.status || undefined })
    rows.value = data.content
    total.value = data.totalElements
  } finally { loading.value = false }
}

async function handle(row: SafetyAlert, action: 'ACK' | 'CLOSE') {
  const { value } = await ElMessageBox.prompt('处理备注', action === 'ACK' ? '确认告警' : '关闭告警', { inputValue: '' })
  await alertApi.handle(row.id, { action, note: value || '' })
  ElMessage.success('已处理')
  load()
}

onMounted(() => load(1))
</script>
