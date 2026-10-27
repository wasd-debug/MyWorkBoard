import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './styles/tailwind.css'
import App from './App.vue'
import router from './router'
import './styles/theme.css'

const app = createApp(App)

// 渲染崩溃兜底：Vue 渲染函数抛错时整个组件树会静默变白屏（尤其是 HMR 中间态），
// 显示可重载的错误提示，避免"看起来像没数据"的假死。
let crashBannerShown = false
app.config.errorHandler = (err, _instance, info) => {
  console.error('[app] render error:', err, info)
  if (crashBannerShown) return
  crashBannerShown = true
  const banner = document.createElement('div')
  banner.style.cssText = 'position:fixed;z-index:99999;left:50%;bottom:28px;transform:translateX(-50%);max-width:min(520px,90vw);padding:12px 16px;border:1px solid rgba(220,38,38,.35);border-radius:10px;background:#fff;color:#1f2937;font:13px/1.6 system-ui,sans-serif;box-shadow:0 12px 32px rgba(15,23,42,.18)'
  banner.innerHTML = `<strong style="display:block;margin-bottom:4px;color:#dc2626">页面渲染出错了</strong>界面可能不完整。<a href="#" id="__crash_reload" style="color:#2563eb;text-decoration:underline">刷新页面</a> 通常可以恢复。<pre style="display:none;margin:6px 0 0;white-space:pre-wrap;font-size:11px;color:#6b7280" id="__crash_detail"></pre>`
  document.body.appendChild(banner)
  banner.querySelector('#__crash_detail').textContent = String(err?.stack || err)
  banner.querySelector('#__crash_reload').addEventListener('click', event => {
    event.preventDefault()
    location.reload()
  })
}

app.use(createPinia())
app.use(router)
app.mount('#app')
