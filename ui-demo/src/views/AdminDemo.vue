<script setup lang="ts">
import { computed, ref } from 'vue'
import { Bell, CircleCheck, DataAnalysis, Document, Link, Refresh, Search, Setting, WarningFilled } from '@element-plus/icons-vue'
import { knowledgeSources } from '../mock'
import AppModal from '../components/AppModal.vue'
import PaginationBar from '../components/PaginationBar.vue'
import StatusPill from '../components/StatusPill.vue'

const query = ref('')
const page = ref(1)
const showReindex = ref(false)
const toast = ref('')
const filteredSources = computed(() => knowledgeSources.filter((source) => `${source.publisher}${source.title}${source.version}`.includes(query.value.trim())))
const bars = [72, 46, 58, 82, 64, 91, 78]
function reindex() { showReindex.value = false; toast.value = '已模拟完成知识索引重建'; window.setTimeout(() => { toast.value = '' }, 2600) }
</script>

<template>
  <section class="page-stack">
    <Transition name="toast"><div v-if="toast" class="toast"><CircleCheck />{{ toast }}</div></Transition>
    <header class="page-header"><div><span class="eyebrow">治理端 · 安全与可观察性</span><h1>系统运行概览</h1><p>先看异常与整体健康，再按需展开知识来源、运行轨迹和审计细节。</p></div><div class="system-state"><i /><span><strong>所有服务运行正常</strong><small>最后检查：刚刚</small></span></div></header>

    <div class="metric-grid admin-metrics">
      <article><span class="metric-icon green"><DataAnalysis /></span><div><small>今日运行</small><strong>128</strong><em><b>+12%</b> 较昨日</em></div></article>
      <article><span class="metric-icon red"><Bell /></span><div><small>开放告警</small><strong>3</strong><em>1 条需要关注</em></div></article>
      <article><span class="metric-icon blue"><CircleCheck /></span><div><small>安全通过率</small><strong>94.6%</strong><em><b>稳定</b> 最近 7 天</em></div></article>
      <article><span class="metric-icon amber"><Document /></span><div><small>活跃知识分块</small><strong>126</strong><em>4 个来源</em></div></article>
    </div>

    <div class="admin-overview">
      <article class="surface chart-panel"><header class="surface-head"><div><span class="eyebrow">最近 7 天</span><h2>Agent 运行趋势</h2></div><select aria-label="趋势时间范围"><option>最近 7 天</option><option>最近 30 天</option></select></header><div class="chart-legend"><span><i class="green" />安全通过</span><span><i class="amber" />安全阻断</span><span><i class="red" />失败</span></div><div class="bar-chart" aria-label="最近七天运行趋势图"><div v-for="(bar, index) in bars" :key="index"><span class="bar-stack"><i class="success" :style="{ height: `${bar}%` }" /><i class="blocked" :style="{ height: `${Math.max(7, 20 - index)}%` }" /></span><small>{{ ['周三','周四','周五','周六','周日','周一','今天'][index] }}</small></div></div></article>
      <article class="surface alert-panel"><header class="surface-head"><div><span class="eyebrow">实时关注</span><h2>安全告警</h2></div><StatusPill label="3 条开放" tone="red" dot /></header><div class="alert-list"><article><span class="alert-icon red"><WarningFilled /></span><div><strong>规则与摘要结论不一致</strong><p>运行 R-8F21 · 问诊 M-0722-018</p><small>8 分钟前</small></div><button type="button">查看</button></article><article><span class="alert-icon amber"><Bell /></span><div><strong>知识来源即将到期复核</strong><p>中华医学会 · 常见症状基层评估共识</p><small>2 小时前</small></div><button type="button">查看</button></article><article><span class="alert-icon blue"><Setting /></span><div><strong>索引重建耗时高于基线</strong><p>pgvector-kb-v3 · 9.2 秒</p><small>昨天 18:42</small></div><button type="button">查看</button></article></div><footer><button type="button">查看全部告警</button></footer></article>
    </div>

    <article class="surface knowledge-surface">
      <header class="surface-head"><div><span class="eyebrow">可追溯知识库</span><h2>知识来源</h2><p>展示来源状态和业务摘要，技术详情按需展开。</p></div><button class="button primary" type="button" @click="showReindex = true"><Refresh />重建受控索引</button></header>
      <div class="knowledge-toolbar"><label class="compact-search"><Search /><input v-model="query" type="search" placeholder="搜索标题、发布方或版本" /></label><select aria-label="按发布方筛选"><option>全部发布方</option><option>国家卫生健康委</option><option>世界卫生组织</option></select><select aria-label="按状态筛选"><option>全部状态</option><option>已验证</option><option>待复核</option></select></div>
      <div class="knowledge-table"><div class="knowledge-head"><span>来源与标题</span><span>版本</span><span>活跃分块</span><span>状态</span><span>操作</span></div><article v-for="source in filteredSources" :key="source.title"><div><span class="source-icon"><Document /></span><p><small>{{ source.publisher }}</small><strong>{{ source.title }}</strong></p></div><code>{{ source.version }}</code><strong>{{ source.chunks }}</strong><StatusPill :label="source.status" :tone="source.status === '已验证' ? 'green' : 'amber'" dot /><button type="button"><Link />来源详情</button></article></div>
      <footer class="table-footer"><span>共 {{ filteredSources.length }} 个来源 · 最后同步于今天 14:20</span><PaginationBar :page="page" :total="2" @change="page = $event" /></footer>
    </article>

    <AppModal :open="showReindex" eyebrow="受控操作" title="确认重建知识索引？" @close="showReindex = false">
      <p class="modal-copy">系统将基于已审计的知识快照模拟重建索引。Demo 不会访问网络或变更真实存储。</p>
      <div class="modal-summary"><span>知识版本</span><strong>pgvector-kb-v3</strong><span>预计影响</span><strong>仅本地交互状态</strong></div>
      <template #actions><button class="button secondary" type="button" @click="showReindex = false">取消</button><button class="button primary" type="button" @click="reindex">开始重建</button></template>
    </AppModal>
  </section>
</template>
