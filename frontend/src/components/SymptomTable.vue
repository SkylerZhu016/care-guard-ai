<template>
  <el-table :data="symptoms" size="small" border>
    <el-table-column prop="name" label="症状" min-width="100" />
    <el-table-column label="部位" min-width="90">
      <template #default="{ row }">{{ row.bodyPart || '-' }}</template>
    </el-table-column>
    <el-table-column label="严重程度" width="90">
      <template #default="{ row }">{{ row.severity ? severityMap[row.severity] : '-' }}</template>
    </el-table-column>
    <el-table-column label="持续时间" min-width="90">
      <template #default="{ row }">{{ row.duration || '-' }}</template>
    </el-table-column>
    <template #empty><el-empty description="暂无结构化症状" :image-size="60" /></template>
  </el-table>
</template>

<script setup lang="ts">
import type { StructuredSymptom } from '@/types'
import { severityMap } from '@/utils/format'
defineProps<{ symptoms: StructuredSymptom[] }>()
</script>
