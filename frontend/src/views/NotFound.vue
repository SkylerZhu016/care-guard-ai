<template>
  <div class="result-wrap">
    <el-result icon="warning" title="404 页面不存在" sub-title="您访问的页面不存在或已被移除">
      <template #extra>
        <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>
<style scoped>
.result-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f5f7fa; }
</style>
