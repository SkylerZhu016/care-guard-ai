<template>
  <div class="guidelines">
    <el-card class="page-header">
      <h2><el-icon><Reading /></el-icon> 指南检索与原文对照</h2>
      <p class="page-desc">检索基层医疗公开诊疗指南 · 支持关键词和分类检索</p>
    </el-card>

    <el-card class="search-card">
      <el-row :gutter="12">
        <el-col :span="6">
          <el-select v-model="category" placeholder="选择分类" clearable style="width:100%">
            <el-option label="全部" value="" />
            <el-option label="心血管" value="心血管" />
            <el-option label="内分泌" value="内分泌" />
            <el-option label="呼吸" value="呼吸" />
            <el-option label="神经内科" value="神经内科" />
            <el-option label="感染" value="感染" />
          </el-select>
        </el-col>
        <el-col :span="14">
          <el-input v-model="keyword" placeholder="搜索指南关键词..." clearable @keyup.enter="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="search" style="width:100%">检索</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-row :gutter="16">
      <el-col v-for="guide in guidelines" :key="guide.id" :span="12" class="guide-col">
        <el-card class="guide-card" shadow="hover">
          <div class="guide-header">
            <el-tag size="small">{{ guide.category }}</el-tag>
            <span class="guide-source">{{ guide.source }} v{{ guide.version }}</span>
          </div>
          <h3 class="guide-title">{{ guide.title }}</h3>
          <div class="guide-content">{{ guide.content }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!guidelines.length && searched" description="未找到匹配的指南" :image-size="80" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import http from '@/utils/http'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'

// For now define inline since backend entity might differ
interface Guide {
  id: number
  title: string
  category: string
  content: string
  source: string
  version: string
}

const guidelines = ref<Guide[]>([])
const keyword = ref('')
const category = ref('')
const searched = ref(false)

async function search() {
  try {
    let url = '/guidelines/search'
    const params: Record<string, string> = {}
    if (keyword.value) params.keyword = keyword.value
    if (category.value) params.category = category.value
    
    const res = await http.get(url, { params })
    if (res.code === 200) {
      guidelines.value = res.data
      searched.value = true
    }
  } catch {
    ElMessage.error('检索失败')
  }
}

onMounted(() => {
  search()
})
</script>

<style scoped>
.guidelines { max-width: 1400px; margin: 0 auto; }
.page-header { border-radius: 12px; margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }
.page-desc { color: #909399; font-size: 13px; margin-top: 6px; }
.search-card { border-radius: 12px; margin-bottom: 16px; }
.guide-col { margin-bottom: 16px; }
.guide-card { border-radius: 12px; height: 100%; }
.guide-card:hover { border-color: #409eff; }
.guide-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.guide-source { font-size: 12px; color: #909399; }
.guide-title { font-size: 16px; color: #303133; margin-bottom: 10px; }
.guide-content { font-size: 13px; color: #606266; line-height: 1.8; white-space: pre-wrap; }
</style>

