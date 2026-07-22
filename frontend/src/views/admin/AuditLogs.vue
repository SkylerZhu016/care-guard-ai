<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <h3 class="page-title" style="margin:0;flex:1">审计日志</h3>
        <el-input v-model="filters.username" placeholder="操作人" style="width: 140px" clearable @change="load(1)" />
        <el-input v-model="filters.action" placeholder="操作类型" style="width: 160px" clearable @change="load(1)" />
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" @change="load(1)" />
      </div>
      <el-table v-loading="loading" :data="rows" border size="small">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="操作人" width="110" />
        <el-table-column prop="role" label="角色" width="100" />
        <el-table-column prop="action" label="操作" width="150" />
        <el-table-column label="对象" width="160">
          <template #default="{ row }">{{ row.objectType || '-' }}#{{ row.objectId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="afterSummary" label="摘要" min-width="220" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="120" />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="traceId" label="TraceId" width="120" show-overflow-tooltip />
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="total, prev, pager, next" class="mt-12" @current-change="load" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { auditApi } from '@/api/modules'
import type { AuditLog } from '@/types'
import { fmtTime } from '@/utils/format'

const loading = ref(false)
const rows = ref<AuditLog[]>([])
const page = ref(1)
const total = ref(0)
const range = ref<[string, string] | null>(null)
const filters = reactive({ username: '', action: '' })

async function load(p = page.value) {
  page.value = p
  loading.value = true
  try {
    const { data } = await auditApi.page({
      page: p - 1, size: 20,
      username: filters.username || undefined,
      action: filters.action || undefined,
      dateFrom: range.value?.[0], dateTo: range.value?.[1]
    })
    rows.value = data.content
    total.value = data.totalElements
  } finally { loading.value = false }
}

onMounted(() => load(1))
</script>
