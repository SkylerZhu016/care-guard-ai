<script setup lang="ts">
import { ref } from 'vue'; import { useRouter } from 'vue-router'; import { ElMessage } from 'element-plus'
import { ApiClient } from '../api'; import { useSessionStore } from '../stores/session'
const session=useSessionStore(), router=useRouter(); const username=ref('patient'), password=ref('Demo123!'), loading=ref(false)
const roles=[['patient','模拟患者','填写预问诊与查看任务'],['clinician','医务人员','规则、AI 与人工审核'],['followup','随访人员','执行慢病随访任务'],['admin','管理员','安全告警与审计追踪']]
async function login(){loading.value=true;try{const data=await new ApiClient(()=>null).login(username.value,password.value);session.establish(data.accessToken,data.user);await router.push(session.home())}catch(e:any){ElMessage.error(e.message)}finally{loading.value=false}}
function choose(value:string){username.value=value;password.value='Demo123!'}
</script>
<template>
  <section class="login-page">
    <div class="login-intro"><span class="eyebrow">SAFETY-FIRST CARE WORKFLOW</span><h1>让每一次教学问诊<br><em>有规则、有证据、有复核</em></h1><p>确定性规则承担安全底线，AI 只做信息整理与指南检索，最终决定始终由模拟医务人员完成。</p>
      <div class="safety-points"><span>✓ 合成数据</span><span>✓ 规则优先</span><span>✓ 人工终审</span><span>✓ 全程审计</span></div>
    </div>
    <div class="login-card"><div class="card-kicker">演示入口</div><h2>选择角色并登录</h2><p class="muted">四个账号密码均为 Demo123!</p>
      <div class="role-grid"><button v-for="role in roles" :key="role[0]" :class="{active:username===role[0]}" @click="choose(role[0])"><strong>{{role[1]}}</strong><small>{{role[2]}}</small></button></div>
      <el-form label-position="top" @submit.prevent="login"><el-form-item label="账号"><el-input v-model="username" /></el-form-item><el-form-item label="密码"><el-input v-model="password" type="password" show-password /></el-form-item><el-button type="primary" size="large" :loading="loading" class="full" @click="login">进入教学工作台</el-button></el-form>
      <div class="notice">⚠ 不得输入真实姓名、电话、身份证号或真实病历资料</div>
    </div>
  </section>
</template>

