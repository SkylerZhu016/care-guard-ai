<script setup lang="ts">
import type { Tone } from '../types'

withDefaults(defineProps<{ label: string; tone?: Tone; dot?: boolean }>(), { tone: 'gray', dot: false })
</script>

<template>
  <span class="status-pill" :class="`is-${tone}`"><i v-if="dot" aria-hidden="true" />{{ label }}</span>
</template>
