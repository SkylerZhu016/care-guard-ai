<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <h3 class="page-title" style="margin:0;flex:1">症状变化趋势</h3>
        <el-select v-model="patientId" placeholder="选择患者" style="width: 240px" filterable @change="loadTrends">
          <el-option v-for="p in patients" :key="p.id" :label="`${p.name}（${p.patientNo}）`" :value="p.id" />
        </el-select>
      </div>
      <div ref="chartRef" style="width: 100%; height: 380px"></div>
      <el-empty v-if="!points.length && loaded" description="该患者暂无随访记录" />
    </div>
    <div class="page-card" v-if="points.length">
      <h4 class="page-title">随访明细</h4>
      <el-table :data="points" border size="small">
        <el-table-column prop="date" label="日期" width="120" />
        <el-table-column label="症状变化" width="110">
          <template #default="{ row }">{{ symptomChangeMap[row.symptomChange] || row.symptomChange }}</template>
        </el-table-column>
        <el-table-column prop="note" label="记录" min-width="220" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { followupApi, patientApi } from '@/api/modules'
import type { Patient, TrendPoint } from '@/types'
import { symptomChangeMap } from '@/utils/format'

const patients = ref<Patient[]>([])
const patientId = ref<number>()
const points = ref<TrendPoint[]>([])
const loaded = ref(false)
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

async function loadTrends() {
  if (!patientId.value) return
  const { data } = await followupApi.trends(patientId.value)
  points.value = data
  loaded.value = true
  await nextTick()
  renderChart()
}

function renderChart() {
  if (!chartRef.value) return
  chart = chart || echarts.init(chartRef.value)
  chart.setOption({
    title: { text: '症状变化趋势（1=好转 2=平稳 3=加重）', textStyle: { fontSize: 13 } },
    tooltip: {
      trigger: 'axis',
      formatter: (ps: unknown) => {
        const arr = ps as { dataIndex: number }[]
        const p = points.value[arr[0]?.dataIndex ?? 0]
        return p ? `${p.date}<br/>${symptomChangeMap[p.symptomChange] || ''}<br/>${p.note || ''}` : ''
      }
    },
    grid: { left: 50, right: 30, top: 50, bottom: 40 },
    xAxis: { type: 'category', data: points.value.map((p) => p.date) },
    yAxis: { type: 'value', min: 0.5, max: 3.5, interval: 1, axisLabel: { formatter: (v: number) => ['', '好转', '平稳', '加重'][v] || v } },
    series: [{
      type: 'line', data: points.value.map((p) => p.value), smooth: true,
      symbolSize: 10, lineStyle: { width: 3 },
      itemStyle: { color: '#409eff' }, areaStyle: { opacity: 0.12 }
    }]
  }, true)
}

onMounted(async () => {
  const { data } = await patientApi.page({ page: 0, size: 50 })
  patients.value = data.content
  if (data.content.length) {
    patientId.value = data.content[0].id
    loadTrends()
  }
})
</script>
