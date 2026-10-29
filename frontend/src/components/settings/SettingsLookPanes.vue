<template>
  <section class="set-pane">
    <template v-if="pane === 'appearance'">
      <div class="set-tabs">
        <SettingChoices :model-value="settings.appearanceTab" :options="appearanceTabs" label="外观分类" @update:model-value="set('appearanceTab', $event)" />
      </div>

      <template v-if="settings.appearanceTab === 'THEME'">
        <SettingRow label="界面样式" stacked>
          <SettingChoices :model-value="settings.interfaceStyle" :options="styleOptions" variant="cards" label="界面样式" @update:model-value="set('interfaceStyle', $event)" />
        </SettingRow>
        <SettingRow label="跟随系统切换暗色主题" desc="系统切换为深色外观时，应用自动跟随。">
          <SettingSwitch :model-value="settings.followSystemDark" label="跟随系统切换暗色主题" @update:model-value="set('followSystemDark', $event)" />
        </SettingRow>
        <SettingRow label="颜色主题" stacked>
          <div class="set-swatches" role="radiogroup" aria-label="颜色主题">
            <button
              v-for="theme in COLOR_THEMES"
              :key="theme.key"
              type="button"
              role="radio"
              :aria-checked="settings.colorTheme === theme.key"
              :aria-label="theme.label"
              :class="{ active: settings.colorTheme === theme.key }"
              @click="set('colorTheme', theme.key)"
            ><span :style="{ background: theme.color }"></span><small>{{ theme.label }}</small></button>
          </div>
        </SettingRow>
        <SettingRow label="季节主题" stacked>
          <div class="set-swatches" role="radiogroup" aria-label="季节主题">
            <button
              v-for="theme in SEASON_THEMES"
              :key="theme.key"
              type="button"
              role="radio"
              :aria-checked="settings.seasonTheme === theme.key"
              :aria-label="theme.label"
              :class="{ active: settings.seasonTheme === theme.key }"
              @click="set('seasonTheme', settings.seasonTheme === theme.key ? 'NONE' : theme.key)"
            ><span :style="{ background: theme.color }"></span><small>{{ theme.label }}</small></button>
          </div>
        </SettingRow>
        <SettingRow label="城市主题" stacked>
          <div class="set-swatches" role="radiogroup" aria-label="城市主题">
            <button
              v-for="theme in CITY_THEMES"
              :key="theme.key"
              type="button"
              role="radio"
              :aria-checked="settings.cityTheme === theme.key"
              :aria-label="theme.label"
              :class="{ active: settings.cityTheme === theme.key }"
              @click="set('cityTheme', settings.cityTheme === theme.key ? 'NONE' : theme.key)"
            ><span :style="{ background: theme.color }"></span><small>{{ theme.label }}</small></button>
          </div>
        </SettingRow>
      </template>

      <template v-else-if="settings.appearanceTab === 'ICON'">
        <SettingRow label="应用图标" stacked desc="仅桌面客户端生效，Web 端仅作为预览。">
          <div class="set-icon-grid" role="radiogroup" aria-label="应用图标">
            <button v-for="icon in iconOptions" :key="icon.key" type="button" role="radio" :aria-checked="icon.key === activeIcon" :class="{ active: icon.key === activeIcon }" @click="activeIcon = icon.key">
              <span :style="{ background: icon.background }">{{ icon.symbol }}</span>
              <small>{{ icon.label }}</small>
            </button>
          </div>
        </SettingRow>
      </template>

      <template v-else>
        <SettingRow label="字体大小">
          <SettingChoices :model-value="settings.fontSize" :options="fontOptions" label="字体大小" @update:model-value="set('fontSize', $event)" />
        </SettingRow>
        <SettingRow label="清单颜色">
          <SettingChoices :model-value="settings.listColor" :options="showHideOptions" label="清单颜色" @update:model-value="set('listColor', $event)" />
        </SettingRow>
        <SettingRow label="侧边栏数量">
          <SettingSelect :model-value="settings.sidebarCount" :options="sidebarCountOptions" @update:model-value="set('sidebarCount', $event)" />
        </SettingRow>
        <SettingRow label="已完成任务样式">
          <SettingChoices :model-value="settings.completedStyle" :options="completedOptions" label="已完成任务样式" @update:model-value="set('completedStyle', $event)" />
        </SettingRow>
      </template>
    </template>

    <template v-else-if="pane === 'sticky'">
      <SettingRow label="默认颜色" stacked>
        <div class="set-swatches is-compact" role="radiogroup" aria-label="便签默认颜色">
          <button
            v-for="color in stickyColors"
            :key="color"
            type="button"
            role="radio"
            :aria-checked="settings.stickyColor === color"
            :aria-label="`便签颜色 ${color}`"
            :class="{ active: settings.stickyColor === color }"
            @click="set('stickyColor', color)"
          ><span :style="{ background: color }"></span></button>
        </div>
      </SettingRow>
      <SettingRow label="不透明度" desc="数值越低，便签越透。">
        <div class="set-volume">
          <input v-model.number="settings.stickyOpacity" type="range" min="30" max="100" step="1" aria-label="便签不透明度">
          <b>{{ settings.stickyOpacity }}%</b>
        </div>
      </SettingRow>
      <SettingRow label="字体大小">
        <SettingChoices :model-value="settings.stickyFont" :options="fontOptions" label="便签字号" @update:model-value="set('stickyFont', $event)" />
      </SettingRow>
      <SettingRow label="默认置顶" desc="新建便签时默认置于其他窗口之上。">
        <SettingSwitch :model-value="settings.stickyTop" label="默认置顶" @update:model-value="set('stickyTop', $event)" />
      </SettingRow>
      <SettingRow label="不在任务栏中显示便签" desc="便签窗口不在任务栏中显示，改用快捷键唤起。">
        <SettingSwitch :model-value="settings.stickyHideTaskbar" label="不在任务栏中显示便签" @update:model-value="set('stickyHideTaskbar', $event)" />
      </SettingRow>
      <SettingRow label="恢复便签的默认大小" desc="重新打开便签时使用默认尺寸，而不是上次调整后的大小。">
        <SettingSwitch :model-value="settings.stickyRestoreSize" label="恢复便签的默认大小" @update:model-value="set('stickyRestoreSize', $event)" />
      </SettingRow>
      <SettingRow label="便签默认间距">
        <SettingChoices :model-value="settings.stickyGap" :options="gapOptions" label="便签默认间距" @update:model-value="set('stickyGap', $event)" />
      </SettingRow>
    </template>

    <template v-else>
      <div v-for="group in SHORTCUT_GROUPS" :key="group.title" class="set-shortcut-group">
        <h4 class="set-group-title">{{ group.title }}</h4>
        <p v-if="group.desc" class="set-hint">{{ group.desc }}</p>
        <div v-for="item in group.items" :key="item.label" class="set-shortcut-row">
          <span>{{ item.label }}</span>
          <span v-if="item.unset" class="set-shortcut-unset">设置快捷键</span>
          <span v-else class="set-keys">
            <template v-if="item.sequences">
              <kbd v-for="key in item.sequences[0]" :key="`${item.label}-${key}`">{{ key }}</kbd>
              <em v-if="item.sequences[0].length > 1">然后</em>
            </template>
            <template v-else>
              <kbd v-for="key in item.keys" :key="`${item.label}-${key}`">{{ key }}</kbd>
            </template>
          </span>
        </div>
      </div>
      <div class="set-actions">
        <button type="button" class="set-ghost-btn" @click="restoreShortcuts">恢复默认设置</button>
      </div>
    </template>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { message } from '../../services/message.js'
import { CITY_THEMES, COLOR_THEMES, SEASON_THEMES, SHORTCUT_GROUPS } from './settingsState.js'
import { useSettings } from './useSettings.js'
import SettingChoices from './SettingChoices.vue'
import SettingRow from './SettingRow.vue'
import SettingSelect from './SettingSelect.vue'
import SettingSwitch from './SettingSwitch.vue'

defineProps({ pane: { type: String, required: true } })

const { settings, set } = useSettings()

const appearanceTabs = [
  { value: 'THEME', label: '主题' },
  { value: 'ICON', label: '应用图标' },
  { value: 'DISPLAY', label: '显示' }
]
const styleOptions = [
  { value: 'CARD', label: '卡片', desc: '默认，留白充足' },
  { value: 'COMPACT', label: '紧凑', desc: '一屏更多信息' },
  { value: 'IMMERSIVE', label: '沉浸', desc: '弱化边框与分隔' }
]
const fontOptions = [
  { value: 'NORMAL', label: '正常' },
  { value: 'LARGE', label: '大' },
  { value: 'HUGE', label: '超大' }
]
const showHideOptions = [
  { value: 'SHOW', label: '显示' },
  { value: 'HIDE', label: '隐藏' }
]
const sidebarCountOptions = [
  { value: 'ALL', label: '显示所有' },
  { value: 'HIDDEN_ONLY', label: '显示隐藏笔记' },
  { value: 'NONE', label: '隐藏所有' }
]
const completedOptions = [
  { value: 'STANDARD', label: '标准' },
  { value: 'STRIKE', label: '划线' }
]
const gapOptions = [
  { value: 'NONE', label: '无' },
  { value: 'NORMAL', label: '正常' },
  { value: 'LARGE', label: '大' },
  { value: 'HUGE', label: '超大' }
]
const stickyColors = ['#ffe8a3', '#ffd0d6', '#d6e8ff', '#d5f0e0', '#ece0ff', '#ffe0c2', '#e4e4e6', '#c9f2ef']
const iconOptions = [
  { key: 'DEFAULT', label: '默认', symbol: '✓', background: '#3b82f6' },
  { key: 'DARK', label: '深色', symbol: '✓', background: '#22262f' },
  { key: 'COLOR', label: '彩色', symbol: '✓', background: 'linear-gradient(135deg,#f0a08a,#7f76c9)' },
  { key: 'MINIMAL', label: '简约', symbol: '○', background: '#f2f0ec' }
]
const activeIcon = ref('DEFAULT')
function restoreShortcuts() {
  message.success('快捷键已恢复默认')
}
</script>

<style scoped>
.set-pane{display:grid;gap:4px}
.set-tabs{display:flex;padding-bottom:4px}
.set-swatches{display:flex;flex-wrap:wrap;gap:10px;width:100%}
.set-swatches button{display:grid;justify-items:center;gap:3px;padding:0;border:0;background:transparent;cursor:pointer}
.set-swatches button>span{display:block;width:30px;height:30px;border-radius:50%;box-shadow:inset 0 0 0 1px rgba(0,0,0,.08)}
.set-swatches button small{color:var(--muted);font-size:.62rem}
.set-swatches button.active>span{box-shadow:0 0 0 2px var(--card),0 0 0 4px #3b82f6}
.set-swatches.is-compact{gap:8px}
.set-swatches.is-compact button>span{width:22px;height:22px}
.set-icon-grid{display:flex;gap:14px;flex-wrap:wrap}
.set-icon-grid button{display:grid;justify-items:center;gap:5px;padding:0;border:0;background:transparent;cursor:pointer}
.set-icon-grid button>span{display:grid;width:44px;height:44px;place-items:center;border-radius:11px;color:#fff;font-size:1.1rem;font-weight:700}
.set-icon-grid button small{color:var(--muted);font-size:.64rem}
.set-icon-grid button.active>span{box-shadow:0 0 0 2px var(--card),0 0 0 4px #3b82f6}
.set-volume{display:inline-flex;align-items:center;gap:9px}
.set-volume input{width:132px;accent-color:#3b82f6}
.set-volume b{min-width:32px;color:var(--muted);font-size:.72rem;font-weight:500;text-align:right}
.set-shortcut-group{padding-bottom:8px}
.set-group-title{margin:14px 0 4px;color:var(--ink2);font-size:.74rem;font-weight:600}
.set-shortcut-group:first-child .set-group-title{margin-top:2px}
.set-shortcut-row{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:6px 0;border-bottom:1px solid color-mix(in srgb,var(--line) 55%,transparent);font-size:.77rem}
.set-shortcut-row:last-child{border-bottom:0}
.set-keys{display:inline-flex;align-items:center;gap:4px}
.set-keys kbd{min-width:20px;padding:1px 6px;border:1px solid var(--line2);border-bottom-width:2px;border-radius:5px;background:var(--panel2);color:var(--ink2);font-family:inherit;font-size:.68rem;text-align:center}
.set-keys em{color:var(--muted);font-size:.66rem;font-style:normal}
.set-shortcut-unset{color:var(--accent);font-size:.7rem;cursor:pointer}
.set-shortcut-unset:hover{text-decoration:underline}
.set-hint{margin:2px 0 4px;color:var(--muted);font-size:.7rem}
.set-actions{display:flex;justify-content:flex-start;padding-top:10px}
.set-ghost-btn{height:27px;padding:0 12px;border:1px solid var(--line2);border-radius:6px;background:var(--card);color:var(--ink);font-size:.73rem;cursor:pointer}
.set-ghost-btn:hover{border-color:var(--accent);color:var(--accent)}
</style>
