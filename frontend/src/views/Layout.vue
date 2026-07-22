<template>
  <el-container class="layout-container">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <el-icon :size="28" color="#fff"><MedicalKit /></el-icon>
        <span v-show="!isCollapse" class="logo-text">预问诊平台</span>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="isCollapse"
        :collapse-transition="false"
        background-color="#1d1e23"
        text-color="#a6a7ad"
        active-text-color="#409eff"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.role === 'ROLE_PATIENT' || userStore.role === 'ROLE_ADMIN'" index="/pre-consult">
          <el-icon><Edit /></el-icon>
          <span>预问诊</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.role === 'ROLE_DOCTOR' || userStore.role === 'ROLE_ADMIN'" index="/doctor-workspace">
          <el-icon><Notebook /></el-icon>
          <span>医生工作台</span>
        </el-menu-item>
        <el-menu-item v-if="['ROLE_FOLLOWUP','ROLE_DOCTOR','ROLE_ADMIN'].includes(userStore.role)" index="/followup">
          <el-icon><Calendar /></el-icon>
          <span>随访管理</span>
        </el-menu-item>
        <el-menu-item v-if="['ROLE_ADMIN','ROLE_DOCTOR'].includes(userStore.role)" index="/safety">
          <el-icon><WarningFilled /></el-icon>
          <span>安全监控</span>
        </el-menu-item>
        <el-menu-item index="/guidelines">
          <el-icon><Reading /></el-icon>
          <span>指南检索</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse" size="20">
            <Fold v-if="!isCollapse" /><Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-badge :value="alertCount" :hidden="alertCount === 0" class="alert-badge">
            <el-icon size="20" color="#909399"><Bell /></el-icon>
          </el-badge>
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-avatar :size="32" icon="UserFilled" style="background:#409eff" />
              <span class="username">{{ userStore.displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>角色: {{ roleLabel }}</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="slide-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { safetyApi } from '@/api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(false)
const alertCount = ref(0)

const roleLabel = computed(() => {
  const map: Record<string, string> = {
    ROLE_PATIENT: '患者',
    ROLE_DOCTOR: '医务人员',
    ROLE_FOLLOWUP: '随访人员',
    ROLE_ADMIN: '管理员',
  }
  return map[userStore.role] || userStore.role
})

async function fetchAlertCount() {
  try {
    const res = await safetyApi.getCount()
    if (res.code === 200) alertCount.value = res.data
  } catch {}
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

onMounted(() => {
  if (['ROLE_ADMIN', 'ROLE_DOCTOR'].includes(userStore.role)) {
    fetchAlertCount()
  }
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
}
.layout-aside {
  background-color: #1d1e23;
  overflow-y: auto;
  transition: width 0.3s;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border-bottom: 1px solid #2d2e33;
}
.logo-text {
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  white-space: nowrap;
}
.el-menu {
  border-right: none;
}
.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  padding: 0 20px;
  height: 60px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.collapse-btn {
  cursor: pointer;
  color: #606266;
  padding: 4px;
}
.collapse-btn:hover {
  color: #409eff;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}
.alert-badge :deep(.el-badge__content) {
  background-color: #f56c6c;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.username {
  font-size: 14px;
  color: #303133;
}
.layout-main {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
</style>
