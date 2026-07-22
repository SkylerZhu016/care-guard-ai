<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ApiClient } from '../api'
import { useSessionStore } from '../stores/session'
import type { Task } from '../types'
import StatusPill from '../components/StatusPill.vue'

const session=useSessionStore(),api=new ApiClient(()=>session.token),tasks=ref<Task[]>([]),filter=ref('ACTIVE'),page=ref(1),pageSize=6
const notes=reactive<Record<string,string>>({})
const filtered=computed(()=>tasks.value.filter(t=>filter.value==='ALL'||(filter.value==='ACTIVE'?t.status!=='COMPLETED':t.status===filter.value)))
const pageTasks=computed(()=>filtered.value.slice((page.value-1)*pageSize,page.value*pageSize))
async function load(){tasks.value=await api.myTasks();for(const task of tasks.value)if(notes[task.id]===undefined)notes[task.id]=task.resultSummary||'';page.value=Math.min(page.value,Math.max(1,Math.ceil(filtered.value.length/pageSize)))}
async function start(task:Task){try{await api.updateTask(task.id,'IN_PROGRESS');ElMessage.success('任务已开始');await load()}catch(e:any){ElMessage.error(e.message)}}
async function complete(task:Task){try{await api.updateTask(task.id,'COMPLETED',notes[task.id]);ElMessage.success('任务已完成并写入审计');await load()}catch(e:any){ElMessage.error(e.message)}}
watch(filter,()=>page.value=1)
onMounted(()=>load().catch((e:any)=>ElMessage.error(e.message)))
</script>

<template><section class="workspace"><div class="page-heading"><div><span class="eyebrow">FOLLOW-UP OPERATIONS</span><h1>随访任务看板</h1><p>每项任务独立记录；必须先开始，再填写摘要并完成。</p></div><div class="stat-card"><strong>{{tasks.filter(t=>t.status!=='COMPLETED').length}}</strong><span>待办任务</span></div></div><div class="panel"><div class="panel-title"><h2>我的任务</h2><div class="toolbar"><el-select v-model="filter" aria-label="任务状态筛选"><el-option label="未完成" value="ACTIVE"/><el-option label="待执行" value="PENDING"/><el-option label="进行中" value="IN_PROGRESS"/><el-option label="已完成" value="COMPLETED"/><el-option label="全部" value="ALL"/></el-select><span class="safe-chip">高血压教学模板 v1</span></div></div><div v-if="!filtered.length" class="empty large">当前筛选条件下没有任务。</div><div class="task-board"><article v-for="task in pageTasks" :key="task.id" class="follow-card"><div class="task-icon">✓</div><div class="grow"><small>{{task.taskCode}}</small><h3>{{task.title}}</h3><p>截止 {{new Date(task.dueAt).toLocaleString()}}</p><el-input v-if="task.status==='IN_PROGRESS'" v-model="notes[task.id]" type="textarea" :rows="2" placeholder="完成前填写本任务结果摘要"/><p v-else-if="task.resultSummary" class="result-summary">结果：{{task.resultSummary}}</p></div><div class="task-action"><StatusPill :value="task.status"/><el-button v-if="task.status==='PENDING'" type="primary" plain @click="start(task)">开始</el-button><el-button v-if="task.status==='IN_PROGRESS'" type="primary" :disabled="!notes[task.id]?.trim()" @click="complete(task)">完成</el-button></div></article></div><el-pagination v-if="filtered.length>pageSize" v-model:current-page="page" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next" class="pagination"/></div></section></template>
