<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { init, use, type EChartsType } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, DataAnalysis, Document, Refresh, Search } from '@element-plus/icons-vue'
import { ApiClient } from '../api'
import { formatDateTime } from '../domain/presentation'
import { useSessionStore } from '../stores/session'
import type { Alert, Audit, Guideline, Run } from '../types'
import StatusPill from '../components/StatusPill.vue'

use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])
const session=useSessionStore(),api=new ApiClient(()=>session.token)
const alerts=ref<Alert[]>([]),audits=ref<Audit[]>([]),runs=ref<Run[]>([]),guidelines=ref<Guideline[]>([]),chart=ref<HTMLElement>(),reindexing=ref(false)
const query=ref(''),publisher=ref('ALL'),knowledgePage=ref(1),knowledgePageSize=6
let chartInstance:EChartsType|undefined
const publishers=computed(()=>[...new Set(guidelines.value.map(item=>item.publisher))].sort())
const filteredGuidelines=computed(()=>{const keyword=query.value.trim().toLowerCase();return guidelines.value.filter(item=>(publisher.value==='ALL'||item.publisher===publisher.value)&&(!keyword||[item.title,item.publisher,item.activeVersion].some(value=>value.toLowerCase().includes(keyword))))})
const pageGuidelines=computed(()=>filteredGuidelines.value.slice((knowledgePage.value-1)*knowledgePageSize,knowledgePage.value*knowledgePageSize))
const openAlerts=computed(()=>alerts.value.filter(item=>item.status==='OPEN').length)

function renderChart(){const counts=['SUCCEEDED','BLOCKED','FAILED'].map(status=>runs.value.filter(run=>run.status===status).length);if(!chart.value)return;chartInstance?.dispose();chartInstance=init(chart.value);chartInstance.setOption({tooltip:{trigger:'axis'},grid:{left:34,right:12,top:18,bottom:28},xAxis:{type:'category',data:['完成','安全阻断','失败'],axisTick:{show:false}},yAxis:{type:'value',minInterval:1},series:[{type:'bar',data:counts,barWidth:32,itemStyle:{color:(params:any)=>['#1b8a70','#d98b2b','#c64b55'][params.dataIndex],borderRadius:[7,7,0,0]}}]})}
async function load(){[alerts.value,audits.value,runs.value,guidelines.value]=await Promise.all([api.alerts(),api.audits(),api.runs(),api.guidelines()]);await nextTick();renderChart()}
async function reindex(){try{await ElMessageBox.confirm('将基于已审计的网络语料快照重建 MinIO 对象与 pgvector 索引。运行时不会临时访问互联网。','确认重建知识索引',{type:'warning',confirmButtonText:'开始重建',cancelButtonText:'取消'});reindexing.value=true;await api.reindexGuidelines();ElMessage.success('知识索引已完成受控重建');await load()}catch(e:any){if(e!=='cancel'&&e!=='close')ElMessage.error(e.message)}finally{reindexing.value=false}}
function resize(){chartInstance?.resize()}
watch([query,publisher],()=>knowledgePage.value=1)
onMounted(()=>{load().catch((e:any)=>ElMessage.error(e.message));window.addEventListener('resize',resize)})
onUnmounted(()=>{window.removeEventListener('resize',resize);chartInstance?.dispose()})
</script>

<template>
  <section class="workspace admin-workspace">
    <div class="page-heading"><div><span class="eyebrow">GOVERNANCE & OBSERVABILITY</span><h1>安全与审计中心</h1><p>先看异常与整体健康，再按需展开知识来源、运行轨迹和审计细节。</p></div><span class="system-badge dark">知识 pgvector-kb-v3 · 规则 red-flags-v1</span></div>
    <div class="metrics"><div><DataAnalysis/><strong>{{runs.length}}</strong><span>Agent 运行</span></div><div :class="{attention:openAlerts>0}"><Bell/><strong>{{openAlerts}}</strong><span>开放告警</span></div><div><Document/><strong>{{runs.filter(r=>r.safetyDecision==='PASS').length}}</strong><span>安全通过</span></div><div><Document/><strong>{{guidelines.reduce((total,item)=>total+item.chunkCount,0)}}</strong><span>活跃知识分块</span></div></div>
    <div class="admin-grid"><article class="panel"><div class="panel-title"><div><DataAnalysis/><h2>运行结果分布</h2></div><span class="safe-chip">最近记录</span></div><div ref="chart" class="chart" role="img" :aria-label="`Agent 运行结果：完成 ${runs.filter(r=>r.status==='SUCCEEDED').length}，阻断 ${runs.filter(r=>r.status==='BLOCKED').length}，失败 ${runs.filter(r=>r.status==='FAILED').length}`"/></article><article class="panel"><div class="panel-title"><div><Bell/><h2>安全告警</h2></div><span class="danger-chip">{{openAlerts}} 条开放</span></div><div v-if="!alerts.length" class="empty-state compact-empty"><Document/><strong>暂无安全告警</strong></div><div class="alert-list"><article v-for="alert in alerts.slice(0,6)" :key="alert.id" class="alert-row"><div><strong>{{alert.category}}</strong><p>{{alert.redactedSummary}}</p><small>{{alert.reasonCodes.join('、')}} · {{formatDateTime(alert.createdAt)}}</small></div><StatusPill :value="alert.status"/></article></div></article></div>
    <article class="panel knowledge-panel">
      <div class="panel-title knowledge-heading"><div><Document/><div><h2>知识来源</h2><p>默认展示业务摘要；许可、哈希和对象路径收进来源详情。</p></div></div><el-button type="primary" :icon="Refresh" :loading="reindexing" @click="reindex">重建受控索引</el-button></div>
      <div class="knowledge-toolbar"><el-input v-model="query" clearable :prefix-icon="Search" aria-label="搜索知识来源" placeholder="搜索标题、发布方或版本"/><el-select v-model="publisher" aria-label="按发布方筛选"><el-option label="全部发布方" value="ALL"/><el-option v-for="item in publishers" :key="item" :label="item" :value="item"/></el-select><span>{{filteredGuidelines.length}} 个来源</span></div>
      <div v-if="!filteredGuidelines.length" class="empty-state large"><Search/><strong>没有匹配的知识来源</strong><p>调整搜索词或发布方筛选。</p></div>
      <div class="guideline-grid"><article v-for="item in pageGuidelines" :key="item.guidelineId" class="guideline-card"><div class="guideline-summary"><div><span class="publisher-label">{{item.publisher}}</span><h3>{{item.title}}</h3><p>{{item.activeVersion}} · {{item.chunkCount}} 个活跃分块</p></div><StatusPill :value="item.sourceStatus"/></div><a :href="item.sourceUrl" target="_blank" rel="noreferrer">打开公开来源</a><details><summary>来源与存储详情</summary><dl><div><dt>许可说明</dt><dd>{{item.licenseNote}}</dd></div><div><dt>采集方式</dt><dd>{{item.retrievalMethod}}</dd></div><div><dt>获取时间</dt><dd>{{item.fetchedAt?formatDateTime(item.fetchedAt):'内置基线'}}</dd></div><div><dt>SHA-256</dt><dd><code>{{item.contentSha256}}</code></dd></div><div><dt>MinIO 对象</dt><dd><code>{{item.objectKey}}</code></dd></div></dl></details></article></div>
      <el-pagination v-if="filteredGuidelines.length>knowledgePageSize" v-model:current-page="knowledgePage" :page-size="knowledgePageSize" :total="filteredGuidelines.length" layout="prev, pager, next" class="pagination"/>
    </article>
    <article class="panel top-gap"><div class="panel-title"><div><DataAnalysis/><h2>Agent 运行轨迹</h2></div><span class="safe-chip">可观察步骤，不展示隐藏推理</span></div><div v-if="!runs.length" class="empty-state compact-empty"><Document/><strong>暂无运行记录</strong></div><details v-for="run in runs.slice(0,12)" :key="run.runId" class="trace-row"><summary><code>{{run.runId.slice(0,12)}}…</code><span>{{run.provider}} / {{run.model}}</span><StatusPill :value="run.status"/></summary><ol><li v-for="step in run.agentTrace" :key="step">{{step}}</li></ol><p v-if="run.safetyReasons.length">安全原因：{{run.safetyReasons.join('、')}}</p></details></article>
    <article class="panel top-gap"><div class="panel-title"><div><Document/><h2>不可变审计时间线</h2></div><span class="safe-chip">最近 {{audits.length}} 条</span></div><div class="audit-table"><div class="table-head"><span>时间</span><span>动作</span><span>对象</span><span>结果</span><span>Request ID</span></div><div v-for="audit in audits" :key="audit.id" class="table-row"><span>{{formatDateTime(audit.createdAt)}}</span><strong>{{audit.action}}</strong><span>{{audit.targetType}}</span><span>{{audit.result}}</span><code>{{audit.requestId.slice(0,12)}}…</code></div></div></article>
  </section>
</template>
