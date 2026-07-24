<template>
  <div class="guidelines">
    <el-card class="page-header">
      <h2><el-icon><Reading /></el-icon> AI 指南检索</h2>
      <p class="page-desc">基于 DeepSeek 大模型的智能医学指南检索 · 输入自然语言即可查询</p>
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
          <el-input v-model="keyword" placeholder="输入问题，如：高血压怎么治？糖尿病患者用什么药？" clearable @keyup.enter="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="search" :loading="loading" style="width:100%">AI 检索</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- AI Answer -->
    <el-card v-if="aiAnswer" class="answer-card" shadow="hover">
      <template #header>
        <div class="answer-header">
          <el-tag type="success" effect="dark">AI 回答</el-tag>
          <span class="answer-label">基于检索到的指南内容生成</span>
        </div>
      </template>
      <div class="answer-content">{{ aiAnswer }}</div>
    </el-card>

    <!-- Search Results -->
    <el-row :gutter="16">
      <el-col v-for="guide in guidelines" :key="guide.id" :span="12" class="guide-col">
        <el-card class="guide-card" shadow="hover">
          <div class="guide-header">
            <el-tag size="small">{{ guide.category }}</el-tag>
          </div>
          <h3 class="guide-title">{{ guide.title }}</h3>
          <el-scrollbar max-height="180px">
            <div class="guide-content">{{ guide.content }}</div>
          </el-scrollbar>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!guidelines.length && searched && !loading" description="未找到匹配的指南" :image-size="80" />
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue"
import { ElMessage } from "element-plus"
import { Search } from "@element-plus/icons-vue"

interface Guide {
  id: number
  title: string
  category: string
  content: string
}

const guidelines = ref<Guide[]>([])
const keyword = ref("")
const category = ref("")
const searched = ref(false)
const loading = ref(false)
const aiAnswer = ref("")

async function search() {
  loading.value = true
  searched.value = true
  aiAnswer.value = ""
  guidelines.value = []

  try {
    const body: Record<string, any> = { query: keyword.value || "", top_k: 5 }
    if (category.value) body.category = category.value

    const res = await fetch("/ai/rag/search", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    })

    const result = await res.json()
    if (result.code === 200) {
      guidelines.value = result.data.results || []
      aiAnswer.value = result.data.ai_answer || ""
    } else {
      ElMessage.error("检索失败")
    }
  } catch {
    ElMessage.error("网络错误")
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.guidelines { max-width: 1400px; margin: 0 auto; }
.page-header { border-radius: 12px; margin-bottom: 16px; }
.page-header h2 { font-size: 20px; }
.page-desc { color: #909399; font-size: 13px; margin-top: 6px; }
.search-card { border-radius: 12px; margin-bottom: 16px; }
.answer-card { border-radius: 12px; margin-bottom: 16px; border-color: #67c23a; }
.answer-card :deep(.el-card__header) { padding: 12px 20px; border-bottom: 1px solid #e1f3d8; background: #f0f9eb; border-radius: 12px 12px 0 0; }
.answer-header { display: flex; align-items: center; gap: 8px; }
.answer-label { font-size: 12px; color: #67c23a; }
.answer-content { font-size: 14px; color: #303133; line-height: 1.8; white-space: pre-wrap; padding: 4px 0; }
.guide-col { margin-bottom: 16px; }
.guide-card { border-radius: 12px; height: 100%; }
.guide-card:hover { border-color: #409eff; }
.guide-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.guide-title { font-size: 16px; color: #303133; margin-bottom: 10px; }
.guide-content { font-size: 13px; color: #606266; line-height: 1.8; white-space: pre-wrap; }
</style>
