<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <h3 class="page-title" style="margin:0;flex:1">模拟患者档案</h3>
        <el-alert type="info" title="本页为教学演示用合成患者数据" show-icon :closable="false" style="flex:2" />
        <el-button type="primary" @click="openEdit()">新建档案</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="patientNo" label="编号" width="130" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column prop="gender" label="性别" width="70" />
        <el-table-column prop="birthDate" label="出生日期" width="110" />
        <el-table-column prop="phone" label="电话" width="140" />
        <el-table-column label="慢病标签" min-width="150">
          <template #default="{ row }">
            <el-tag v-for="t in row.chronicTags" :key="t" size="small" style="margin-right:4px">{{ t }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="primary" @click="openDetail(row.id)">管理</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无档案，点击右上角新建" /></template>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="total, prev, pager, next" class="mt-12" @current-change="load" />
    </div>

    <!-- 新建/编辑档案 -->
    <el-dialog v-model="dlg" :title="editForm.id ? '编辑档案' : '新建档案'" width="560px">
      <el-form label-width="90px">
        <el-form-item label="姓名" required><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="editForm.gender"><el-radio value="男">男</el-radio><el-radio value="女">女</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="出生日期"><el-date-picker v-model="editForm.birthDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="editForm.phone" /></el-form-item>
        <el-form-item label="血型">
          <el-select v-model="editForm.bloodType" clearable><el-option v-for="b in ['A','B','O','AB']" :key="b" :value="b" :label="b" /></el-select>
        </el-form-item>
        <el-form-item label="慢病标签">
          <el-select v-model="editForm.chronicTags" multiple filterable allow-create class="w-100">
            <el-option v-for="t in ['高血压','糖尿病','冠心病','哮喘','慢阻肺']" :key="t" :value="t" :label="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="住址"><el-input v-model="editForm.address" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 档案管理抽屉：既往史/过敏/用药 -->
    <el-drawer v-model="drawer" :title="`档案管理 - ${detail?.name || ''}`" size="55%">
      <template v-if="detail">
        <el-descriptions :column="2" border class="mb-12">
          <el-descriptions-item label="编号">{{ detail.patientNo }}</el-descriptions-item>
          <el-descriptions-item label="血型">{{ detail.bloodType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="出生日期">{{ detail.birthDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="住址">{{ detail.address || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="flex-between mb-12"><h4 style="margin:0">既往史</h4><el-button size="small" @click="subDlg = 'history'">+ 添加</el-button></div>
        <el-table :data="detail.histories" size="small" border>
          <el-table-column prop="diseaseName" label="疾病" />
          <el-table-column prop="diagnosedAt" label="确诊时间" width="110" />
          <el-table-column prop="note" label="备注" />
          <el-table-column label="操作" width="70"><template #default="{ row }"><el-button size="small" type="danger" link @click="removeSub('histories', row.id!)">删除</el-button></template></el-table-column>
        </el-table>

        <div class="flex-between mb-12 mt-12"><h4 style="margin:0">过敏史</h4><el-button size="small" @click="subDlg = 'allergy'">+ 添加</el-button></div>
        <el-table :data="detail.allergies" size="small" border>
          <el-table-column prop="allergen" label="过敏原" />
          <el-table-column prop="reaction" label="反应" />
          <el-table-column prop="severity" label="程度" width="80" />
          <el-table-column label="操作" width="70"><template #default="{ row }"><el-button size="small" type="danger" link @click="removeSub('allergies', row.id!)">删除</el-button></template></el-table-column>
        </el-table>

        <div class="flex-between mb-12 mt-12"><h4 style="margin:0">用药记录</h4><el-button size="small" @click="subDlg = 'medication'">+ 添加</el-button></div>
        <el-table :data="detail.medications" size="small" border>
          <el-table-column prop="drugName" label="药品" />
          <el-table-column prop="dosage" label="剂量" width="90" />
          <el-table-column prop="frequency" label="频次" width="110" />
          <el-table-column label="操作" width="70"><template #default="{ row }"><el-button size="small" type="danger" link @click="removeSub('medications', row.id!)">删除</el-button></template></el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <!-- 子记录添加 -->
    <el-dialog v-model="subDlgVisible" :title="subDlgTitle" width="440px" @close="subDlg = ''">
      <el-form label-width="80px">
        <template v-if="subDlg === 'history'">
          <el-form-item label="疾病" required><el-input v-model="subForm.diseaseName" /></el-form-item>
          <el-form-item label="确诊时间"><el-date-picker v-model="subForm.diagnosedAt" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="subForm.note" /></el-form-item>
        </template>
        <template v-else-if="subDlg === 'allergy'">
          <el-form-item label="过敏原" required><el-input v-model="subForm.allergen" /></el-form-item>
          <el-form-item label="反应"><el-input v-model="subForm.reaction" /></el-form-item>
          <el-form-item label="程度">
            <el-select v-model="subForm.severity" clearable><el-option v-for="s in ['轻度','中度','重度']" :key="s" :value="s" :label="s" /></el-select>
          </el-form-item>
        </template>
        <template v-else-if="subDlg === 'medication'">
          <el-form-item label="药品" required><el-input v-model="subForm.drugName" /></el-form-item>
          <el-form-item label="剂量"><el-input v-model="subForm.dosage" /></el-form-item>
          <el-form-item label="频次"><el-input v-model="subForm.frequency" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="subDlgVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSub">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { patientApi } from '@/api/modules'
import type { Patient } from '@/types'

const loading = ref(false)
const saving = ref(false)
const rows = ref<Patient[]>([])
const page = ref(1)
const total = ref(0)
const dlg = ref(false)
const drawer = ref(false)
const detail = ref<Patient | null>(null)
const subDlg = ref('')
const subDlgVisible = ref(false)

const editForm = reactive<{ id?: number; name: string; gender: string; birthDate: string; phone: string; bloodType: string; chronicTags: string[]; address: string }>({
  name: '', gender: '男', birthDate: '', phone: '', bloodType: '', chronicTags: [], address: ''
})
const subForm = reactive<Record<string, string>>({})

const subDlgTitle = computed(() => ({ history: '添加既往史', allergy: '添加过敏史', medication: '添加用药记录' }[subDlg.value] || ''))

watch(subDlg, (v) => { if (v) subDlgVisible.value = true })

async function load(p = page.value) {
  page.value = p
  loading.value = true
  try {
    const { data } = await patientApi.page({ page: p - 1, size: 10 })
    rows.value = data.content
    total.value = data.totalElements
  } finally { loading.value = false }
}

function openEdit(row?: Patient) {
  Object.assign(editForm, row
    ? { id: row.id, name: row.name, gender: row.gender || '男', birthDate: row.birthDate || '', phone: row.phone || '', bloodType: row.bloodType || '', chronicTags: [...(row.chronicTags || [])], address: row.address || '' }
    : { id: undefined, name: '', gender: '男', birthDate: '', phone: '', bloodType: '', chronicTags: [], address: '' })
  dlg.value = true
}

async function save() {
  if (!editForm.name.trim()) return ElMessage.warning('请填写姓名')
  saving.value = true
  try {
    if (editForm.id) await patientApi.update(editForm.id, { ...editForm })
    else await patientApi.create({ ...editForm })
    ElMessage.success('已保存')
    dlg.value = false
    load()
  } finally { saving.value = false }
}

async function openDetail(id: number) {
  const { data } = await patientApi.detail(id)
  detail.value = data
  drawer.value = true
}

async function saveSub() {
  if (!detail.value) return
  saving.value = true
  try {
    const id = detail.value.id
    if (subDlg.value === 'history') await patientApi.addHistory(id, { diseaseName: subForm.diseaseName, diagnosedAt: subForm.diagnosedAt, note: subForm.note })
    else if (subDlg.value === 'allergy') await patientApi.addAllergy(id, { allergen: subForm.allergen, reaction: subForm.reaction, severity: subForm.severity })
    else if (subDlg.value === 'medication') await patientApi.addMedication(id, { drugName: subForm.drugName, dosage: subForm.dosage, frequency: subForm.frequency })
    ElMessage.success('已添加')
    subDlgVisible.value = false
    Object.keys(subForm).forEach((k) => delete subForm[k])
    openDetail(id)
  } finally { saving.value = false }
}

async function removeSub(kind: 'histories' | 'allergies' | 'medications', subId: number) {
  if (!detail.value) return
  await patientApi.removeSub(detail.value.id, kind, subId)
  ElMessage.success('已删除')
  openDetail(detail.value.id)
}

onMounted(() => load(1))
</script>
