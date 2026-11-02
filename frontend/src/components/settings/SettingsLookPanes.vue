<template>
  <section class="set-pane">
    <div class="set-segmented" role="tablist" aria-label="外观分类">
      <button
        v-for="tab in appearanceTabs"
        :key="tab.value"
        type="button"
        role="tab"
        :aria-selected="settings.appearanceTab === tab.value"
        :class="{ active: settings.appearanceTab === tab.value }"
        @click="set('appearanceTab', tab.value)"
      >{{ tab.label }}</button>
    </div>

    <template v-if="settings.appearanceTab === 'THEME'">
      <section class="set-theme-block">
        <h4 class="set-theme-title">颜色主题</h4>
        <div class="set-color-grid">
          <button
            v-for="theme in COLOR_THEMES"
            :key="theme.key"
            type="button"
            :aria-label="theme.label"
            :aria-pressed="settings.colorTheme === theme.key"
            :class="{ active: settings.colorTheme === theme.key }"
            @click="set('colorTheme', theme.key)"
          >
            <span class="set-color-swatch" :style="{ background: theme.color }">
              <svg v-if="settings.colorTheme === theme.key" class="set-color-check" viewBox="0 0 16 16" aria-hidden="true"><circle cx="8" cy="8" r="8" fill="#3b82f6" /><path d="M4.6 8.2l2.2 2.2 4.4-4.6" fill="none" stroke="#fff" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
            </span>
            <small>{{ theme.label }}</small>
          </button>
        </div>
      </section>

      <section v-for="series in artSeries" :key="series.key" class="set-theme-block">
        <h4 class="set-theme-title">{{ series.title }}</h4>
        <div class="set-art-grid">
          <button
            v-for="item in series.items"
            :key="item.key"
            type="button"
            class="set-art"
            :aria-label="item.label"
            :aria-pressed="settings[series.key] === item.key"
            :class="{ active: settings[series.key] === item.key }"
            @click="set(series.key, settings[series.key] === item.key ? 'NONE' : item.key)"
          >
            <span class="set-art-img">
              <img v-if="item.image" :src="item.image" :alt="item.label" loading="lazy">
              <i v-else class="set-art-fallback" :style="{ background: item.art }" aria-hidden="true"></i>
              <u class="set-art-corner" aria-hidden="true"></u>
              <svg v-if="settings[series.key] === item.key" class="set-art-check" viewBox="0 0 16 16" aria-hidden="true"><circle cx="8" cy="8" r="8" fill="#3b82f6" /><path d="M4.6 8.2l2.2 2.2 4.4-4.6" fill="none" stroke="#fff" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" /></svg>
            </span>
            <small>{{ item.label }}</small>
          </button>
          <button v-if="series.key === 'photoTheme'" type="button" class="set-art is-custom" aria-label="自定义主题" @click="addCustomTheme">
            <span class="set-art-img">
              <span class="set-art-plus"><Plus :size="16" /></span>
            </span>
            <small>自定义</small>
          </button>
        </div>
      </section>
    </template>

    <template v-else-if="settings.appearanceTab === 'ICON'">
      <section class="set-theme-block">
        <h4 class="set-theme-title">应用图标</h4>
        <p class="set-theme-hint">仅桌面客户端生效，Web 端仅作为预览。</p>
        <div class="set-icon-grid" role="radiogroup" aria-label="应用图标">
          <button v-for="icon in iconOptions" :key="icon.key" type="button" role="radio" :aria-checked="icon.key === activeIcon" :class="{ active: icon.key === activeIcon }" @click="activeIcon = icon.key">
            <span :style="{ background: icon.background }">{{ icon.symbol }}</span>
            <small>{{ icon.label }}</small>
          </button>
        </div>
      </section>
    </template>

    <template v-else>
      <SettingsGroup>
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
      </SettingsGroup>
    </template>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { Plus } from 'lucide-vue-next'
import { message } from '../../services/message.js'
import { CITY_THEMES, COLOR_THEMES, PHOTO_THEMES, SEASON_THEMES } from './settingsState.js'
import { THEME_ART } from './themeArt.js'
import { useSettings } from './useSettings.js'
import SettingChoices from './SettingChoices.vue'
import SettingRow from './SettingRow.vue'
import SettingSelect from './SettingSelect.vue'
import SettingsGroup from './SettingsGroup.vue'

defineProps({ pane: { type: String, required: true } })

const { settings, set } = useSettings()

const appearanceTabs = [
  { value: 'THEME', label: '主题' },
  { value: 'ICON', label: '应用图标' },
  { value: 'DISPLAY', label: '显示' }
]
const artSeries = [
  { key: 'seasonTheme', title: '季节系列', items: SEASON_THEMES },
  { key: 'cityTheme', title: '城市系列', items: CITY_THEMES },
  { key: 'photoTheme', title: '摄影系列', items: PHOTO_THEMES }
].map(series => ({
  ...series,
  items: series.items.map(item => ({ ...item, image: THEME_ART[series.key]?.[item.key] || '' }))
}))
const activeIcon = ref('DEFAULT')
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
const iconOptions = [
  { key: 'DEFAULT', label: '默认', symbol: '✓', background: '#3b82f6' },
  { key: 'DARK', label: '深色', symbol: '✓', background: '#22262f' },
  { key: 'COLOR', label: '彩色', symbol: '✓', background: 'linear-gradient(135deg,#f0a08a,#7f76c9)' },
  { key: 'MINIMAL', label: '简约', symbol: '○', background: '#f2f0ec' }
]
function addCustomTheme() {
  message.info('自定义主题：可上传一张图片作为背景，功能待接入')
}
</script>

<style scoped>
.set-pane{display:grid;gap:18px}
.set-segmented{display:inline-flex;align-items:center;justify-self:center;gap:2px;padding:3px;border-radius:10px;background:var(--set-group-bg,color-mix(in srgb,var(--paper) 62%,var(--card)))}
.set-segmented button{min-height:28px;padding:0 14px;border:0;border-radius:8px;background:transparent;color:var(--muted);font-size:.78rem;cursor:pointer;transition:background .14s ease,color .14s ease}
.set-segmented button:hover{color:var(--ink)}
.set-segmented button.active{background:color-mix(in srgb,var(--ink2) 16%,transparent);color:var(--ink);font-weight:600}
.set-theme-block{display:grid;gap:10px}
.set-theme-title{margin:0;color:var(--ink);font-size:.82rem;font-weight:600}
.set-theme-hint{margin:-4px 0 0;color:var(--muted);font-size:.7rem}
.set-color-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(74px,1fr));gap:14px 12px}
.set-color-grid button{display:grid;justify-items:center;gap:7px;padding:0;border:0;background:transparent;cursor:pointer}
.set-color-swatch{position:relative;display:block;width:100%;max-width:64px;aspect-ratio:1;border-radius:10px;box-shadow:inset 0 0 0 1px rgba(15,23,42,.08)}
.set-color-grid button.active .set-color-swatch{box-shadow:0 0 0 2px var(--card),0 0 0 4px #3b82f6}
.set-color-grid small{color:var(--muted);font-size:.7rem}
.set-color-grid button.active small{color:var(--ink)}
.set-color-check{position:absolute;right:-5px;bottom:-5px;width:16px;height:16px}
.set-art-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(168px,1fr));gap:16px 12px}
.set-art{display:grid;gap:7px;padding:0;border:0;background:transparent;cursor:pointer}
.set-art-img{position:relative;display:block;overflow:hidden;aspect-ratio:16/10;border-radius:9px;background:var(--set-group-bg);box-shadow:inset 0 0 0 1px rgba(15,23,42,.08)}
.set-art-img img,.set-art-fallback{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}
.set-art-corner{position:absolute;top:0;left:0;width:16px;height:16px;background:#f5b942;clip-path:polygon(0 0,100% 0,0 100%)}
.set-art-check{position:absolute;right:5px;bottom:5px;width:16px;height:16px}
.set-art small{color:var(--muted);font-size:.7rem;text-align:center}
.set-art.active small{color:var(--ink)}
.set-art.active .set-art-img{box-shadow:0 0 0 2px #3b82f6}
.set-art.is-custom .set-art-img{display:grid;place-items:center;border:1px dashed var(--line2);background:transparent;box-shadow:none}
.set-art-plus{display:grid;width:26px;height:26px;place-items:center;border-radius:50%;background:color-mix(in srgb,var(--ink2) 14%,transparent);color:var(--ink2)}
.set-icon-grid{display:flex;gap:14px;flex-wrap:wrap}
.set-icon-grid button{display:grid;justify-items:center;gap:5px;padding:0;border:0;background:transparent;cursor:pointer}
.set-icon-grid button>span{display:grid;width:44px;height:44px;place-items:center;border-radius:11px;color:#fff;font-size:1.1rem;font-weight:700}
.set-icon-grid button small{color:var(--muted);font-size:.64rem}
.set-icon-grid button.active>span{box-shadow:0 0 0 2px var(--card),0 0 0 4px #3b82f6}
@media(max-width:640px){
  .set-art-grid{grid-template-columns:repeat(auto-fill,minmax(96px,1fr))}
  .set-color-grid{grid-template-columns:repeat(auto-fill,minmax(52px,1fr))}
}
</style>
