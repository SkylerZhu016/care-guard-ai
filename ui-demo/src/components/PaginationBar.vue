<script setup lang="ts">
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'

defineProps<{ page: number; total: number }>()
defineEmits<{ change: [page: number] }>()
</script>

<template>
  <nav class="pagination" aria-label="分页">
    <button type="button" :disabled="page === 1" aria-label="上一页" @click="$emit('change', page - 1)"><ArrowLeft /></button>
    <button v-for="item in total" :key="item" type="button" :class="{ active: page === item }" @click="$emit('change', item)">{{ item }}</button>
    <button type="button" :disabled="page === total" aria-label="下一页" @click="$emit('change', page + 1)"><ArrowRight /></button>
  </nav>
</template>
