import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElButton, ElForm, ElFormItem, ElInput, ElOption, ElSelect, ElSlider } from 'element-plus'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-button.css'
import 'element-plus/theme-chalk/el-form.css'
import 'element-plus/theme-chalk/el-form-item.css'
import 'element-plus/theme-chalk/el-input.css'
import 'element-plus/theme-chalk/el-message.css'
import 'element-plus/theme-chalk/el-option.css'
import 'element-plus/theme-chalk/el-select.css'
import 'element-plus/theme-chalk/el-slider.css'
import './style.css'
import App from './App.vue'
import { router } from './router'

const app = createApp(App).use(createPinia()).use(router)

for (const component of [ElButton, ElForm, ElFormItem, ElInput, ElOption, ElSelect, ElSlider]) {
  app.component(component.name!, component)
}

app.mount('#app')
