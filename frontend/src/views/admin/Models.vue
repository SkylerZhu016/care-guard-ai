<template>
  <div class="page">
    <div class="page-card">
      <h3 class="page-title">模型配置管理</h3>
      <el-alert type="info" title="API Key 仅从服务端环境变量读取，此处不显示也不保存密钥" show-icon :closable="false" class="mb-12" />
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="name" label="名称" width="160" />
        <el-table-column prop="provider" label="提供方" width="110" />
        <el-table-column prop="modelName" label="模型" width="150" />
        <el-table-column prop="purpose" label="用途" width="100" />
        <el-table-column label="默认" width="80">
          <template #default="{ row }"><el-tag v-if="row.isDefault" type="success" size="small">默认</el-tag></template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="(v: boolean) => save(row, { enabled: v })" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }"><el-button size="small" @click="open(row)">编辑</el-button></template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dlg" title="编辑模型配置" width="480px">
      <el-form label-width="100px" v-if="current">
        <el-form-item label="名称"><el-input v-model="current.name" disabled /></el-form-item>
        <el-form-item label="API Base"><el-input v-model="current.apiBase" placeholder="OpenAI 兼容接口地址" /></el-form-item>
        <el-form-item label="设为默认"><el-switch v-model="current.isDefault" /></el-form-item>
        <el-form-item label="参数(JSON)">
          <el-input v-model="paramsText" type="textarea" :rows="4" class="mono" placeholder='{"temperature":0.2}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="saveFull">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api/modules'
import type { ModelConfig } from '@/types'

const loading = ref(false)
const rows = ref<ModelConfig[]>([])
const dlg = ref(false)
const current = ref<ModelConfig | null>(null)
const paramsText = ref('')

async function load() {
  loading.value = true
  try {
    const { data } = await adminApi.models()
    rows.value = data
  } finally { loading.value = false }
}

async function save(row: ModelConfig, patch: Partial<ModelConfig>) {
  await adminApi.saveModel(row.id, patch)
  Object.assign(row, patch)
  ElMessage.success('已更新')
}

function open(row: ModelConfig) {
  current.value = { ...row }
  paramsText.value = JSON.stringify(row.params || {}, null, 2)
  dlg.value = true
}

async function saveFull() {
  if (!current.value) return
  let params: Record<string, unknown> = {}
  try { params = JSON.parse(paramsText.value || '{}') } catch { return ElMessage.error('参数不是合法 JSON') }
  await adminApi.saveModel(current.value.id, { apiBase: current.value.apiBase, isDefault: current.value.isDefault, params })
  ElMessage.success('已保存')
  dlg.value = false
  load()
}

onMounted(load)
</script>
