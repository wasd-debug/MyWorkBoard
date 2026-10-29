<template>
  <section class="set-pane">
    <template v-if="pane === 'smartlist'">
      <div v-for="group in SMART_LISTS" :key="group.group" class="set-group">
        <h4 class="set-group-title">{{ group.group }}</h4>
        <SettingRow v-for="item in group.items" :key="item.key" :label="`${item.icon}  ${item.label}`">
          <SettingSelect :model-value="settings[item.key]" :options="SHOW_OPTIONS" :label="item.label" @update:model-value="set(item.key, $event)" />
        </SettingRow>
      </div>
    </template>

    <template v-else-if="pane === 'advanced'">
      <RouterLink class="set-banner" to="/tasks/settings">
        <span>任务默认值、日历叠加、通知与 ICS 订阅</span>
        <b>前往任务设置 →</b>
      </RouterLink>
      <SettingRow label="默认日期">
        <SettingSelect :model-value="settings.defaultDate" :options="dateOptions" @update:model-value="set('defaultDate', $event)" />
      </SettingRow>
      <SettingRow label="默认日期模式">
        <SettingSelect :model-value="settings.defaultDateMode" :options="dateModeOptions" @update:model-value="set('defaultDateMode', $event)" />
      </SettingRow>
      <SettingRow label="默认时间段">
        <SettingSelect :model-value="settings.defaultDuration" :options="durationOptions" @update:model-value="set('defaultDuration', $event)" />
      </SettingRow>
      <SettingRow label="默认提醒（有时间的任务）">
        <SettingSelect :model-value="settings.defaultRemindTimed" :options="remindTimedOptions" @update:model-value="set('defaultRemindTimed', $event)" />
      </SettingRow>
      <SettingRow label="默认提醒（全天任务）">
        <SettingSelect :model-value="settings.defaultRemindAllDay" :options="remindAllDayOptions" @update:model-value="set('defaultRemindAllDay', $event)" />
      </SettingRow>
      <SettingRow label="默认优先级">
        <SettingSelect :model-value="settings.defaultPriority" :options="priorityOptions" @update:model-value="set('defaultPriority', $event)" />
      </SettingRow>
      <SettingRow label="默认标签">
        <SettingSelect :model-value="settings.defaultTag" :options="tagOptions" @update:model-value="set('defaultTag', $event)" />
      </SettingRow>
      <SettingRow label="默认清单">
        <SettingSelect :model-value="settings.defaultList" :options="listOptions" @update:model-value="set('defaultList', $event)" />
      </SettingRow>
      <SettingRow label="默认添加到">
        <SettingSelect :model-value="settings.defaultAppend" :options="appendOptions" @update:model-value="set('defaultAppend', $event)" />
      </SettingRow>
      <SettingRow label="已过期任务的分组位置">
        <SettingSelect :model-value="settings.overduePosition" :options="positionOptions" @update:model-value="set('overduePosition', $event)" />
      </SettingRow>
      <SettingRow label="Mini 日历" desc="在任务页右侧显示可点击的小月历">
        <SettingSwitch :model-value="settings.miniCalendar" label="Mini 日历" @update:model-value="set('miniCalendar', $event)" />
      </SettingRow>
      <div class="set-actions">
        <button type="button" class="set-ghost-btn" @click="resetDefaults">重置任务默认值</button>
      </div>
    </template>

    <template v-else>
      <SettingRow label="自动接受共享邀请" desc="有其他人邀请你加入共享清单时，自动接受邀请。">
        <SettingSwitch :model-value="settings.autoAcceptShare" label="自动接受共享邀请" @update:model-value="set('autoAcceptShare', $event)" />
      </SettingRow>
      <h4 class="set-group-title">共享清单的默认通知</h4>
      <div class="set-check-list">
        <label v-for="item in shareNotifications" :key="item.key">
          <input type="checkbox" :checked="settings[item.key]" @change="set(item.key, $event.target.checked)">
          <span>{{ item.label }}</span>
          <small>{{ item.desc }}</small>
        </label>
      </div>
      <p class="set-hint">按任务内的清单设置可以更改任意清单的通知类型。</p>
    </template>
  </section>
</template>

<script setup>
import { message } from '../../services/message.js'
import { RouterLink } from 'vue-router'
import { SHOW_OPTIONS, SMART_LISTS } from './settingsState.js'
import { useSettings } from './useSettings.js'
import SettingRow from './SettingRow.vue'
import SettingSelect from './SettingSelect.vue'
import SettingSwitch from './SettingSwitch.vue'

defineProps({ pane: { type: String, required: true } })

const { settings, set, reset } = useSettings()

const dateOptions = [
  { value: 'TODAY', label: '今天' },
  { value: 'NONE', label: '不设置' },
  { value: 'TOMORROW', label: '明天' }
]
const dateModeOptions = [
  { value: 'DATE', label: '日期' },
  { value: 'DATETIME', label: '日期和时间' }
]
const durationOptions = [
  { value: 'PT30M', label: '30 分钟' },
  { value: 'PT1H', label: '1 小时' },
  { value: 'PT2H', label: '2 小时' }
]
const remindTimedOptions = [
  { value: 'M5', label: '提前 5 分钟' },
  { value: 'M5,M30', label: '提前 5 分钟、30 分钟' },
  { value: 'NONE', label: '不提醒' }
]
const remindAllDayOptions = [
  { value: 'NONE', label: '不提醒' },
  { value: '09:00', label: '当天 09:00' },
  { value: '20:00', label: '当天 20:00' }
]
const priorityOptions = [
  { value: 'NONE', label: '无优先级' },
  { value: 'LOW', label: '低优先级' },
  { value: 'MEDIUM', label: '中优先级' },
  { value: 'HIGH', label: '高优先级' }
]
const tagOptions = [
  { value: 'NONE', label: '不设置' },
  { value: 'LAST_USED', label: '上次使用的标签' }
]
const listOptions = [
  { value: 'INBOX', label: '收集箱' },
  { value: 'CURRENT', label: '当前清单' },
  { value: 'LAST_USED', label: '上次使用的清单' }
]
const appendOptions = [
  { value: 'BOTTOM', label: '清单底部' },
  { value: 'TOP', label: '清单顶部' }
]
const positionOptions = [
  { value: 'TOP', label: '置顶' },
  { value: 'BOTTOM', label: '置底' },
  { value: 'HIDDEN', label: '单独分组' }
]
const shareNotifications = [
  { key: 'shareNotifyComplete', label: '完成/取消完成', desc: '共享任务被完成或取消完成时通知我' },
  { key: 'shareNotifyAdd', label: '添加了任务/笔记', desc: '共享清单新增任务或笔记时通知我' },
  { key: 'shareNotifyDelete', label: '删除/移走任务', desc: '共享清单里的任务被删除或移走时通知我' }
]
function resetDefaults() {
  reset(['defaultDate', 'defaultDateMode', 'defaultDuration', 'defaultRemindTimed', 'defaultRemindAllDay', 'defaultPriority', 'defaultTag', 'defaultList', 'defaultAppend', 'overduePosition', 'miniCalendar'])
  message.success('任务默认值已重置')
}
</script>

<style scoped>
.set-pane{display:grid;gap:6px}
.set-group{display:grid;gap:0;padding-bottom:6px}
.set-group-title{margin:12px 0 2px;color:var(--ink2);font-size:.72rem;font-weight:600;letter-spacing:.06em}
.set-group:first-child .set-group-title{margin-top:0}
.set-banner{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:10px;padding:10px 12px;border:1px solid var(--line);border-radius:9px;background:var(--panel2);color:var(--ink);font-size:.75rem;text-decoration:none}
.set-banner b{color:var(--accent);font-size:.73rem;white-space:nowrap}
.set-banner:hover{border-color:var(--accent)}
.set-check-list{display:grid;gap:2px}
.set-check-list label{display:grid;grid-template-columns:16px 1fr;align-items:center;gap:8px;padding:8px 0;border-bottom:1px solid color-mix(in srgb,var(--line) 60%,transparent);font-size:.78rem;cursor:pointer}
.set-check-list label:last-child{border-bottom:0}
.set-check-list small{grid-column:2;color:var(--muted);font-size:.68rem}
.set-actions{display:flex;justify-content:flex-start;padding-top:10px}
.set-hint{margin:6px 0 0;color:var(--muted);font-size:.7rem}
.set-ghost-btn{height:27px;padding:0 12px;border:1px solid var(--line2);border-radius:6px;background:var(--card);color:var(--ink);font-size:.73rem;cursor:pointer}
.set-ghost-btn:hover{border-color:var(--accent);color:var(--accent)}
</style>
