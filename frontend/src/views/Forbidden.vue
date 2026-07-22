<template>
  <div class="result-wrap">
    <el-result icon="error" title="403 无权限访问" sub-title="您没有访问该页面的权限">
      <template #extra>
        <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>
<style scoped>
.result-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f5f7fa; }
</style>
