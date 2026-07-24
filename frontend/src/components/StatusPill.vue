<script setup lang="ts">
defineProps<{value?:string}>()
const label:Record<string,string>={DRAFT:'草稿',PENDING_REVIEW:'待人工审核',REVIEWED:'已审核',FOLLOWUP_ACTIVE:'随访中',ROUTINE:'常规',URGENT:'紧急',EMERGENCY:'急症红旗',REQUIRES_MANUAL_REVIEW:'需人工复核',RULE_EVALUATED:'规则已评估',SUCCEEDED:'分析完成',BLOCKED:'安全阻断',FAILED:'服务降级',PENDING:'待执行',IN_PROGRESS:'进行中',COMPLETED:'已完成'}
</script>
<template><span class="status-pill" :class="`status-${(value||'unknown').toLowerCase()}`">{{ label[value||''] || value || '未知' }}</span></template>

