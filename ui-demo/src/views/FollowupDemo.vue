<script setup lang="ts">
import { computed, ref } from 'vue'
import { Calendar, CircleCheck, Clock, EditPen, Filter, Phone, Search, Tickets, WarningFilled } from '@element-plus/icons-vue'
import { followupTasks } from '../mock'
import AppModal from '../components/AppModal.vue'
import PaginationBar from '../components/PaginationBar.vue'
import StatusPill from '../components/StatusPill.vue'

type FilterValue = '全部' | '待执行' | '进行中' | '已完成'
const filter = ref<FilterValue>('全部')
const query = ref('')
const page = ref(1)
const activeTask = ref<(typeof followupTasks)[number] | null>(null)
const result = ref('')
const completedIds = ref<string[]>([])
const filtered = computed(() => followupTasks.filter((task) => (filter.value === '全部' || task.status === filter.value) && `${task.title}${task.patient}${task.id}`.includes(query.value.trim())))

function openTask(task: (typeof followupTasks)[number]) { activeTask.value = task; result.value = task.status === '进行中' ? '已电话联系，当前症状较提交时有所缓解。' : '' }
function complete() { if (activeTask.value) completedIds.value.push(activeTask.value.id); activeTask.value = null }
</script>

<template>
  <section class="page-stack">
    <header class="page-header"><div><span class="eyebrow">随访端 · 今日任务</span><h1>把每一次联系做好</h1><p>任务只来自医务人员已激活的计划，按时完成并留下清楚、可追溯的结果。</p></div><button class="button primary" type="button"><Calendar />查看日程</button></header>

    <div class="metric-grid follow-metrics">
      <button type="button" :class="{ active: filter === '全部' }" @click="filter = '全部'"><span class="metric-icon green"><Tickets /></span><span><small>今日任务</small><strong>8</strong><em>共 12 项待办</em></span></button>
      <button type="button" :class="{ active: filter === '待执行' }" @click="filter = '待执行'"><span class="metric-icon amber"><Clock /></span><span><small>待执行</small><strong>5</strong><em>2 项即将到期</em></span></button>
      <button type="button" :class="{ active: filter === '进行中' }" @click="filter = '进行中'"><span class="metric-icon blue"><Phone /></span><span><small>进行中</small><strong>2</strong><em>等待补充结果</em></span></button>
      <button type="button" :class="{ active: filter === '已完成' }" @click="filter = '已完成'"><span class="metric-icon gray"><CircleCheck /></span><span><small>今日已完成</small><strong>{{ 6 + completedIds.length }}</strong><em>完成率 75%</em></span></button>
    </div>

    <div class="scope-banner"><CircleCheck /><div><strong>任务来源清楚可追溯</strong><p>任务标题、执行说明和截止时间均来自已激活计划，不自动关联疾病或诊断。</p></div><button type="button">了解任务规则</button></div>

    <article class="surface task-surface">
      <header class="surface-head"><div><span class="eyebrow">任务列表</span><h2>我的待办</h2></div><div class="toolbar"><label class="compact-search"><Search /><input v-model="query" type="search" placeholder="搜索任务或测试患者" /></label><button class="icon-button" type="button" aria-label="筛选"><Filter /></button></div></header>
      <div class="filter-chips"><button v-for="item in ['全部', '待执行', '进行中', '已完成'] as FilterValue[]" :key="item" type="button" :class="{ active: filter === item }" @click="filter = item">{{ item }}</button></div>
      <div v-if="!filtered.length" class="empty-state"><CircleCheck /><strong>当前筛选下没有任务</strong><p>可以调整状态或搜索条件。</p></div>
      <div v-else class="task-table">
        <div class="task-table-head"><span>任务</span><span>关联对象</span><span>截止时间</span><span>状态</span><span>操作</span></div>
        <article v-for="task in filtered" :key="task.id" :class="{ overdue: task.status === '已逾期' }">
          <div class="task-main"><span class="task-type"><Phone /></span><div><small>{{ task.id }}</small><strong>{{ task.title }}</strong><p>{{ task.description }}</p></div></div>
          <div class="task-patient"><strong>{{ task.patient }}</strong><small>信息已脱敏</small></div>
          <div class="task-due"><Clock /><span><strong>{{ task.due }}</strong><small>{{ task.status === '已逾期' ? '已超过计划时间' : 'Asia/Shanghai' }}</small></span></div>
          <div><StatusPill :label="completedIds.includes(task.id) ? '已完成' : task.status" :tone="completedIds.includes(task.id) ? 'green' : task.tone" dot /></div>
          <div><button class="table-action" type="button" @click="openTask(task)">{{ task.status === '待执行' ? '开始任务' : task.status === '已逾期' ? '立即处理' : '继续记录' }}</button></div>
        </article>
      </div>
      <footer class="table-footer"><span>显示 {{ filtered.length }} 项模拟任务</span><PaginationBar :page="page" :total="3" @change="page = $event" /></footer>
    </article>

    <AppModal :open="!!activeTask" eyebrow="随访任务" :title="activeTask?.title || ''" @close="activeTask = null">
      <div v-if="activeTask" class="task-modal-content"><div class="modal-summary"><span>任务编号</span><strong>{{ activeTask.id }}</strong><span>关联对象</span><strong>{{ activeTask.patient }}</strong><span>截止时间</span><strong>{{ activeTask.due }}</strong></div><div class="attention-card"><WarningFilled /><div><strong>执行边界</strong><p>按计划复核并记录事实；如发现异常或无法判断，请转交医务人员。</p></div></div><label class="form-field"><span>结果摘要 <b>必填</b></span><textarea v-model="result" rows="5" placeholder="记录联系结果、症状变化与下一步安排" /></label><label class="confirm-row"><input type="checkbox" /> 需要转交医务人员复核</label></div>
      <template #actions><button class="button secondary" type="button" @click="activeTask = null"><EditPen />保存草稿</button><button class="button primary" type="button" :disabled="!result.trim()" @click="complete"><CircleCheck />完成任务</button></template>
    </AppModal>
  </section>
</template>
