import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PatientView from './PatientView.vue'

const state=vi.hoisted(()=>({intakeCatalog:vi.fn(),myVisits:vi.fn(),myTasks:vi.fn(),patientProfile:vi.fn(),createVisit:vi.fn(),updateVisit:vi.fn(),submitVisit:vi.fn(),savePatientProfile:vi.fn(),supplementVisit:vi.fn()}))
vi.mock('../api',()=>({ApiClient:class{intakeCatalog=state.intakeCatalog;myVisits=state.myVisits;myTasks=state.myTasks;patientProfile=state.patientProfile;createVisit=state.createVisit;updateVisit=state.updateVisit;submitVisit=state.submitVisit;savePatientProfile=state.savePatientProfile;supplementVisit=state.supplementVisit}}))
vi.mock('../stores/session',()=>({useSessionStore:()=>({token:'test-token'})}))
vi.mock('element-plus',()=>({ElMessage:{success:vi.fn(),error:vi.fn(),warning:vi.fn(),info:vi.fn()},ElMessageBox:{confirm:vi.fn(),prompt:vi.fn()}}))

const ButtonStub=defineComponent({props:['disabled'],emits:['click'],template:'<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'})
const InputStub=defineComponent({props:['modelValue'],emits:['update:modelValue'],template:'<input :value="modelValue" @input="$emit(\'update:modelValue\',$event.target.value)" />'})
const SlotStub=defineComponent({template:'<div><slot /></div>'})
const mountView=()=>mount(PatientView,{global:{directives:{loading:()=>{}},stubs:{Teleport:true,'el-button':ButtonStub,'el-input':InputStub,'el-select':SlotStub,'el-option':SlotStub,'el-form':SlotStub,'el-form-item':SlotStub}}})
const profile={ownerId:'1',version:0,data:{ageBand:'UNKNOWN',physiologicalInfoStatus:'UNKNOWN',physiologicalInfo:'',chronicConditionsStatus:'UNKNOWN',chronicConditions:[],allergiesStatus:'UNKNOWN',allergies:[],longTermMedicationsStatus:'UNKNOWN',longTermMedications:[]}}
const catalog={version:'intake-catalog-test',symptoms:[
  {code:'CHEST_PAIN',name:'胸痛',category:'胸部与呼吸',supportLevel:'RULE_SUPPORTED',common:true,questions:[{id:'chest.current',prompt:'现在有没有胸口疼、发紧或不舒服？',type:'SINGLE_CHOICE',multiple:false,options:[{value:'YES',label:'是'},{value:'NO',label:'否'},{value:'UNKNOWN',label:'不知道/说不清'}]}]},
  {code:'HEADACHE',name:'头痛',category:'头部与神经',supportLevel:'RECORD_ONLY',common:true,questions:[]},
  {code:'OTHER',name:'其他不适',category:'其他',supportLevel:'CUSTOM',common:false,questions:[]},
]}

describe('PatientView v2 intake',()=>{
  beforeEach(()=>{localStorage.clear();state.intakeCatalog.mockReset().mockResolvedValue(catalog);state.myVisits.mockReset().mockResolvedValue([]);state.myTasks.mockReset().mockResolvedValue([]);state.patientProfile.mockReset().mockResolvedValue(profile)})

  it('has four patient areas and no numeric severity control',async()=>{
    const wrapper=mountView();await flushPromises()
    expect(wrapper.text()).toContain('新建预问诊')
    expect(wrapper.text()).toContain('问诊记录')
    expect(wrapper.text()).toContain('随访任务')
    expect(wrapper.text()).toContain('健康资料')
    expect(wrapper.text()).not.toContain('/ 10')
    expect(wrapper.find('input[type="range"]').exists()).toBe(false)
  })

  it('uses patient language and hides technical symptom codes',async()=>{
    const wrapper=mountView();await flushPromises()
    await wrapper.find('.symptom-picker-launcher').trigger('click')
    await wrapper.findAll('button').find(button=>button.text().includes('胸痛'))!.trigger('click')
    expect(wrapper.findAll('input').some(input=>input.element.value==='主要不适为胸痛')).toBe(true)
    expect(wrapper.text()).not.toContain('CHEST_PAIN')
  })

  it('explains record-only symptoms without declaring routine risk',async()=>{
    const wrapper=mountView();await flushPromises()
    await wrapper.find('.symptom-picker-launcher').trigger('click')
    await wrapper.findAll('button').find(button=>button.text().includes('头痛'))!.trigger('click')
    expect(wrapper.text()).toContain('该症状会被记录并交由人工复核')
    expect(wrapper.text()).not.toContain('筛查通过')
  })
})
