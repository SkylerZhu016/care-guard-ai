import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PatientView from './PatientView.vue'

const state=vi.hoisted(()=>({myVisits:vi.fn(),myTasks:vi.fn(),createVisit:vi.fn(),updateVisit:vi.fn(),submitVisit:vi.fn()}))
vi.mock('../api',()=>({ApiClient:class{myVisits=state.myVisits;myTasks=state.myTasks;createVisit=state.createVisit;updateVisit=state.updateVisit;submitVisit=state.submitVisit}}))
vi.mock('../stores/session',()=>({useSessionStore:()=>({token:'test-token'})}))
vi.mock('element-plus',()=>({ElMessage:{success:vi.fn(),error:vi.fn(),warning:vi.fn(),info:vi.fn()},ElMessageBox:{confirm:vi.fn()}}))

const ButtonStub=defineComponent({props:['disabled'],emits:['click'],template:'<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'})
const InputStub=defineComponent({props:['modelValue'],emits:['update:modelValue'],template:'<input :value="modelValue" @input="$emit(\'update:modelValue\',$event.target.value)" />'})
const SlotStub=defineComponent({template:'<div><slot /></div>'})
const mountView=()=>mount(PatientView,{global:{stubs:{'el-button':ButtonStub,'el-input':InputStub,'el-select':SlotStub,'el-option':SlotStub,'el-form':SlotStub,'el-form-item':SlotStub,'el-slider':SlotStub,'el-pagination':SlotStub,'el-icon':SlotStub}}})

describe('PatientView controlled intake',()=>{
  beforeEach(()=>{state.myVisits.mockReset().mockResolvedValue([]);state.myTasks.mockReset().mockResolvedValue([])})

  it('starts empty and does not expose an editable technical-code field',async()=>{
    const wrapper=mountView();await flushPromises()
    expect(wrapper.text()).toContain('尚未选择症状')
    expect(wrapper.text()).not.toContain('代码，如')
    expect(wrapper.text()).not.toContain('合成胸痛伴呼吸困难')
  })

  it('loads the explicit red-flag teaching example',async()=>{
    const wrapper=mountView();await flushPromises()
    await wrapper.findAll('button').find(button=>button.text().includes('加载红旗教学示例'))!.trigger('click')
    await wrapper.findAll('button').find(button=>button.text().includes('选择症状'))!.trigger('click')
    expect(wrapper.text()).toContain('CHEST_PAIN')
    expect(wrapper.text()).toContain('DYSPNEA')
  })

  it('keeps save and submit unavailable without a supported symptom',async()=>{
    const wrapper=mountView();await flushPromises()
    const save=wrapper.findAll('button').find(button=>button.text().includes('保存完整草稿'))!
    expect(save.attributes('disabled')).toBeDefined()
  })
})
