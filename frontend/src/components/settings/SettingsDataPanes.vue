<template>
  <section class="set-pane">
    <h4 class="set-group-title">导入</h4>
    <SettingRow label="从滴答清单导入" desc="支持任务、清单、标签、习惯与倒数纪念日">
      <button type="button" class="set-ghost-btn" @click="stub('从滴答清单导入')">开始导入</button>
    </SettingRow>
    <SettingRow label="从 Excel / CSV 导入" desc="按列映射后批量写入指定清单">
      <button type="button" class="set-ghost-btn" @click="stub('从 Excel / CSV 导入')">选择文件</button>
    </SettingRow>
    <SettingRow label="从其他应用导入" desc="Todoist、Microsoft To Do 等格式">
      <button type="button" class="set-ghost-btn" @click="stub('从其他应用导入')">了解支持格式</button>
    </SettingRow>

    <h4 class="set-group-title">导出</h4>
    <SettingRow label="导出任务" desc="包含任务、清单、标签与完成状态">
      <button type="button" class="set-ghost-btn" @click="stub('导出任务')">导出</button>
    </SettingRow>
    <SettingRow label="导出账本流水" desc="按时间范围导出为 CSV">
      <button type="button" class="set-ghost-btn" @click="stub('导出账本流水')">导出</button>
    </SettingRow>

    <h4 class="set-group-title">关联日历账号</h4>
    <article v-for="item in calendarAccounts" :key="item.key" class="set-account">
      <span class="set-account-icon">{{ item.icon }}</span>
      <div><b>{{ item.label }}</b><small>{{ item.desc }}</small></div>
      <button type="button" class="set-ghost-btn" @click="stub(`关联${item.label}`)">关联</button>
    </article>
    <p class="set-hint">关联后可把外部日历日程叠加在任务日历上，只读同步，不会写回。</p>
  </section>
</template>

<script setup>
import { message } from '../../services/message.js'
import SettingRow from './SettingRow.vue'

defineProps({ pane: { type: String, required: true } })

const calendarAccounts = [
  { key: 'GOOGLE', label: 'Google 日历', desc: '只读叠加日程', icon: '📅' },
  { key: 'OUTLOOK', label: 'Outlook 日历', desc: '只读叠加日程', icon: '📆' },
  { key: 'CALDAV', label: 'CalDAV', desc: '自建日历服务', icon: '🗓️' }
]
function stub(name) {
  message.info(`${name}：入口已就位，交互待接入`)
}
</script>

<style scoped>
.set-pane{display:grid;gap:2px}
.set-group-title{margin:14px 0 4px;color:var(--ink2);font-size:.72rem;font-weight:600;letter-spacing:.06em}
.set-pane>.set-group-title:first-child{margin-top:0}
.set-account{display:flex;align-items:center;gap:11px;padding:9px 0;border-bottom:1px solid color-mix(in srgb,var(--line) 60%,transparent)}
.set-account:last-of-type{border-bottom:0}
.set-account-icon{display:grid;width:30px;height:30px;place-items:center;border-radius:8px;background:var(--panel2);font-size:14px}
.set-account b{display:block;font-size:.78rem}
.set-account small{color:var(--muted);font-size:.68rem}
.set-account .set-ghost-btn{margin-left:auto}
.set-hint{margin:6px 0 0;color:var(--muted);font-size:.7rem}
.set-ghost-btn{height:26px;padding:0 11px;border:1px solid var(--line2);border-radius:6px;background:var(--card);color:var(--ink);font-size:.72rem;cursor:pointer}
.set-ghost-btn:hover{border-color:var(--accent);color:var(--accent)}
</style>
