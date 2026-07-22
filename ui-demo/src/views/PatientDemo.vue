<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, Check, CircleCheck, Clock, Delete, Plus, WarningFilled } from '@element-plus/icons-vue'
import { symptomOptions } from '../mock'
import AppModal from '../components/AppModal.vue'
import StatusPill from '../components/StatusPill.vue'

const step = ref(1)
const selected = ref(['头痛'])
const complaint = ref('主要不适为头痛，今天开始，间歇出现。')
const symptomQuery = ref('')
const onset = ref('今天')
const course = ref('间歇出现')
const activity = ref('需要短暂休息')
const current = ref('仍然存在')
const urgent = ref(false)
const saved = ref(true)
const showSubmit = ref(false)
const submitted = ref(false)
const symptomPickerOpen = ref(false)
const disclaimerAccepted = ref(false)

const hasChest = computed(() => selected.value.includes('胸口不适'))
const filteredSymptoms = computed(() => symptomOptions.filter((item) => item.includes(symptomQuery.value.trim())))
function toggleSymptom(item: string) {
  if (selected.value.includes(item)) selected.value = selected.value.filter((value) => value !== item)
  else selected.value.push(item)
  complaint.value = selected.value.length ? `主要不适为${selected.value.join('、')}。` : ''
  saved.value = false
  window.setTimeout(() => { saved.value = true }, 650)
}
function next() { if (step.value < 3) step.value += 1 }
function submit() { submitted.value = true; showSubmit.value = false }
</script>

<template>
  <section class="page-stack">
    <div v-if="submitted" class="success-banner"><CircleCheck /><div><strong>预问诊已提交</strong><p>编号 M-0722-021，已进入人工审核队列。你仍可在“问诊记录”中补充信息。</p></div><button type="button" @click="submitted = false; step = 1">再建一条</button></div>
    <header class="page-header">
      <div><span class="eyebrow">患者端 · 新建预问诊</span><h1>预问诊信息采集</h1><p>请如实填写本次不适及相关情况，所填信息将提交医务人员审核。</p></div>
      <div class="save-state"><CircleCheck v-if="saved" /><Clock v-else /><span><strong>{{ saved ? '草稿已保存' : '正在保存' }}</strong><small>可稍后继续填写</small></span></div>
    </header>

    <nav class="stepper" aria-label="预问诊步骤">
      <button v-for="(item, index) in ['选择不适', '补充信息', '核对提交']" :key="item" type="button" :class="{ active: step === index + 1, done: step > index + 1 }" @click="step = index + 1">
        <span><Check v-if="step > index + 1" />{{ step > index + 1 ? '' : index + 1 }}</span><b>{{ item }}</b><small>{{ ['确定主要不适', '填写相关情况', '确认并提交'][index] }}</small>
      </button>
    </nav>

    <div class="patient-layout">
      <article class="surface intake-card">
        <template v-if="step === 1">
          <div class="section-head"><div><span class="step-label">01 / 选择不适</span><h2>请选择本次主要不适</h2><p>可选择一项或多项，并将最主要的不适放在首位。</p></div><StatusPill :label="`${selected.length} 项已选`" tone="green" /></div>
          <button class="symptom-picker-launcher" type="button" @click="symptomPickerOpen = true">
            <span class="picker-launcher-icon"><Plus /></span>
            <span><strong>{{ selected.length ? '选择或修改不适项目' : '选择不适项目' }}</strong><small>{{ selected.length ? `当前已选择：${selected.join('、')}` : '打开不适项目列表进行选择' }}</small></span>
            <ArrowRight />
          </button>
          <div v-if="selected.length" class="selected-box compact-selected"><div><strong>已选不适</strong><small>点击标签可移除</small></div><div><button v-for="item in selected" :key="item" type="button" @click="toggleSymptom(item)">{{ item }}<Delete /></button></div>
          </div>
          <label class="form-field"><span>主诉摘要 <b>必填</b></span><textarea v-model="complaint" rows="3" maxlength="200" /><small>{{ complaint.length }} / 200</small></label>
        </template>

        <template v-else-if="step === 2">
          <div class="section-head"><div><span class="step-label">02 / 补充信息</span><h2>补充症状相关信息</h2><p>请根据实际情况填写；无法确定时可选择“不清楚”。</p></div></div>
          <div class="focus-symptom"><span>当前填写</span><strong>{{ selected[0] || '尚未选择' }}</strong><StatusPill label="主要不适" tone="blue" /></div>
          <div class="form-grid">
            <label class="form-field"><span>什么时候开始</span><select v-model="onset"><option>刚刚</option><option>今天</option><option>1–3 天前</option><option>3 天以上</option><option>不清楚</option></select></label>
            <label class="form-field"><span>持续情况</span><select v-model="course"><option>持续存在</option><option>间歇出现</option><option>已经缓解</option><option>不清楚</option></select></label>
            <label class="form-field"><span>现在是否仍存在</span><select v-model="current"><option>仍然存在</option><option>目前没有</option><option>不清楚</option></select></label>
            <label class="form-field"><span>对日常活动的影响</span><select v-model="activity"><option>没有明显影响</option><option>需要短暂休息</option><option>无法正常活动</option><option>不清楚</option></select></label>
          </div>
          <fieldset class="choice-group"><legend>是否突然出现并快速加重？</legend><button type="button" :class="{ selected: urgent }" @click="urgent = true">是</button><button type="button" :class="{ selected: !urgent }" @click="urgent = false">否</button><button type="button">不清楚</button></fieldset>
          <div v-if="hasChest" class="attention-card danger"><WarningFilled /><div><strong>与“胸口不适”有关的重点追问</strong><p>是否同时存在呼吸困难、真正失去意识或反应异常？</p><label><input v-model="urgent" type="checkbox" /> 同时存在呼吸困难</label></div></div>
        </template>

        <template v-else>
          <div class="section-head"><div><span class="step-label">03 / 核对提交</span><h2>核对并提交预问诊信息</h2><p>请确认以下内容准确完整，提交后仍可在问诊记录中补充信息。</p></div></div>
          <div v-if="urgent" class="attention-card danger"><WarningFilled /><div><strong>当前填写内容将进入优先人工审核</strong><p>如症状正在发生或快速加重，请及时联系当地急救或尽快线下就医。</p></div></div>
          <div class="review-card"><span>主要不适</span><h3>{{ complaint || '尚未填写' }}</h3><button type="button" @click="step = 1">返回修改</button></div>
          <div class="review-grid"><div><span>什么时候开始</span><strong>{{ onset }}</strong></div><div><span>持续情况</span><strong>{{ course }}</strong></div><div><span>当前状态</span><strong>{{ current }}</strong></div><div><span>活动影响</span><strong>{{ activity }}</strong></div></div>
          <div class="attention-card submit-info"><CircleCheck /><div><strong>提交后的处理流程</strong><p>预问诊信息将进入人工审核队列，你可以在“问诊记录”中查看审核进度并补充信息。</p></div></div>
          <label class="disclaimer-check"><input v-model="disclaimerAccepted" type="checkbox" /><span>我已阅读并知悉：本服务用于预问诊信息采集，不构成诊断、处方或治疗建议；如遇紧急情况，应及时联系当地急救或前往医疗机构。</span></label>
        </template>

        <footer class="form-actions"><button v-if="step > 1" class="button secondary" type="button" @click="step -= 1">上一步</button><span /><button class="button ghost" type="button">保存并稍后继续</button><button v-if="step < 3" class="button primary" type="button" :disabled="step === 1 && !selected.length" @click="next">继续下一步</button><button v-else class="button primary" type="button" :disabled="!disclaimerAccepted" @click="showSubmit = true">确认并提交</button></footer>
      </article>

      <aside class="patient-aside">
        <article class="surface help-card"><h3>填写小提示</h3><ul><li>只写当前这次不适</li><li>不知道时可以选“不清楚”</li><li>不要填写姓名、电话或地址</li></ul></article>
      </aside>
    </div>

    <AppModal :open="symptomPickerOpen" eyebrow="不适项目" title="选择本次不适项目" @close="symptomPickerOpen = false">
      <p class="modal-copy symptom-picker-copy">可选择一项或多项不适。完成后返回预问诊表单继续填写。</p>
      <label class="search-field picker-search"><span>搜索不适项目</span><input v-model="symptomQuery" type="search" placeholder="例如头痛、咳嗽、腹痛" /></label>
      <div class="symptom-grid symptom-picker-grid">
        <button v-for="item in filteredSymptoms" :key="item" type="button" :class="{ selected: selected.includes(item) }" :aria-pressed="selected.includes(item)" @click="toggleSymptom(item)">
          <span>{{ item }}</span><Check v-if="selected.includes(item)" /><Plus v-else />
        </button>
      </div>
      <div v-if="!filteredSymptoms.length" class="picker-empty">没有匹配的不适项目</div>
      <template #actions><span class="picker-count">已选择 {{ selected.length }} 项</span><button class="button primary" type="button" :disabled="!selected.length" @click="symptomPickerOpen = false">完成选择</button></template>
    </AppModal>

    <AppModal :open="showSubmit" eyebrow="最终确认" title="确认提交这份预问诊？" @close="showSubmit = false">
      <p class="modal-copy">提交后，预问诊信息将进入人工审核队列。请再次确认填写内容准确无误。</p>
      <label class="confirm-row"><input type="checkbox" checked /> 以上内容与我填写的一致</label>
      <template #actions><button class="button secondary" type="button" @click="showSubmit = false">返回检查</button><button class="button primary" type="button" @click="submit">确认并提交</button></template>
    </AppModal>
  </section>
</template>
