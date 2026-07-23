<script setup lang="ts">
import { Close } from '@element-plus/icons-vue'
defineProps<{ open:boolean; title:string; eyebrow?:string }>()
defineEmits<{ close:[] }>()
</script>

<template>
  <Teleport to="body"><Transition name="fade"><div v-if="open" class="modal-backdrop" role="presentation" @click.self="$emit('close')"><section class="modal-card" role="dialog" aria-modal="true" :aria-label="title"><header class="modal-head"><div><span v-if="eyebrow" class="eyebrow">{{eyebrow}}</span><h2>{{title}}</h2></div><button class="icon-button" type="button" aria-label="关闭弹窗" @click="$emit('close')"><Close/></button></header><slot/><footer class="modal-actions"><slot name="actions"/></footer></section></div></Transition></Teleport>
</template>
