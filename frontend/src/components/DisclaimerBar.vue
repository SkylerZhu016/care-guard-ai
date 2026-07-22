<template>
  <div class="disclaimer">
    <el-icon style="vertical-align: -2px"><WarningFilled /></el-icon>
    {{ text }}
  </div>
</template>

<script setup lang="ts">
import { DISCLAIMER } from '@/utils/format'
withDefaults(defineProps<{ text?: string }>(), { text: DISCLAIMER })
</script>
