import { mount } from '@vue/test-utils'; import { describe,expect,it } from 'vitest'; import StatusPill from './StatusPill.vue'
describe('StatusPill',()=>{
  it('renders text in addition to risk color',()=>{const wrapper=mount(StatusPill,{props:{value:'EMERGENCY'}});expect(wrapper.text()).toContain('急症红旗');expect(wrapper.classes()).toContain('status-emergency')})
  it('translates manual review status for the clinician queue',()=>{const wrapper=mount(StatusPill,{props:{value:'REQUIRES_MANUAL_REVIEW'}});expect(wrapper.text()).toBe('需人工复核')})
})

