<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <h3 class="page-title" style="margin:0;flex:1">规则引擎管理</h3>
        <el-select v-model="filters.category" placeholder="分类" clearable style="width: 140px" @change="load(1)">
          <el-option v-for="(v, k) in ruleCategoryMap" :key="k" :label="v" :value="k" />
        </el-select>
        <el-button @click="testDlg = true">规则测试</el-button>
        <el-button type="primary" @click="openEdit()">新建规则</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="code" label="编码" width="90" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="分类" width="100">
          <template #default="{ row }">{{ ruleCategoryMap[row.category] }}</template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" sortable />
        <el-table-column label="风险" width="90">
          <template #default="{ row }"><StatusTag kind="risk" :value="row.riskLevel" /></template>
        </el-table-column>
        <el-table-column label="版本" width="70"><template #default="{ row }">v{{ row.currentVersion }}</template></el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }"><el-switch :model-value="row.enabled" @change="(v: boolean) => toggle(row, v)" /></template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" @click="viewVersions(row)">版本</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="total, prev, pager, next" class="mt-12" @current-change="load" />
    </div>

    <el-dialog v-model="dlg" :title="editForm.id ? '编辑规则（保存生成新版本）' : '新建规则'" width="640px">
      <el-form label-width="100px">
        <el-form-item label="编码" required><el-input v-model="editForm.code" :disabled="!!editForm.id" placeholder="RF001" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editForm.category" class="w-100">
            <el-option v-for="(v, k) in ruleCategoryMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="editForm.riskLevel" class="w-100">
            <el-option v-for="(v, k) in riskMap" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="editForm.priority" :min="1" :max="999" /></el-form-item>
        <el-form-item label="条件表达式" required>
          <el-input v-model="editForm.conditionText" type="textarea" :rows="6" class="mono"
            placeholder='{"all":[{"field":"symptoms","op":"CONTAINS_ANY","value":["胸痛"]}]}' />
          <div class="muted">JSON DSL，支持 all/any/not；op：EQ/NE/IN/CONTAINS_ANY/CONTAINS_ALL/GT/LT/IS_EMPTY</div>
        </el-form-item>
        <el-form-item label="提示信息" required><el-input v-model="editForm.message" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testDlg" title="规则测试" width="560px">
      <el-form label-width="90px">
        <el-form-item label="主诉"><el-input v-model="testForm.chiefComplaint" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="伴随症状">
          <el-select v-model="testForm.accompanying" multiple filterable allow-create class="w-100">
            <el-option v-for="s in commonSymptoms" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="特殊人群">
          <el-select v-model="testForm.specialGroup" class="w-100">
            <el-option v-for="(v, k) in specialGroupMap" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testDlg = false">关闭</el-button>
        <el-button type="primary" :loading="testing" @click="runTest">执行测试</el-button>
      </template>
      <div v-if="testHits.length" class="mt-12">
        <el-alert v-for="h in testHits" :key="h.ruleCode" :type="h.riskLevel === 'CRITICAL' || h.riskLevel === 'HIGH' ? 'error' : 'warning'"
          :title="`${h.ruleCode} ${h.ruleName}：${h.message}`" :closable="false" class="mb-12" show-icon />
      </div>
      <el-alert v-else-if="tested" type="success" title="未命中任何规则" :closable="false" show-icon />
    </el-dialog>

    <el-drawer v-model="verDrawer" title="版本历史" size="40%">
      <el-timeline>
        <el-timeline-item v-for="v in versions" :key="v.id" :timestamp="`v${v.version} · ${fmtTime(v.createdAt)}`">
          <div class="mono json-view">{{ JSON.stringify(v.conditionExpr, null, 2) }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ruleApi } from '@/api/modules'
import { StatusTag } from '@/components/Tags'
import type { RiskLevel, RuleCategory, RuleDefinition, RuleHit } from '@/types'
import { commonSymptoms, fmtTime, riskMap, ruleCategoryMap, specialGroupMap } from '@/utils/format'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const tested = ref(false)
const rows = ref<RuleDefinition[]>([])
const page = ref(1)
const total = ref(0)
const filters = reactive({ category: '' })
const dlg = ref(false)
const testDlg = ref(false)
const verDrawer = ref(false)
const versions = ref<{ id: number; version: number; conditionExpr: unknown; createdAt: string }[]>([])
const testHits = ref<RuleHit[]>([])

const editForm = reactive<{ id?: number; code: string; name: string; category: RuleCategory; riskLevel: RiskLevel; priority: number; conditionText: string; message: string }>({
  code: '', name: '', category: 'RED_FLAG', riskLevel: 'HIGH', priority: 100, conditionText: '', message: ''
})
const testForm = reactive({ chiefComplaint: '', accompanying: [] as string[], specialGroup: 'NONE' })

async function load(p = page.value) {
  page.value = p
  loading.value = true
  try {
    const { data } = await ruleApi.page({ page: p - 1, size: 10, category: filters.category || undefined })
    rows.value = data.content
    total.value = data.totalElements
  } finally { loading.value = false }
}

function openEdit(row?: RuleDefinition) {
  Object.assign(editForm, row
    ? { id: row.id, code: row.code, name: row.name, category: row.category, riskLevel: row.riskLevel, priority: row.priority, conditionText: JSON.stringify(row.conditionExpr, null, 2), message: row.message }
    : { id: undefined, code: '', name: '', category: 'RED_FLAG', riskLevel: 'HIGH', priority: 100, conditionText: '', message: '' })
  dlg.value = true
}

async function save() {
  let expr: unknown
  try { expr = JSON.parse(editForm.conditionText) } catch { return ElMessage.error('条件表达式不是合法 JSON') }
  saving.value = true
  try {
    const payload = { code: editForm.code, name: editForm.name, category: editForm.category, riskLevel: editForm.riskLevel, priority: editForm.priority, conditionExpr: expr, message: editForm.message }
    if (editForm.id) await ruleApi.update(editForm.id, payload)
    else await ruleApi.create(payload)
    ElMessage.success('已保存')
    dlg.value = false
    load()
  } finally { saving.value = false }
}

async function toggle(row: RuleDefinition, enabled: boolean) {
  await ruleApi.toggle(row.id, enabled)
  row.enabled = enabled
}

async function runTest() {
  testing.value = true
  tested.value = false
  try {
    const { data } = await ruleApi.test({
      formData: {
        basic: { specialGroup: testForm.specialGroup as never },
        chiefComplaint: testForm.chiefComplaint,
        accompanying: testForm.accompanying
      }
    })
    testHits.value = data
    tested.value = true
  } finally { testing.value = false }
}

async function viewVersions(row: RuleDefinition) {
  const { data } = await ruleApi.versions(row.id)
  versions.value = data
  verDrawer.value = true
}

onMounted(() => load(1))
</script>
