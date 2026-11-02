<template>
  <section class="set-pane">
    <div class="set-block">
      <h4 class="set-block-title">日历</h4>
      <div class="set-tile-grid">
        <button v-for="item in calendarSources" :key="item.key" type="button" class="set-tile" @click="openTile(item.label)">
          <span class="set-tile-icon" :style="{ background: item.tint, color: item.fg }"><component :is="item.icon" :size="15" /></span>
          <span class="set-tile-label">{{ item.label }}</span>
          <HelpCircle v-if="item.help" :size="13" class="set-tile-help" :aria-label="`${item.label} 说明`" title="填写 CalDAV 或 Webcal 订阅地址" />
        </button>
      </div>
    </div>

    <div class="set-block">
      <h4 class="set-block-title">关联</h4>
      <div class="set-tile-grid">
        <button v-for="item in linkSources" :key="item.key" type="button" class="set-tile" @click="openTile(item.label)">
          <span class="set-tile-icon" :style="{ background: item.tint, color: item.fg }"><component :is="item.icon" :size="15" /></span>
          <span class="set-tile-label">{{ item.label }}</span>
        </button>
      </div>
    </div>

    <div class="set-block">
      <h4 class="set-block-title">其他</h4>
      <div class="set-tile-grid">
        <button v-for="item in otherSources" :key="item.key" type="button" class="set-tile is-wide" @click="openTile(item.label)">
          <span class="set-tile-icon" :style="{ background: item.tint, color: item.fg }"><component :is="item.icon" :size="15" /></span>
          <span class="set-tile-text">
            <b>{{ item.label }}</b>
            <small>{{ item.desc }}</small>
          </span>
        </button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { Calendar, CalendarClock, CalendarDays, Cloud, HelpCircle, Link2, Mail, MessageCircle, MessageSquare, NotebookPen, Rss, Send } from 'lucide-vue-next'
import { message } from '../../services/message.js'

defineProps({ pane: { type: String, required: true } })

const calendarSources = [
  { key: 'local', label: '本地日历', icon: Calendar, tint: '#e8f0fe', fg: '#2f6fdb' },
  { key: 'google', label: 'Google 日历', icon: CalendarDays, tint: '#e8f0fe', fg: '#4285f4' },
  { key: 'outlook', label: 'Outlook 日历', icon: Mail, tint: '#e3effa', fg: '#0f6cbd' },
  { key: 'exchange', label: 'Exchange 日历', icon: Mail, tint: '#e3f0fb', fg: '#0078d4' },
  { key: 'icloud', label: 'iCloud 日历', icon: Cloud, tint: '#e6f2ff', fg: '#3693f3' },
  { key: 'wecom', label: '企业微信日历', icon: MessageSquare, tint: '#e6f0fd', fg: '#2a82e4' },
  { key: 'dingtalk', label: '钉钉日历', icon: MessageCircle, tint: '#e4f3ff', fg: '#1e9fff' },
  { key: 'feishu', label: '飞书日历', icon: Send, tint: '#e6efff', fg: '#3370ff' },
  { key: 'caldav', label: 'CalDAV', icon: CalendarClock, tint: '#fff1e2', fg: '#f5a623' },
  { key: 'url', label: 'URL', icon: Link2, tint: '#e8f0fe', fg: '#4285f4', help: true }
]
const linkSources = [
  { key: 'google-link', label: 'Google 日历', icon: CalendarDays, tint: '#e8f0fe', fg: '#4285f4' },
  { key: 'notion', label: 'Notion', icon: NotebookPen, tint: '#eceef2', fg: '#1f2430' }
]
const otherSources = [
  { key: 'subscribe', label: '订阅滴答清单', desc: '将滴答清单订阅到 Google、Outlook 等应用显示。', icon: Rss, tint: '#e8f0fe', fg: '#2f6fdb' },
  { key: 'email', label: '邮件创建任务', desc: '发送邮件即可在滴答清单中创建任务。', icon: Mail, tint: '#e8f0fe', fg: '#2f6fdb' }
]

function openTile(name) {
  message.info(`「${name}」：入口已就位，账号授权待接入`)
}
</script>

<style scoped>
.set-pane{display:grid;gap:18px}
.set-block{display:grid;gap:9px}
.set-block-title{margin:0;color:var(--ink);font-size:.82rem;font-weight:600}
.set-tile-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.set-tile{display:flex;align-items:center;gap:10px;min-height:48px;padding:0 12px;border:1px solid transparent;border-radius:9px;background:var(--set-group-bg,color-mix(in srgb,var(--paper) 62%,var(--card)));color:var(--ink);font-size:.79rem;text-align:left;cursor:pointer}
.set-tile:hover{border-color:var(--line2)}
.set-tile-icon{display:grid;flex:none;width:26px;height:26px;place-items:center;border-radius:6px;color:#1f2937}
.set-tile-label{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.set-tile-help{flex:none;margin-left:auto;color:var(--muted)}
.set-tile.is-wide{min-height:72px;align-items:flex-start;padding:12px}
.set-tile.is-wide .set-tile-icon{margin-top:1px}
.set-tile-text{display:grid;gap:3px;min-width:0}
.set-tile-text b{font-size:.79rem;font-weight:500;color:var(--ink)}
.set-tile-text small{color:var(--muted);font-size:.7rem;line-height:1.5}
@media(max-width:820px){.set-tile-grid{grid-template-columns:1fr}}
</style>
