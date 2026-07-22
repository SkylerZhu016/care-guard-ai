// 轻量函数式组件：风险标签 + 状态标签
import { defineComponent, h, computed } from 'vue'
import { ElTag } from 'element-plus'
import { riskMap, visitStatusMap, runStatusMap, planStatusMap, taskStatusMap, docStatusMap } from '@/utils/format'

const maps: Record<string, Record<string, { label: string; type: never }>> = {
  risk: riskMap as never,
  visit: visitStatusMap as never,
  run: runStatusMap as never,
  plan: planStatusMap as never,
  task: taskStatusMap as never,
  doc: docStatusMap as never
}

export const StatusTag = defineComponent({
  name: 'StatusTag',
  props: { kind: { type: String, required: true }, value: { type: String, default: '' } },
  setup(props) {
    const conf = computed(() => maps[props.kind]?.[props.value] || { label: props.value || '-', type: 'info' })
    return () => h(ElTag, { type: conf.value.type, size: 'small', effect: 'light' }, () => conf.value.label)
  }
})
