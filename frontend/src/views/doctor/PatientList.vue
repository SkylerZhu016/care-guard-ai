<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <h3 class="page-title" style="margin:0;flex:1">患者档案查询</h3>
        <el-input v-model="keyword" placeholder="姓名 / 编号" style="width: 220px" clearable @change="load(1)" />
        <el-button type="primary" @click="load(1)">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="patientNo" label="编号" width="130" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column prop="gender" label="性别" width="70" />
        <el-table-column prop="birthDate" label="出生日期" width="110" />
        <el-table-column prop="phone" label="电话" width="140" />
        <el-table-column label="慢病标签" min-width="140">
          <template #default="{ row }">
            <el-tag v-for="t in row.chronicTags" :key="t" size="small" style="margin-right:4px">{{ t }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }"><el-button size="small" @click="open(row.id)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="total, prev, pager, next" class="mt-12" @current-change="load" />
    </div>

    <el-drawer v-model="drawer" title="患者档案详情" size="50%">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="编号">{{ detail.patientNo }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="血型">{{ detail.bloodType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="电话">{{ detail.phone || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4 class="mt-12">既往史</h4>
        <el-table :data="detail.histories" size="small" border>
          <el-table-column prop="diseaseName" label="疾病" />
          <el-table-column prop="diagnosedAt" label="确诊时间" width="110" />
          <el-table-column prop="note" label="备注" />
        </el-table>
        <h4 class="mt-12">过敏史</h4>
        <el-table :data="detail.allergies" size="small" border>
          <el-table-column prop="allergen" label="过敏原" />
          <el-table-column prop="reaction" label="反应" />
          <el-table-column prop="severity" label="程度" width="90" />
        </el-table>
        <h4 class="mt-12">用药记录</h4>
        <el-table :data="detail.medications" size="small" border>
          <el-table-column prop="drugName" label="药品" />
          <el-table-column prop="dosage" label="剂量" />
          <el-table-column prop="frequency" label="频次" />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { patientApi } from '@/api/modules'
import type { Patient } from '@/types'

const loading = ref(false)
const rows = ref<Patient[]>([])
const page = ref(1)
const total = ref(0)
const keyword = ref('')
const drawer = ref(false)
const detail = ref<Patient | null>(null)

async function load(p = page.value) {
  page.value = p
  loading.value = true
  try {
    const { data } = await patientApi.page({ page: p - 1, size: 10, keyword: keyword.value || undefined })
    rows.value = data.content
    total.value = data.totalElements
  } finally { loading.value = false }
}

async function open(id: number) {
  const { data } = await patientApi.detail(id)
  detail.value = data
  drawer.value = true
}

onMounted(() => load(1))
</script>
