<script setup lang="ts">
import { computed } from 'vue'
import {
  Bell, Calendar, CaretBottom, DataAnalysis, Document, FirstAidKit,
  FolderOpened, HomeFilled, Lock, Menu, Operation, Setting, Tickets, User,
} from '@element-plus/icons-vue'
import type { Role } from '../types'
import { roles } from '../mock'

export interface NavItem { id: string; label: string; icon: keyof typeof iconMap; badge?: string }

const iconMap = { Bell, Calendar, DataAnalysis, Document, FirstAidKit, FolderOpened, HomeFilled, Lock, Operation, Setting, Tickets, User }
const props = defineProps<{ role: Role; navItems: NavItem[]; activeNav: string; mobileNavOpen: boolean }>()
defineEmits<{ nav: [id: string]; role: [role: Role]; toggleMenu: []; exit: [] }>()
const currentRole = computed(() => roles.find((item) => item.id === props.role)!)
</script>

<template>
  <div class="app-frame" :class="{ 'menu-open': mobileNavOpen }">
    <a href="#page-main" class="skip-link">跳到主要内容</a>
    <header class="app-topbar">
      <button class="mobile-menu" type="button" aria-label="打开导航" @click="$emit('toggleMenu')"><Menu /></button>
      <button class="brand" type="button" @click="$emit('exit')">
        <span class="brand-icon"><FirstAidKit /></span>
        <span><strong>守望基层医疗</strong><small>预问诊与随访信息服务</small></span>
      </button>
      <div class="topbar-actions">
        <span class="demo-badge"><i /> 静态设计 Demo</span>
        <label class="role-switcher">
          <span>当前视角</span>
          <select :value="role" aria-label="切换演示角色" @change="$emit('role', ($event.target as HTMLSelectElement).value as Role)">
            <option v-for="item in roles" :key="item.id" :value="item.id">{{ item.label }}</option>
          </select>
          <CaretBottom />
        </label>
        <button class="avatar-button" type="button" aria-label="演示用户菜单">演</button>
      </div>
    </header>

    <aside class="app-sidebar">
      <div class="sidebar-context"><span>{{ currentRole.label }}</span><strong>{{ currentRole.shortLabel }}工作区</strong><p>{{ currentRole.description }}</p></div>
      <nav aria-label="角色功能导航">
        <button v-for="item in navItems" :key="item.id" type="button" :class="{ active: activeNav === item.id }" @click="$emit('nav', item.id)">
          <component :is="iconMap[item.icon]" /><span>{{ item.label }}</span><b v-if="item.badge">{{ item.badge }}</b>
        </button>
      </nav>
      <div class="sidebar-note"><Lock /><p><strong>演示数据已脱敏</strong><span>所有操作仅保存在当前页面，不会写入真实系统。</span></p></div>
    </aside>
    <button class="sidebar-scrim" type="button" aria-label="关闭导航" @click="$emit('toggleMenu')" />

    <main id="page-main" class="app-content"><slot /></main>
  </div>
</template>
