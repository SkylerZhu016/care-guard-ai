<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <h3 class="page-title" style="margin:0;flex:1">我的预问诊</h3>
        <el-radio-group v-model="tab" @change="load(1)">
          <el-radio-button value="all">全部记录</el-radio-button>
          <el-radio-button value="drafts">草稿箱</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="$router.push('/p/visit-new')">新建预问诊</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="visitNo" label="问诊单号" width="150" />
        <el-table-column prop="patientName" label="患者" width="110" />
        <el-table-column label="主诉" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.formData?.chiefComplaint || '（未填写）' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><StatusTag kind="visit" :value="row.status" /></template>
        </el-table-column>
        <el-table-column label="风险" width="90">
          <template #default="{ row }">
            <StatusTag v-if="row.status === 'REVIEWED' && row.riskLevel" kind="risk" :value="row.riskLevel" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" size="small" type="primary" @click="$router.push(`/p/visit-new?draftId=${row.id}`)">继续填写</el-button>
            <el-button v-else size="small" @click="$router.push(`/p/visits/${row.id}`)">查看</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无记录，点击右上角新建预问诊" /></template>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="total, prev, pager, next" class="mt-12" @current-change="load" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { visitApi } from '@/api/modules'
import { StatusTag } from '@/components/Tags'
import type { Visit } from '@/types'
import { fmtTime } from '@/utils/format'

const loading = ref(false)
const rows = ref<Visit[]>([])
const page = ref(1)
const total = ref(0)
const tab = ref('all')

async function load(p = page.value) {
  page.value = p
  loading.value = true
  try {
    if (tab.value === 'drafts') {
      const { data } = await visitApi.drafts({ page: p - 1, size: 10 })
      rows.value = data.content
      total.value = data.totalElements
    } else {
      const { data } = await visitApi.page({ page: p - 1, size: 10 })
      rows.value = data.content.filter((v) => v.status !== 'DRAFT')
      total.value = data.totalElements
    }
  } finally { loading.value = false }
}

onMounted(() => load(1))
</script>
