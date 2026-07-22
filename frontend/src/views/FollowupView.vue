<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Calendar, CircleCheck, Clock, Tickets } from '@element-plus/icons-vue'
import { ApiClient } from '../api'
import { formatDateTime, taskPresentation } from '../domain/presentation'
import { useSessionStore } from '../stores/session'
import type { Task } from '../types'
import StatusPill from '../components/StatusPill.vue'

const session=useSessionStore(),api=new ApiClient(()=>session.token),tasks=ref<Task[]>([]),filter=ref('ACTIVE'),page=ref(1),pageSize=6
const notes=reactive<Record<string,string>>({})
const filtered=computed(()=>tasks.value.filter(task=>filter.value==='ALL'||(filter.value==='ACTIVE'?task.status!=='COMPLETED':task.status===filter.value)))
const pageTasks=computed(()=>filtered.value.slice((page.value-1)*pageSize,page.value*pageSize))
const counts=computed(()=>({active:tasks.value.filter(t=>t.status!=='COMPLETED').length,pending:tasks.value.filter(t=>t.status==='PENDING').length,inProgress:tasks.value.filter(t=>t.status==='IN_PROGRESS').length,completed:tasks.value.filter(t=>t.status==='COMPLETED').length}))
async function load(){tasks.value=await api.myTasks();for(const task of tasks.value)if(notes[task.id]===undefined)notes[task.id]=task.resultSummary||'';page.value=Math.min(page.value,Math.max(1,Math.ceil(filtered.value.length/pageSize)))}
async function start(task:Task){try{await api.updateTask(task.id,'IN_PROGRESS');ElMessage.success('任务已开始，完成后请填写结果摘要');await load()}catch(e:any){ElMessage.error(e.message)}}
async function complete(task:Task){try{await api.updateTask(task.id,'COMPLETED',notes[task.id]);ElMessage.success('任务已完成并写入审计记录');await load()}catch(e:any){ElMessage.error(e.message)}}
watch(filter,()=>page.value=1)
onMounted(()=>load().catch((e:any)=>ElMessage.error(e.message)))
</script>

<template>
  <section class="workspace followup-workspace">
    <div class="page-heading"><div><span class="eyebrow">FOLLOW-UP OPERATIONS</span><h1>随访任务工作台</h1><p>这里只显示医务人员明确选择模板并激活后产生的任务。</p></div><div class="stat-card"><strong>{{counts.active}}</strong><span>当前待办</span></div></div>
    <div class="task-metrics" aria-label="任务状态统计"><button type="button" :class="{active:filter==='ACTIVE'}" @click="filter='ACTIVE'"><Tickets/><span>全部待办</span><strong>{{counts.active}}</strong></button><button type="button" :class="{active:filter==='PENDING'}" @click="filter='PENDING'"><Calendar/><span>待执行</span><strong>{{counts.pending}}</strong></button><button type="button" :class="{active:filter==='IN_PROGRESS'}" @click="filter='IN_PROGRESS'"><Clock/><span>进行中</span><strong>{{counts.inProgress}}</strong></button><button type="button" :class="{active:filter==='COMPLETED'}" @click="filter='COMPLETED'"><CircleCheck/><span>已完成</span><strong>{{counts.completed}}</strong></button></div>
    <div class="coverage-banner compact-banner" role="note"><CircleCheck/><div><strong>任务来源可追溯</strong><p>任务并不自动属于某种疾病；标题和说明来自医务人员明确激活的随访计划。</p></div></div>
    <article class="panel">
      <div class="panel-title"><div><Tickets/><h2>我的任务</h2></div><div class="toolbar"><el-select v-model="filter" aria-label="任务状态筛选"><el-option label="全部待办" value="ACTIVE"/><el-option label="待执行" value="PENDING"/><el-option label="进行中" value="IN_PROGRESS"/><el-option label="已完成" value="COMPLETED"/><el-option label="全部记录" value="ALL"/></el-select><span class="safe-chip">{{filtered.length}} 项</span></div></div>
      <div v-if="!filtered.length" class="empty-state large"><CircleCheck/><strong>当前筛选下没有任务</strong><p>新的任务只有在适用模板被医务人员激活后才会出现。</p></div>
      <div class="task-board" aria-live="polite"><article v-for="task in pageTasks" :key="task.id" class="follow-card"><div class="task-icon"><Calendar/></div><div class="grow"><small class="task-code">{{task.taskCode}}</small><h3>{{taskPresentation(task.taskCode).label}}</h3><p class="task-description">{{taskPresentation(task.taskCode).description}}</p><p class="due-date">截止 {{formatDateTime(task.dueAt)}}</p><el-input v-if="task.status==='IN_PROGRESS'" v-model="notes[task.id]" type="textarea" :rows="3" maxlength="500" show-word-limit :aria-label="`${taskPresentation(task.taskCode).label}结果摘要`" placeholder="完成前填写本任务结果摘要"/><p v-else-if="task.resultSummary" class="result-summary"><strong>结果摘要</strong>{{task.resultSummary}}</p></div><div class="task-action"><StatusPill :value="task.status"/><el-button v-if="task.status==='PENDING'" type="primary" plain @click="start(task)">开始任务</el-button><el-button v-if="task.status==='IN_PROGRESS'" type="primary" :disabled="!notes[task.id]?.trim()" @click="complete(task)">完成任务</el-button></div></article></div>
      <el-pagination v-if="filtered.length>pageSize" v-model:current-page="page" :page-size="pageSize" :total="filtered.length" layout="prev, pager, next" class="pagination"/>
    </article>
  </section>
</template>
