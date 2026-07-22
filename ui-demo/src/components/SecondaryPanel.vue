<script setup lang="ts">
import { Bell, CircleCheck, Document, FolderOpened, Lock, User } from '@element-plus/icons-vue'
import type { Role } from '../types'

const props = defineProps<{ role: Role; title: string }>()
const content: Record<string, { eyebrow: string; description: string; items: { title: string; meta: string; text: string }[] }> = {
  records: { eyebrow: '患者端 · 问诊记录', description: '按状态查看草稿、审核中和已完成记录，并在允许时补充信息。', items: [
    { title: '主要不适为头痛、恶心', meta: 'M-0721-008 · 等待人工审核', text: '提交于昨天 16:42，当前无需额外操作。' },
    { title: '主要不适为持续咳嗽', meta: 'M-0718-016 · 已完成审核', text: '医务人员已完成审核，未创建随访任务。' },
    { title: '胸口不适（草稿）', meta: '本机草稿 · 2 小时前', text: '已填写 2 / 4 个阶段，可继续完成。' },
  ] },
  patientTasks: { eyebrow: '患者端 · 随访任务', description: '查看医务人员已激活的随访计划与完成状态。', items: [
    { title: '症状变化复核', meta: '今天 18:00 前', text: '确认头痛是否仍存在，并补充变化。' },
    { title: '健康资料确认', meta: '本周五前', text: '确认过敏史和长期用药信息是否有变化。' },
  ] },
  profile: { eyebrow: '患者端 · 健康资料', description: '维护轻量、结构化的健康资料，提交问诊时生成快照。', items: [
    { title: '基础资料', meta: '完整度 80%', text: '年龄段 45–64 岁；生理信息已填写。' },
    { title: '慢性病情况', meta: '已更新', text: '高血压；最近更新于 2026-07-18。' },
    { title: '过敏与长期用药', meta: '待补充', text: '当前未填写，可选择“不清楚”。' },
  ] },
  default: { eyebrow: '静态流程预览', description: '该分区已纳入导航结构，当前以代表性 Mock 内容验证布局。', items: [
    { title: '结构已就绪', meta: '本地 Mock 状态', text: '列表、状态、操作入口与响应式布局均可持续扩展。' },
    { title: '不连接真实后端', meta: '独立 Demo', text: '所有界面操作不会影响原项目数据。' },
  ] },
}
const key = props.title.includes('问诊记录') ? 'records' : props.title.includes('患者任务') ? 'patientTasks' : props.title.includes('健康资料') ? 'profile' : 'default'
const page = content[key]
</script>

<template>
  <section class="page-stack secondary-page">
    <header class="page-header"><div><span class="eyebrow">{{ page.eyebrow }}</span><h1>{{ title }}</h1><p>{{ page.description }}</p></div><button class="button primary" type="button"><Document />新建记录</button></header>
    <div class="secondary-grid"><article v-for="(item, index) in page.items" :key="item.title" class="surface secondary-card"><span class="secondary-icon"><FolderOpened v-if="index === 0" /><User v-else-if="index === 1" /><Bell v-else /></span><div><small>{{ item.meta }}</small><h2>{{ item.title }}</h2><p>{{ item.text }}</p></div><button type="button">查看详情</button></article></div>
    <article class="scope-banner"><Lock /><div><strong>当前为设计验证页面</strong><p>内容使用脱敏 Mock 数据，未来可在保持组件接口稳定的前提下接回业务逻辑。</p></div><CircleCheck /></article>
  </section>
</template>
