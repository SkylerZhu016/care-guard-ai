<template>
  <div>
    <el-empty v-if="!citations.length" description="暂无引用（证据不足时请以人工审核为准）" :image-size="60" />
    <div v-for="c in citations" :key="c.id" class="citation-item">
      <div class="flex-between">
        <b>《{{ c.title }}》</b>
        <el-tag size="small" type="info">得分 {{ c.score?.toFixed(3) ?? '-' }}</el-tag>
      </div>
      <div class="muted mt-4">
        {{ c.section || '未标注章节' }}<template v-if="c.pageNo"> · 第 {{ c.pageNo }} 页</template>
        <template v-if="c.knowledgeVersion"> · 知识库版本 {{ c.knowledgeVersion }}</template>
      </div>
      <div class="snippet">{{ c.snippet }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Citation } from '@/types'
defineProps<{ citations: Citation[] }>()
</script>

<style scoped>
.citation-item { border: 1px solid #e4e7ed; border-radius: 6px; padding: 10px 12px; margin-bottom: 10px; background: #fafafa; }
.snippet { margin-top: 6px; font-size: 13px; color: #606266; line-height: 1.6; }
.mt-4 { margin-top: 4px; }
</style>
