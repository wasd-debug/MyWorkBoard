<template>
  <section class="set-pane">
    <template v-if="pane === 'smartlist'">
      <SettingsGroup v-for="group in SMART_LISTS" :key="group.group">
        <SettingRow v-for="item in group.items" :key="item.key" :label="item.label" :icon="ICONS[item.icon]">
          <SettingSelect :model-value="settings[item.key]" :options="SHOW_OPTIONS" :label="item.label" @update:model-value="set(item.key, $event)" />
        </SettingRow>
      </SettingsGroup>
    </template>

    <template v-else-if="pane === 'advanced'">
      <div class="set-section-head">
        <h3>任务默认值</h3>
        <HelpCircle :size="15" class="set-help" aria-label="新建任务时使用的默认值" title="新建任务时使用的默认值" />
      </div>

      <SettingsGroup>
        <SettingRow label="默认日期">
          <SettingSelect :model-value="settings.defaultDate" :options="dateOptions" label="默认日期" @update:model-value="set('defaultDate', $event)" />
        </SettingRow>
        <SettingRow label="默认日期模式">
          <SettingSelect :model-value="settings.defaultDateMode" :options="dateModeOptions" label="默认日期模式" @update:model-value="set('defaultDateMode', $event)" />
        </SettingRow>
        <SettingRow label="默认时间段">
          <SettingSelect :model-value="settings.defaultDuration" :options="durationOptions" label="默认时间段" @update:model-value="set('defaultDuration', $event)" />
        </SettingRow>
        <SettingRow label="默认提醒（有时间的任务）">
          <SettingSelect :model-value="settings.defaultRemindTimed" :options="remindTimedOptions" label="默认提醒（有时间的任务）" @update:model-value="set('defaultRemindTimed', $event)" />
        </SettingRow>
        <SettingRow label="默认提醒（全天任务）">
          <SettingSelect :model-value="settings.defaultRemindAllDay" :options="remindAllDayOptions" label="默认提醒（全天任务）" @update:model-value="set('defaultRemindAllDay', $event)" />
        </SettingRow>
      </SettingsGroup>

      <SettingsGroup>
        <SettingRow label="默认优先级">
          <SettingSelect :model-value="settings.defaultPriority" :options="priorityOptions" label="默认优先级" @update:model-value="set('defaultPriority', $event)" />
        </SettingRow>
        <SettingRow label="默认标签">
          <SettingSelect :model-value="settings.defaultTag" :options="tagOptions" label="默认标签" @update:model-value="set('defaultTag', $event)" />
        </SettingRow>
        <SettingRow label="默认清单">
          <SettingSelect :model-value="settings.defaultList" :options="listOptions" label="默认清单" @update:model-value="set('defaultList', $event)" />
        </SettingRow>
      </SettingsGroup>

      <SettingsGroup>
        <SettingRow label="默认添加到">
          <SettingSelect :model-value="settings.defaultAppend" :options="appendOptions" label="默认添加到" @update:model-value="set('defaultAppend', $event)" />
        </SettingRow>
        <SettingRow label="已过期分组位置">
          <SettingSelect :model-value="settings.overduePosition" :options="positionOptions" label="已过期分组位置" @update:model-value="set('overduePosition', $event)" />
        </SettingRow>
      </SettingsGroup>

      <button type="button" class="set-text-link" @click="resetDefaults">恢复默认设置</button>

      <div class="set-template">
        <button type="button" class="set-template-trigger" :aria-expanded="templateMenuOpen" @click="templateMenuOpen = !templateMenuOpen">
          <span>{{ templateKind === 'note' ? '笔记模板' : '任务模板' }}</span>
          <ChevronDown :size="13" />
        </button>
        <div v-if="templateMenuOpen" class="set-template-menu">
          <button type="button" :class="{ active: templateKind === 'task' }" @click="pickTemplateKind('task')">任务模板<Check v-if="templateKind === 'task'" :size="14" /></button>
          <button type="button" :class="{ active: templateKind === 'note' }" @click="pickTemplateKind('note')">笔记模板<Check v-if="templateKind === 'note'" :size="14" /></button>
        </div>
        <div class="set-template-grid">
          <article v-for="item in templates" :key="item.key" class="set-template-card">
            <label class="set-template-head">
              <input type="checkbox">
              <b>{{ item.label }}</b>
            </label>
            <ul>
              <li v-for="line in item.items" :key="line"><span class="set-template-box" aria-hidden="true"></span>{{ line }}</li>
            </ul>
          </article>
        </div>
      </div>
    </template>

    <template v-else>
      <SettingsGroup>
        <SettingRow label="自动接受共享邀请" desc="启用后，将自动接受来自已知合作者的共享邀请。">
          <SettingSwitch :model-value="settings.autoAcceptShare" label="自动接受共享邀请" @update:model-value="set('autoAcceptShare', $event)" />
        </SettingRow>
      </SettingsGroup>

      <SettingsGroup title="共享清单的默认通知" desc="你可以选择在所有共享清单内默认接收以下类型的通知。若你手动更改过某个特定共享清单的通知类型，那么此默认设置将不再对已更改的共享清单生效。">
        <label v-for="item in shareNotifications" :key="item.key" class="set-check">
          <input type="checkbox" :checked="settings[item.key]" @change="set(item.key, $event.target.checked)">
          <span>{{ item.label }}</span>
        </label>
      </SettingsGroup>
    </template>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { message } from '../../services/message.js'
import { Check, ChevronDown, HelpCircle, Inbox, Archive, Calendar, Sunrise, CalendarDays, User, FileText, Tag, Funnel, CheckCircle2, XCircle, Trash2 } from 'lucide-vue-next'
import { SHOW_OPTIONS, SMART_LISTS } from './settingsState.js'
import { useSettings } from './useSettings.js'
import SettingRow from './SettingRow.vue'
import SettingSelect from './SettingSelect.vue'
import SettingSwitch from './SettingSwitch.vue'
import SettingsGroup from './SettingsGroup.vue'

defineProps({ pane: { type: String, required: true } })

const { settings, set, reset } = useSettings()

const ICONS = {
  inbox: Inbox,
  calendar: Calendar,
  sunrise: Sunrise,
  calendarDays: CalendarDays,
  user: User,
  tray: Archive,
  summary: FileText,
  tag: Tag,
  filter: Funnel,
  checkCircle: CheckCircle2,
  xCircle: XCircle,
  trash: Trash2
}

const dateOptions = [
  { value: 'TODAY', label: '今天' },
  { value: 'NONE', label: '无' },
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
  { value: 'NONE', label: '无' }
]
const remindAllDayOptions = [
  { value: 'NONE', label: '无' },
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
  { value: 'NONE', label: '无' },
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
  { value: 'TOP', label: '清单顶部' },
  { value: 'BOTTOM', label: '清单底部' },
  { value: 'HIDDEN', label: '单独分组' }
]
const shareNotifications = [
  { key: 'shareNotifyComplete', label: '完成 / 取消完成了任务' },
  { key: 'shareNotifyAdd', label: '添加了任务 / 笔记' },
  { key: 'shareNotifyDelete', label: '删除 / 移走了任务 / 笔记' }
]

const templateMenuOpen = ref(false)
const templateKind = ref('task')
const TEMPLATE_LIBRARY = {
  task: [
    { key: 'travel', label: '旅行必备物品', items: ['身份证 / 护照 / 签证', '充电器 / 数据线', '常用药品与口罩'] },
    { key: 'workday', label: '每天工作前要做的几件事', items: ['简单回顾昨天的工作', '梳理今天最重要的三件事', '清空收件箱'] }
  ],
  note: [
    { key: 'meeting', label: '会议纪要', items: ['会议主题与参与人', '讨论要点', '待办与负责人'] },
    { key: 'weekly', label: '每周复盘', items: ['本周完成的事', '卡住的事', '下周计划'] }
  ]
}
const templates = ref(TEMPLATE_LIBRARY.task)
function pickTemplateKind(kind) {
  templateKind.value = kind
  templates.value = TEMPLATE_LIBRARY[kind]
  templateMenuOpen.value = false
}

function resetDefaults() {
  reset(['defaultDate', 'defaultDateMode', 'defaultDuration', 'defaultRemindTimed', 'defaultRemindAllDay', 'defaultPriority', 'defaultTag', 'defaultList', 'defaultAppend', 'overduePosition'])
  message.success('任务默认值已重置')
}
</script>

<style scoped>
.set-pane{display:grid;gap:14px}
.set-section-head{display:flex;align-items:center;gap:6px;margin-bottom:-4px}
.set-section-head h3{margin:0;font-size:.86rem;font-weight:600;color:var(--ink)}
.set-help{color:var(--muted)}
.set-text-link{justify-self:start;padding:0;border:0;background:transparent;color:#3b82f6;font-size:.78rem;cursor:pointer}
.set-text-link:hover{text-decoration:underline}
.set-check{display:flex;align-items:center;gap:9px;min-height:42px;padding:6px 0;color:var(--ink);font-size:.79rem;cursor:pointer}
.set-check + .set-check{border-top:1px solid var(--set-row-line,color-mix(in srgb,var(--line) 76%,transparent))}
.set-check input{flex:none;width:15px;height:15px;margin:0;border-radius:4px;accent-color:#3b82f6;cursor:pointer}
.set-template{position:relative;display:grid;gap:10px;margin-top:4px}
.set-template-trigger{display:inline-flex;align-items:center;gap:5px;justify-self:start;padding:5px 11px;border:1px solid var(--line2);border-radius:7px;background:var(--card);color:var(--ink2);font-size:.78rem;cursor:pointer}
.set-template-trigger:hover{border-color:var(--accent);color:var(--ink)}
.set-template-menu{position:absolute;top:34px;left:0;z-index:3;display:grid;min-width:132px;padding:5px;border:1px solid var(--line);border-radius:9px;background:var(--card);box-shadow:0 10px 24px rgba(15,23,42,.16)}
.set-template-menu button{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:7px 9px;border:0;border-radius:6px;background:transparent;color:var(--ink2);font-size:.78rem;text-align:left;cursor:pointer}
.set-template-menu button:hover{background:var(--set-group-bg)}
.set-template-menu button.active{color:#3b82f6}
.set-template-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.set-template-card{padding:11px 13px;border:1px solid var(--line);border-radius:10px;background:var(--set-group-bg,color-mix(in srgb,var(--paper) 62%,var(--card)))}
.set-template-head{display:flex;align-items:center;gap:8px;cursor:pointer}
.set-template-head b{font-size:.79rem;font-weight:600;color:var(--ink)}
.set-template-head input{width:14px;height:14px;margin:0;accent-color:#3b82f6}
.set-template-card ul{margin:8px 0 0;padding:0;list-style:none;display:grid;gap:6px}
.set-template-card li{display:flex;align-items:center;gap:7px;color:var(--muted);font-size:.72rem;line-height:1.4}
.set-template-box{flex:none;width:12px;height:12px;border:1px solid var(--line2);border-radius:3px}
@media(max-width:820px){.set-template-grid{grid-template-columns:1fr}}
</style>
