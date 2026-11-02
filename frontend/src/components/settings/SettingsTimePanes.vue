<template>
  <section class="set-pane">
    <template v-if="pane === 'notify'">
      <SettingRow label="提醒方式">
        <SettingSelect :model-value="settings.remindMethod" :options="methodOptions" @update:model-value="set('remindMethod', $event)" />
      </SettingRow>
      <SettingRow label="显示通知详情（解锁时）" desc="关闭后在锁屏时只提示“收到一条提醒”，不显示任务标题。">
        <SettingSwitch :model-value="settings.notifyDetail === 'UNLOCK'" label="显示通知详情" @update:model-value="set('notifyDetail', $event ? 'UNLOCK' : 'HIDE')" />
      </SettingRow>
      <SettingRow label="全局持续提醒" desc="在列表或日历中，持续高亮显示需要提醒的任务或日程。">
        <SettingSwitch :model-value="settings.persistentRemind" label="全局持续提醒" @update:model-value="set('persistentRemind', $event)" />
      </SettingRow>
      <SettingRow label="提醒铃声">
        <SettingSelect :model-value="settings.remindSound" :options="soundOptions" @update:model-value="set('remindSound', $event)" />
      </SettingRow>
      <SettingRow label="完成提示音">
        <SettingSelect :model-value="settings.completeSound" :options="completeOptions" @update:model-value="set('completeSound', $event)" />
      </SettingRow>
      <SettingRow label="提醒音量" desc="仅影响本应用的提醒音，不影响系统音量。">
        <div class="set-volume">
          <input v-model.number="settings.remindVolume" type="range" min="0" max="100" step="1" aria-label="提醒音量">
          <b>{{ settings.remindVolume }}</b>
        </div>
      </SettingRow>
      <div class="set-actions">
        <button type="button" class="set-ghost-btn" @click="testSound">试听提醒音</button>
      </div>
    </template>

    <template v-else>
      <SettingRow label="时间格式">
        <SettingSelect :model-value="settings.timeFormat" :options="formatOptions" @update:model-value="set('timeFormat', $event)" />
      </SettingRow>
      <SettingRow label="每周开始于">
        <SettingChoices :model-value="settings.weekStartDay" :options="weekOptions" label="每周开始于" @update:model-value="set('weekStartDay', $event)" />
      </SettingRow>
      <SettingRow label="显示农历" desc="在日历与日期浮层中同时显示农历。">
        <SettingSwitch :model-value="settings.showLunar" label="显示农历" @update:model-value="set('showLunar', $event)" />
      </SettingRow>
      <SettingRow label="显示周数" desc="在日历左侧显示第几周。">
        <SettingSwitch :model-value="settings.showWeekNumber" label="显示周数" @update:model-value="set('showWeekNumber', $event)" />
      </SettingRow>
      <SettingRow label="显示节假日调休" desc="显示法定节假日与调休上班标记。">
        <SettingSwitch :model-value="settings.showHoliday" label="显示节假日调休" @update:model-value="set('showHoliday', $event)" />
      </SettingRow>
      <SettingRow label="根据设备时区设置时间" desc="关闭后使用固定时区，跨时区出行时日期保持不变。">
        <SettingSwitch :model-value="settings.timezonePick" label="根据设备时区设置时间" @update:model-value="set('timezonePick', $event)" />
      </SettingRow>
      <SettingRow label="当前时区">
        <span class="set-value">{{ timezone }}</span>
      </SettingRow>
    </template>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { message } from '../../services/message.js'
import { useSettings } from './useSettings.js'
import SettingChoices from './SettingChoices.vue'
import SettingRow from './SettingRow.vue'
import SettingSelect from './SettingSelect.vue'
import SettingSwitch from './SettingSwitch.vue'

defineProps({ pane: { type: String, required: true } })

const { settings, set } = useSettings()

const methodOptions = [
  { value: 'IN_APP', label: '应用内提醒' },
  { value: 'SYSTEM', label: '系统通知' },
  { value: 'BOTH', label: '应用内 + 系统通知' }
]
const soundOptions = [
  { value: 'DEFAULT', label: '默认' },
  { value: 'BELL', label: '铃铛' },
  { value: 'SOFT', label: '柔和' },
  { value: 'NONE', label: '静音' }
]
const completeOptions = [
  { value: 'WATER', label: '水滴' },
  { value: 'CLICK', label: '清脆' },
  { value: 'NONE', label: '静音' }
]
const formatOptions = [
  { value: 'SYSTEM', label: '跟随系统' },
  { value: 'H24', label: '24 小时制' },
  { value: 'H12', label: '12 小时制' }
]
const weekOptions = [
  { value: 'MONDAY', label: '周一' },
  { value: 'SUNDAY', label: '周日' }
]
const timezone = computed(() => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || '—'
  } catch {
    return '—'
  }
})
function testSound() {
  message.info('试听提醒音：入口已就位，音频待接入')
}
</script>

<style scoped>
.set-pane{display:grid;gap:2px}
.set-volume{display:inline-flex;align-items:center;gap:9px}
.set-volume input{width:132px;accent-color:#3b82f6}
.set-volume b{min-width:26px;color:var(--muted);font-size:.72rem;font-weight:500;text-align:right}
.set-value{color:var(--muted);font-size:.76rem}
.set-actions{display:flex;padding-top:10px}
.set-ghost-btn{height:27px;padding:0 12px;border:1px solid var(--line2);border-radius:6px;background:var(--card);color:var(--ink);font-size:.73rem;cursor:pointer}
.set-ghost-btn:hover{border-color:var(--accent);color:var(--accent)}
</style>
