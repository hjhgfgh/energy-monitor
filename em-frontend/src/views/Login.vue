<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2 class="title">智慧能源监控系统</h2>
      <el-form :model="form" @keyup.enter="submit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名（admin）" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码（admin123）" show-password />
        </el-form-item>
        <el-button type="primary" class="btn" :loading="loading" @click="submit">登 录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api'

const router = useRouter()
const form = reactive({ username: 'admin', password: 'admin123' })
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    const data = await login(form.username, form.password)
    localStorage.setItem('em_token', data.token)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100%;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #0f2027, #203a43, #2c5364);
}
.login-card { width: 380px; padding: 12px 8px; }
.title { text-align: center; margin-bottom: 24px; color: #2c5364; }
.btn { width: 100%; }
</style>
