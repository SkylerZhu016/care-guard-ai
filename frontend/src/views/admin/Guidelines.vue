<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <h3 class="page-title" style="margin:0;flex:1">医疗指南与知识库</h3>
        <el-button @click="reindex" :loading="reindexing">重建索引</el-button>
        <el-button type="primary" @click="uploadDlg = true">上传指南</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="org" label="发布机构" width="130" />
        <el-table-column prop="docType" label="类型" width="100" />
        <el-table-column label="版本" width="70"><template #default="{ row }">v{{ row.version }}</template></el-table-column>
        <el-table-column label="分块数" width="80"><template #default="{ row }">{{ row.chunkCount ?? '-' }}</template></el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag kind="doc" :value="row.status" /></template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewChunks(row)">分块</el-button>
            <el-button size="small" :type="row.status === 'ENABLED' ? 'warning' : 'success'" @click="toggle(row)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="total, prev, pager, next" class="mt-12" @current-change="load" />
    </div>

    <div class="page-card">
      <h4 class="page-title">检索测试</h4>
      <div class="toolbar">
        <el-input v-model="searchQuery" placeholder="输入检索词，如：胸痛 红旗" style="width: 320px" @keyup.enter="doSearch" />
        <el-button type="primary" @click="doSearch">检索</el-button>
      </div>
      <div v-for="c in searchResults" :key="c.id" class="chunk-item">
        <div class="flex-between"><b>{{ c.section || '未标注章节' }}</b><el-tag size="small" type="info">得分 {{ c.score?.toFixed(3) }}</el-tag></div>
        <div class="muted mt-4">{{ c.content }}</div>
      </div>
    </div>

    <el-dialog v-model="uploadDlg" title="上传指南文档" width="520px">
      <el-form label-width="90px">
        <el-form-item label="文件" required>
          <input type="file" accept=".txt,.md,.pdf" @change="onFile" />
          <div class="muted">仅支持 txt / md / pdf，≤10MB</div>
        </el-form-item>
        <el-form-item label="标题" required><el-input v-model="uploadForm.title" /></el-form-item>
        <el-form-item label="发布机构"><el-input v-model="uploadForm.org" /></el-form-item>
        <el-form-item label="发布日期"><el-date-picker v-model="uploadForm.publishDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="文档类型">
          <el-select v-model="uploadForm.docType" class="w-100">
            <el-option label="诊疗指南" value="GUIDELINE" /><el-option label="专家共识" value="CONSENSUS" />
            <el-option label="科普资料" value="EDUCATION" /><el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用范围"><el-input v-model="uploadForm.scope" /></el-form-item>
        <el-form-item label="来源说明"><el-input v-model="uploadForm.sourceNote" type="textarea" :rows="2" placeholder="公开来源说明（合规要求）" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDlg = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="doUpload">上传并摄取</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="chunkDrawer" :title="`文档分块 - ${currentDoc?.title || ''}`" size="55%">
      <el-table :data="chunks" size="small" border v-loading="chunksLoading">
        <el-table-column prop="chunkNo" label="#" width="50" />
        <el-table-column prop="section" label="章节" width="140" />
        <el-table-column prop="pageNo" label="页码" width="70" />
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { guidelineApi, knowledgeApi } from '@/api/modules'
import { StatusTag } from '@/components/Tags'
import type { Guideline, KnowledgeChunk } from '@/types'

const loading = ref(false)
const rows = ref<Guideline[]>([])
const page = ref(1)
const total = ref(0)
const uploadDlg = ref(false)
const uploading = ref(false)
const reindexing = ref(false)
const file = ref<File | null>(null)
const uploadForm = reactive({ title: '', org: '', publishDate: '', docType: 'GUIDELINE', scope: '', sourceNote: '' })
const searchQuery = ref('')
const searchResults = ref<KnowledgeChunk[]>([])
const chunkDrawer = ref(false)
const chunksLoading = ref(false)
const chunks = ref<KnowledgeChunk[]>([])
const currentDoc = ref<Guideline | null>(null)

async function load(p = page.value) {
  page.value = p
  loading.value = true
  try {
    const { data } = await guidelineApi.page({ page: p - 1, size: 10 })
    rows.value = data.content
    total.value = data.totalElements
  } finally { loading.value = false }
}

function onFile(e: Event) {
  file.value = (e.target as HTMLInputElement).files?.[0] || null
}

async function doUpload() {
  if (!file.value || !uploadForm.title) return ElMessage.warning('请选择文件并填写标题')
  if (file.value.size > 10 * 1024 * 1024) return ElMessage.warning('文件不能超过 10MB')
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file.value)
    Object.entries(uploadForm).forEach(([k, v]) => v && fd.append(k, v))
    await guidelineApi.upload(fd)
    ElMessage.success('上传成功，后台正在摄取分块')
    uploadDlg.value = false
    setTimeout(() => load(), 2000)
  } finally { uploading.value = false }
}

async function toggle(row: Guideline) {
  await guidelineApi.toggle(row.id, row.status !== 'ENABLED')
  ElMessage.success('已更新')
  load()
}

async function remove(row: Guideline) {
  await ElMessageBox.confirm(`确认删除《${row.title}》？（软删除，分块同步停用）`, '警告', { type: 'warning' })
  await guidelineApi.remove(row.id)
  ElMessage.success('已删除')
  load()
}

async function reindex() {
  reindexing.value = true
  try {
    await knowledgeApi.reindex()
    ElMessage.success('已触发重建索引')
  } finally { reindexing.value = false }
}

async function doSearch() {
  if (!searchQuery.value.trim()) return
  const { data } = await knowledgeApi.search(searchQuery.value)
  searchResults.value = data
}

async function viewChunks(row: Guideline) {
  currentDoc.value = row
  chunkDrawer.value = true
  chunksLoading.value = true
  try {
    const { data } = await knowledgeApi.chunks(row.id, { page: 0, size: 50 })
    chunks.value = data.content
  } finally { chunksLoading.value = false }
}

onMounted(() => load(1))
</script>

<style scoped>
.chunk-item { border: 1px solid #e4e7ed; border-radius: 6px; padding: 10px 12px; margin-bottom: 10px; }
.mt-4 { margin-top: 4px; }
</style>
