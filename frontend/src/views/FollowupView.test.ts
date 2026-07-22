import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Task } from '../types'
import FollowupView from './FollowupView.vue'

const state = vi.hoisted(() => ({ tasks: [] as Task[], updateTask: vi.fn(), myTasks: vi.fn() }))
vi.mock('../api', () => ({ ApiClient: class { myTasks = state.myTasks; updateTask = state.updateTask } }))
vi.mock('../stores/session', () => ({ useSessionStore: () => ({ token: 'test-token' }) }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), error: vi.fn() } }))

const ButtonStub = defineComponent({ props: { disabled: Boolean }, emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' })
const InputStub = defineComponent({ props: ['modelValue'], emits: ['update:modelValue'], template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' })
const task = (id:string,status:Task['status']):Task => ({ id, planId:'p1', taskCode:id, title:`任务 ${id}`, dueAt:'2026-07-30T08:00:00Z', status })
const mountView = () => mount(FollowupView, { global:{ stubs:{ 'el-button':ButtonStub, 'el-input':InputStub, 'el-select':true, 'el-option':true, 'el-pagination':true } } })

describe('FollowupView state machine controls', () => {
  beforeEach(() => {
    state.tasks = []
    state.updateTask.mockReset().mockResolvedValue({})
    state.myTasks.mockReset().mockImplementation(async () => state.tasks)
  })

  it('shows start for pending tasks and does not allow direct completion', async () => {
    state.tasks = [task('pending','PENDING')]
    const wrapper = mountView(); await flushPromises()
    expect(wrapper.text()).toContain('开始')
    expect(wrapper.text()).not.toContain('完成任务')
    await wrapper.get('button').trigger('click')
    expect(state.updateTask).toHaveBeenCalledWith('pending','IN_PROGRESS')
  })

  it('keeps a separate result summary for each in-progress task', async () => {
    state.tasks = [task('first','IN_PROGRESS'),task('second','IN_PROGRESS')]
    const wrapper = mountView(); await flushPromises()
    const inputs = wrapper.findAll('textarea')
    await inputs[0].setValue('第一项摘要')
    await inputs[1].setValue('第二项摘要')
    const buttons = wrapper.findAll('button')
    await buttons[0].trigger('click'); await flushPromises()
    await buttons[1].trigger('click'); await flushPromises()
    expect(state.updateTask).toHaveBeenNthCalledWith(1,'first','COMPLETED','第一项摘要')
    expect(state.updateTask).toHaveBeenNthCalledWith(2,'second','COMPLETED','第二项摘要')
  })
})
