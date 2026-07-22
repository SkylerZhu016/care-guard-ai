<template>
  <div class="page">
    <div class="page-card">
      <h3 class="page-title">Prompt 模板与版本</h3>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="code" label="编码" width="130" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="purpose" label="用途" min-width="160" />
        <el-table-column label="当前版本" width="100">
          <template #default="{ row }">v{{ row.currentVersion }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }"><el-button size="small" @click="open(row)">版本管理</el-button></template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="drawer" :title="`版本管理 - ${current?.name || ''}`" size="55%">
      <div class="flex-between mb-12">
        <b>历史版本</b>
        <el-button type="primary" size="small" @click="newVersionDlg = true">新增版本</el-button>
      </div>
      <el-collapse>
        <el-collapse-item v-for="v in current?.versions || []" :key="v.id" :name="v.id">
          <template #title>
            <span style="margin-right:12px">v{{ v.version }}</span>
            <el-tag size="small" :type="v.enabled ? 'success' : 'info'">{{ v.enabled ? '启用中' : '已停用' }}</el-tag>
            <span class="muted" style="margin-left:12px">{{ fmtTime(v.createdAt) }}</span>
          </template>
          <div class="json-view">{{ v.content }}</div>
          <el-button size="small" class="mt-12" :type="v.enabled ? 'warning' : 'success'" @click="toggle(v)">
            {{ v.enabled ? '停用' : '启用' }}
          </el-button>
        </el-collapse-item>
      </el-collapse>
    </el-drawer>

    <el-dialog v-model="newVersionDlg" title="新增 Prompt 版本" width="640px">
      <el-input v-model="newContent" type="textarea" :rows="14" class="mono" placeholder="输入新的 Prompt 内容（保存为当前模板的新版本）" />
      <template #footer>
        <el-button @click="newVersionDlg = false">取消</el-button>
        <el-button type="primary" @click="createVersion">保存新版本</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api/modules'
import type { PromptTemplate, PromptVersion } from '@/types'
import { fmtTime } from '@/utils/format'

const loading = ref(false)
const rows = ref<PromptTemplate[]>([])
const drawer = ref(false)
const current = ref<PromptTemplate | null>(null)
const newVersionDlg = ref(false)
const newContent = ref('')

async function load() {
  loading.value = true
  try {
    const { data } = await adminApi.prompts()
    rows.value = data
  } finally { loading.value = false }
}

async function open(row: PromptTemplate) {
  const { data } = await adminApi.prompts()
  const full = data.find((t) => t.id === row.id) || row
  current.value = full
  drawer.value = true
}

async function toggle(v: PromptVersion) {
  if (!current.value) return
  await adminApi.togglePromptVersion(current.value.id, v.id, !v.enabled)
  ElMessage.success('已更新')
  open(current.value)
}

async function createVersion() {
  if (!current.value || !newContent.value.trim()) return ElMessage.warning('请输入内容')
  await adminApi.createPromptVersion(current.value.id, { content: newContent.value })
  ElMessage.success('新版本已创建')
  newVersionDlg.value = false
  newContent.value = ''
  load()
  open(current.value)
}

onMounted(load)
</script>
