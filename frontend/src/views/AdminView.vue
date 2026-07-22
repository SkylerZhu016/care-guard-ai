<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { BarChart } from 'echarts/charts'
import { GridComponent } from 'echarts/components'
import { init, use, type EChartsType } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { ElMessage } from 'element-plus'
import { ApiClient } from '../api'
import { useSessionStore } from '../stores/session'
import type { Alert, Audit, Run } from '../types'
import StatusPill from '../components/StatusPill.vue'

use([BarChart, GridComponent, CanvasRenderer])
const session=useSessionStore(),api=new ApiClient(()=>session.token),alerts=ref<Alert[]>([]),audits=ref<Audit[]>([]),runs=ref<Run[]>([]),chart=ref<HTMLElement>()
let chartInstance: EChartsType | undefined
async function load(){[alerts.value,audits.value,runs.value]=await Promise.all([api.alerts(),api.audits(),api.runs()]);await nextTick();const counts=['SUCCEEDED','BLOCKED','FAILED'].map(s=>runs.value.filter(r=>r.status===s).length);if(chart.value){chartInstance=init(chart.value);chartInstance.setOption({grid:{left:28,right:10,top:20,bottom:25},xAxis:{type:'category',data:['成功','阻断','失败']},yAxis:{type:'value',minInterval:1},series:[{type:'bar',data:counts,itemStyle:{color:(p:any)=>['#1b8a70','#d98b2b','#c64b55'][p.dataIndex],borderRadius:[6,6,0,0]}}]})}}
onMounted(()=>load().catch((e:any)=>ElMessage.error(e.message)))
onUnmounted(()=>chartInstance?.dispose())
</script>
<template><section class="workspace"><div class="page-heading"><div><span class="eyebrow">GOVERNANCE & OBSERVABILITY</span><h1>安全与审计中心</h1><p>用真实运行记录解释每一次成功、阻断与降级；摘要均已最小化和脱敏。</p></div><span class="simulation-badge dark">知识库 demo-kb-v1 · 规则 red-flags-v1</span></div><div class="metrics"><div><strong>{{runs.length}}</strong><span>Agent 运行</span></div><div><strong>{{alerts.filter(a=>a.status==='OPEN').length}}</strong><span>开放告警</span></div><div><strong>{{runs.filter(r=>r.safetyDecision==='PASS').length}}</strong><span>安全通过</span></div><div><strong>{{audits.length}}</strong><span>审计事件</span></div></div>
  <div class="admin-grid"><article class="panel"><div class="panel-title"><h2>运行结果分布</h2><span class="safe-chip">ECharts</span></div><div ref="chart" class="chart"></div></article><article class="panel"><div class="panel-title"><h2>安全告警</h2><span class="danger-chip">{{alerts.length}} 条</span></div><div v-if="!alerts.length" class="empty">暂无安全告警。</div><div v-for="a in alerts.slice(0,6)" :key="a.id" class="alert-row"><div><strong>{{a.category}}</strong><p>{{a.redactedSummary}}</p><small>{{a.reasonCodes.join('、')}}</small></div><StatusPill :value="a.status" /></div></article></div>
  <article class="panel"><div class="panel-title"><h2>不可变审计时间线</h2><span class="safe-chip">最近 100 条</span></div><div class="audit-table"><div class="table-head"><span>时间</span><span>动作</span><span>对象</span><span>结果</span><span>Request ID</span></div><div v-for="a in audits" :key="a.id" class="table-row"><span>{{new Date(a.createdAt).toLocaleString()}}</span><strong>{{a.action}}</strong><span>{{a.targetType}}</span><span>{{a.result}}</span><code>{{a.requestId.slice(0,12)}}…</code></div></div></article></section></template>
