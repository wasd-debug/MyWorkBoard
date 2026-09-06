<template>
  <main class="auth-shell">
    <button class="auth-theme-toggle" type="button" :aria-label="store.theme === 'dark' ? '切换日间模式' : '切换暗夜模式'" @click="store.toggleTheme()">
      {{ store.theme === 'dark' ? '☀' : '☾' }}
    </button>
    <section class="auth-card">
      <div class="auth-brand">
        <div class="title">个人工作台</div>
        <div class="label">PERSONAL WORKSPACE</div>
      </div>
      <div class="auth-copy">
        <span class="label">YOUR PRIVATE SPACE</span>
        <h1>{{ registering ? '创建你的工作台' : '欢迎回来' }}</h1>
        <p>{{ registering ? '建立个人账户，开始整理你的生活与工作。' : '登录后继续使用你的个人工作台。' }}</p>
        <div class="auth-features" aria-label="工作台功能">
          <span>账本</span><span>任务</span><span>知识库</span><span>工时</span>
        </div>
      </div>
      <form class="auth-form" @submit.prevent="submit">
        <label>用户名<input v-model.trim="form.username" required minlength="3" autocomplete="username" placeholder="例如 alex" /></label>
        <label v-if="registering">昵称<input v-model.trim="form.nickname" autocomplete="nickname" placeholder="显示名称（可选）" /></label>
        <label v-if="registering">邮箱<input v-model.trim="form.email" type="email" autocomplete="email" placeholder="用于找回账户（可选）" /></label>
        <label>密码<input v-model="form.password" type="password" required minlength="8" autocomplete="current-password" placeholder="至少 8 位" /></label>
        <p v-if="error" class="auth-error" role="alert">{{ error }}</p>
        <Button class="auth-submit" variant="ink" type="submit" :disabled="loading">{{ loading ? '处理中…' : registering ? '注册并进入工作台' : '进入工作台' }}</Button>
      </form>
      <button class="auth-switch" type="button" @click="registering = !registering; error = ''">{{ registering ? '已有账户？返回登录' : '首次使用？创建账户' }}</button>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { apiLogin, apiRegister } from '../api/auth'
import { useAppStore } from '../stores/app'
import Button from '../components/ui/Button.vue'

const store = useAppStore()
const registering = ref(false)
const loading = ref(false)
const error = ref('')
const form = reactive({ username: '', password: '', nickname: '', email: '' })

async function submit() {
  loading.value = true
  error.value = ''
  try {
    const data = registering.value ? await apiRegister(form) : await apiLogin({ username: form.username, password: form.password })
    await store.completeLogin(data.user)
  } catch (exception) {
    error.value = exception.response?.data?.detail || exception.response?.data?.message || '登录失败，请检查输入后重试'
  } finally {
    loading.value = false
  }
}
</script>
