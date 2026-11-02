<template>
  <section class="set-pane">
    <div v-if="pane === 'account'" class="set-block">
      <div class="set-profile">
        <span class="set-avatar">{{ initial }}</span>
        <div>
          <b>{{ nickname }}</b>
          <small>{{ email }}</small>
        </div>
        <button type="button" class="set-ghost-btn" @click="logout">退出登录</button>
      </div>
      <SettingRow label="昵称"><span class="set-value">{{ nickname }}</span></SettingRow>
      <SettingRow label="登录账号"><span class="set-value">{{ email }}</span></SettingRow>
      <SettingRow label="修改密码" desc="修改后其他设备需要重新登录">
        <button type="button" class="set-ghost-btn" @click="stub('修改密码')">去修改</button>
      </SettingRow>
      <SettingRow label="数据归属" desc="任务、习惯、账本数据仅存于你自己的服务端实例">
        <span class="set-value">本地部署</span>
      </SettingRow>
      <SettingRow label="注销账号" desc="永久删除账号及全部数据，不可恢复">
        <button type="button" class="set-ghost-btn is-danger" @click="stub('注销账号')">注销</button>
      </SettingRow>
    </div>

    <div v-else-if="pane === 'premium'" class="set-block">
      <div class="set-premium">
        <div>
          <b>当前方案：免费版</b>
          <small>这是自部署实例，会员能力未接入；所有功能默认可用。</small>
        </div>
        <button type="button" class="set-primary-btn" @click="stub('升级会员')">了解会员</button>
      </div>
      <SettingRow v-for="item in premiumRights" :key="item.label" :label="item.label" :desc="item.desc">
        <span class="set-tag">{{ item.tag }}</span>
      </SettingRow>
    </div>

    <div v-else class="set-block">
      <SettingRow label="版本" desc="自部署版本，随代码库更新">
        <span class="set-value">v{{ version }}</span>
      </SettingRow>
      <SettingRow label="检查更新" desc="拉取最新镜像并重启容器">
        <button type="button" class="set-ghost-btn" @click="stub('检查更新')">检查</button>
      </SettingRow>
      <SettingRow label="服务地址"><code class="set-code">{{ origin }}</code></SettingRow>
      <SettingRow v-for="item in aboutLinks" :key="item.label" :label="item.label">
        <button type="button" class="set-ghost-btn" @click="stub(item.label)">打开</button>
      </SettingRow>
      <SettingRow label="开源许可" desc="Vue 3 / Spring Boot / MySQL 等组件的许可证">
        <button type="button" class="set-ghost-btn" @click="stub('开源许可')">查看</button>
      </SettingRow>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { message } from '../../services/message.js'
import { useAppStore } from '../../stores/app.js'
import SettingRow from './SettingRow.vue'

defineProps({ pane: { type: String, required: true } })

const app = useAppStore()
const version = '1.0.0'
const origin = typeof window === 'undefined' ? '' : window.location.origin
const nickname = computed(() => app.authUser?.nickname || app.authUser?.username || '未登录')
const email = computed(() => app.authUser?.username || '—')
const initial = computed(() => String(nickname.value).slice(0, 1))
const premiumRights = [
  { label: '清单与任务数量', desc: '免费版不限清单数量', tag: '已开放' },
  { label: '主题与外观', desc: '颜色主题、季节主题与城市主题', tag: '已开放' },
  { label: 'AI 能力', desc: '依赖你配置的模型 Key 与预算', tag: '已开放' },
  { label: '数据导出', desc: '任务、账本可导出为 CSV', tag: '已开放' }
]
const aboutLinks = [
  { label: '官网' },
  { label: '隐私政策' },
  { label: '用户协议' }
]
function stub(name) {
  message.info(`${name}：入口已就位，交互待接入`)
}
async function logout() {
  if (!window.confirm('确定退出当前账号？')) return
  await app.logout()
}
</script>

<style scoped>
.set-pane{display:grid;gap:14px}
.set-block{display:grid}
.set-profile{display:flex;align-items:center;gap:12px;padding:4px 0 14px}
.set-avatar{display:grid;width:44px;height:44px;place-items:center;border-radius:12px;background:var(--ink);color:var(--paper);font-size:1.1rem;font-weight:700}
.set-profile b{display:block;font-size:.9rem}
.set-profile small{color:var(--muted);font-size:.72rem}
.set-profile .set-ghost-btn{margin-left:auto}
.set-premium{display:flex;align-items:center;gap:14px;padding:14px;margin-bottom:10px;border:1px solid var(--line);border-radius:10px;background:linear-gradient(120deg,color-mix(in srgb,var(--accent) 10%,transparent),transparent)}
.set-premium b{display:block;font-size:.86rem}
.set-premium small{color:var(--muted);font-size:.72rem}
.set-primary-btn{margin-left:auto;height:30px;padding:0 14px;border:0;border-radius:7px;background:#3b82f6;color:#fff;font-size:.76rem;cursor:pointer}
.set-ghost-btn{height:26px;padding:0 10px;border:1px solid var(--line2);border-radius:6px;background:var(--card);color:var(--ink);font-size:.73rem;cursor:pointer}
.set-ghost-btn:hover{border-color:var(--accent);color:var(--accent)}
.set-ghost-btn.is-danger{color:var(--down);border-color:color-mix(in srgb,var(--down) 40%,transparent)}
.set-value{color:var(--muted);font-size:.76rem}
.set-code{padding:2px 7px;border-radius:5px;background:var(--panel2);font-size:.72rem}
.set-tag{padding:1px 7px;border-radius:8px;background:var(--accent-soft);color:var(--accent);font-size:.68rem}
</style>
