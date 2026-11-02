<template>
  <section class="set-pane">
    <template v-if="pane === 'notify'">
      <SettingsGroup>
        <SettingRow label="提醒通知">
          <button type="button" class="set-link-btn" @click="openSystemNotify">前往系统偏好设置</button>
        </SettingRow>
        <SettingRow label="完成提示音">
          <SettingSelect :model-value="settings.completeSound" :options="completeOptions" label="完成提示音" @update:model-value="set('completeSound', $event)" />
        </SettingRow>
      </SettingsGroup>
    </template>

    <template v-else>
      <SettingsGroup>
        <SettingRow label="每周开始于">
          <SettingSelect :model-value="settings.weekStartDay" :options="weekOptions" label="每周开始于" @update:model-value="set('weekStartDay', $event)" />
        </SettingRow>
      </SettingsGroup>
      <SettingsGroup>
        <SettingRow label="显示农历">
          <SettingSwitch :model-value="settings.showLunar" label="显示农历" @update:model-value="set('showLunar', $event)" />
        </SettingRow>
        <SettingRow label="显示周数">
          <SettingSwitch :model-value="settings.showWeekNumber" label="显示周数" @update:model-value="set('showWeekNumber', $event)" />
        </SettingRow>
        <SettingRow label="显示节假日调休">
          <SettingSwitch :model-value="settings.showHoliday" label="显示节假日调休" @update:model-value="set('showHoliday', $event)" />
        </SettingRow>
      </SettingsGroup>
    </template>
  </section>
</template>

<script setup>
import { message } from '../../services/message.js'
import { useSettings } from './useSettings.js'
import SettingRow from './SettingRow.vue'
import SettingSelect from './SettingSelect.vue'
import SettingSwitch from './SettingSwitch.vue'
import SettingsGroup from './SettingsGroup.vue'

defineProps({ pane: { type: String, required: true } })

const { settings, set } = useSettings()

const completeOptions = [
  { value: 'WATER', label: '水滴' },
  { value: 'CLICK', label: '清脆' },
  { value: 'NONE', label: '静音' }
]
const weekOptions = [
  { value: 'MONDAY', label: '周一' },
  { value: 'SUNDAY', label: '周日' }
]

async function openSystemNotify() {
  if (typeof Notification === 'undefined') {
    message.info('当前浏览器不支持通知，请在系统偏好设置中允许本站通知')
    return
  }
  if (Notification.permission === 'granted') {
    message.success('浏览器通知已开启，可在系统偏好设置中调整')
    return
  }
  if (Notification.permission === 'denied') {
    message.info('通知已被拒绝，请在浏览器地址栏的站点设置中重新允许')
    return
  }
  const result = await Notification.requestPermission()
  if (result === 'granted') message.success('浏览器通知已开启')
  else message.info('未获得通知权限，可在浏览器站点设置中重新允许')
}
</script>

<style scoped>
.set-pane{display:grid;gap:14px}
.set-link-btn{padding:0;border:0;background:transparent;color:#3b82f6;font-size:.78rem;cursor:pointer}
.set-link-btn:hover{text-decoration:underline}
</style>
