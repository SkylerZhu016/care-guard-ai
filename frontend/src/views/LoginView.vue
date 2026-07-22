<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Avatar, Calendar, DataAnalysis, UserFilled, WarningFilled } from '@element-plus/icons-vue'
import { ApiClient } from '../api'
import { useSessionStore } from '../stores/session'

const session=useSessionStore(),router=useRouter(),username=ref('patient'),password=ref('Demo123!'),loading=ref(false)
const roles=[
  {id:'patient',name:'患者',description:'预问诊、记录、随访与健康资料',icon:UserFilled},
  {id:'clinician',name:'医务人员',description:'规则证据与人工终审',icon:Avatar},
  {id:'followup',name:'随访人员',description:'执行已明确激活的任务',icon:Calendar},
  {id:'admin',name:'管理员',description:'安全、知识与审计治理',icon:DataAnalysis},
]
async function login(){loading.value=true;try{const data=await new ApiClient(()=>null).login(username.value,password.value);session.establish(data.accessToken,data.user);await router.push(session.home())}catch(e:any){ElMessage.error(e.message)}finally{loading.value=false}}
function choose(value:string){username.value=value;password.value='Demo123!'}
</script>

<template>
  <section class="login-page">
    <div class="login-layout">
      <section class="login-intro">
        <span class="eyebrow">SAFETY-FIRST CARE WORKFLOW</span>
        <h1>有限范围，<br><em>清楚表达。</em></h1>
        <p>确定性规则承担已覆盖范围内的安全提醒，AI 只整理信息与检索证据。系统始终明确能力边界，最终决定由医务人员完成。</p>
        <div class="safety-points"><span>规则优先</span><span>证据可核对</span><span>人工终审</span><span>全程审计</span></div>
        <div class="scope-card"><WarningFilled /><div><strong>系统整理信息，不代替就医</strong><p>常见症状均可记录；自动规则只覆盖部分明确组合，其他情况会进入人工复核。</p></div></div>
      </section>
      <section class="login-card" aria-labelledby="login-title">
        <div class="card-kicker">测试入口</div><h2 id="login-title">选择角色</h2><p class="muted">测试账号密码统一为 <code>Demo123!</code></p>
        <div class="role-grid">
          <button v-for="role in roles" :key="role.id" type="button" :aria-pressed="username===role.id" :class="{active:username===role.id}" @click="choose(role.id)">
            <component :is="role.icon"/><span><strong>{{role.name}}</strong><small>{{role.description}}</small></span>
          </button>
        </div>
        <el-form label-position="top" @submit.prevent="login">
          <div class="login-fields"><el-form-item label="测试账号"><el-input v-model="username" autocomplete="username" /></el-form-item><el-form-item label="密码"><el-input v-model="password" type="password" show-password autocomplete="current-password" /></el-form-item></div>
          <el-button native-type="submit" type="primary" size="large" :loading="loading" class="full">进入 {{roles.find(r=>r.id===username)?.name}}工作台</el-button>
        </el-form>
        <div class="privacy-notice"><WarningFilled />不得输入真实姓名、电话、身份证号或真实病历资料</div>
      </section>
    </div>
  </section>
</template>
