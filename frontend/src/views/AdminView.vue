<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { BarChart } from 'echarts/charts'
import { GridComponent } from 'echarts/components'
import { init, use, type EChartsType } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ApiClient } from '../api'
import { useSessionStore } from '../stores/session'
import type { Alert, Audit, Guideline, Run } from '../types'
import StatusPill from '../components/StatusPill.vue'

use([BarChart, GridComponent, CanvasRenderer])
const session=useSessionStore(),api=new ApiClient(()=>session.token),alerts=ref<Alert[]>([]),audits=ref<Audit[]>([]),runs=ref<Run[]>([]),guidelines=ref<Guideline[]>([]),chart=ref<HTMLElement>(),reindexing=ref(false)
let chartInstance: EChartsType | undefined
async function load(){[alerts.value,audits.value,runs.value,guidelines.value]=await Promise.all([api.alerts(),api.audits(),api.runs(),api.guidelines()]);await nextTick();const counts=['SUCCEEDED','BLOCKED','FAILED'].map(s=>runs.value.filter(r=>r.status===s).length);if(chart.value){chartInstance?.dispose();chartInstance=init(chart.value);chartInstance.setOption({grid:{left:28,right:10,top:20,bottom:25},xAxis:{type:'category',data:['成功','阻断','失败']},yAxis:{type:'value',minInterval:1},series:[{type:'bar',data:counts,itemStyle:{color:(p:any)=>['#1b8a70','#d98b2b','#c64b55'][p.dataIndex],borderRadius:[6,6,0,0]}}]})}}
async function reindex(){try{await ElMessageBox.confirm('将从已审计的官方网络语料快照重新上传 MinIO 对象并重建 pgvector 分块索引。运行时不会临时访问互联网。','确认重建知识索引',{type:'warning'});reindexing.value=true;await api.reindexGuidelines();ElMessage.success('网络语料索引已完成幂等重建');await load()}catch(e:any){if(e!=='cancel'&&e!=='close')ElMessage.error(e.message)}finally{reindexing.value=false}}
onMounted(()=>load().catch((e:any)=>ElMessage.error(e.message)))
onUnmounted(()=>chartInstance?.dispose())
</script>

<template><section class="workspace"><div class="page-heading"><div><span class="eyebrow">GOVERNANCE & OBSERVABILITY</span><h1>安全与审计中心</h1><p>查看多角色 Agent 轨迹、知识版本、来源许可和每次安全决策。</p></div><span class="simulation-badge dark">知识检索 pgvector-kb-v3 · 规则 red-flags-v1</span></div><div class="metrics"><div><strong>{{runs.length}}</strong><span>Agent 运行</span></div><div><strong>{{alerts.filter(a=>a.status==='OPEN').length}}</strong><span>开放告警</span></div><div><strong>{{runs.filter(r=>r.safetyDecision==='PASS').length}}</strong><span>安全通过</span></div><div><strong>{{guidelines.reduce((n,g)=>n+g.chunkCount,0)}}</strong><span>活跃知识分块</span></div></div>
  <div class="admin-grid"><article class="panel"><div class="panel-title"><h2>运行结果分布</h2><span class="safe-chip">ECharts</span></div><div ref="chart" class="chart"></div></article><article class="panel"><div class="panel-title"><h2>安全告警</h2><span class="danger-chip">{{alerts.length}} 条</span></div><div v-if="!alerts.length" class="empty">暂无安全告警。</div><div v-for="a in alerts.slice(0,6)" :key="a.id" class="alert-row"><div><strong>{{a.category}}</strong><p>{{a.redactedSummary}}</p><small>{{a.reasonCodes.join('、')}}</small></div><StatusPill :value="a.status" /></div></article></div>
  <article class="panel knowledge-panel"><div class="panel-title"><div><h2>知识库版本与对象存储</h2><span class="safe-chip">MinIO + pgvector/HNSW</span></div><el-button type="primary" :loading="reindexing" @click="reindex">重建网络语料索引</el-button></div><div v-if="!guidelines.length" class="empty">尚未入库资料，请执行受控重建。</div><div class="guideline-grid"><section v-for="g in guidelines" :key="g.guidelineId" class="guideline-card"><div class="case-head"><strong>{{g.title}}</strong><span class="safe-chip">{{g.chunkCount}} chunks</span></div><p>{{g.publisher}} · {{g.activeVersion}}</p><a :href="g.sourceUrl" target="_blank" rel="noreferrer">{{g.sourceUrl}}</a><small>{{g.licenseNote}}</small><small>来源状态：{{g.sourceStatus}} · 采集方式：{{g.retrievalMethod}} · {{g.fetchedAt ? new Date(g.fetchedAt).toLocaleString() : '内置基线'}}</small><code>SHA-256: {{g.contentSha256}} · MinIO: {{g.objectKey}}</code></section></div></article>
  <article class="panel top-gap"><div class="panel-title"><h2>多角色 Agent 运行轨迹</h2><span class="safe-chip">可观察步骤，不展示隐藏推理</span></div><div v-if="!runs.length" class="empty">暂无运行记录。</div><details v-for="r in runs.slice(0,12)" :key="r.runId" class="trace-row"><summary><code>{{r.runId}}</code><span>{{r.provider}} / {{r.model}}</span><StatusPill :value="r.status"/></summary><ol><li v-for="step in r.agentTrace" :key="step">{{step}}</li></ol><p v-if="r.safetyReasons.length">安全原因：{{r.safetyReasons.join('、')}}</p></details></article>
  <article class="panel top-gap"><div class="panel-title"><h2>不可变审计时间线</h2><span class="safe-chip">最近 100 条</span></div><div class="audit-table"><div class="table-head"><span>时间</span><span>动作</span><span>对象</span><span>结果</span><span>Request ID</span></div><div v-for="a in audits" :key="a.id" class="table-row"><span>{{new Date(a.createdAt).toLocaleString()}}</span><strong>{{a.action}}</strong><span>{{a.targetType}}</span><span>{{a.result}}</span><code>{{a.requestId.slice(0,12)}}…</code></div></div></article></section></template>
