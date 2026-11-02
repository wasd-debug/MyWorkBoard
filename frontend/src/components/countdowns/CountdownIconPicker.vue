<template>
  <Teleport to="body">
    <div v-if="open" class="ip-mask" role="dialog" aria-modal="true" aria-label="选择图标" @click.self="emit('close')">
      <section class="ip-sheet" @click.stop>
        <div class="ip-tabs" role="tablist">
          <button
            v-for="tab in TABS"
            :key="tab.key"
            type="button"
            role="tab"
            :aria-selected="tabKey === tab.key"
            :class="{ on: tabKey === tab.key }"
            @click="switchTab(tab.key)"
          >{{ tab.label }}</button>
        </div>

        <div v-if="tabKey === 'ICON'" class="ip-body is-grid">
          <button
            v-for="(sticker, index) in ICON_STICKERS"
            :key="`${sticker.value}-${index}`"
            type="button"
            class="ip-sticker"
            :class="{ on: draft.type === 'ICON' && draft.value === sticker.value && draft.color === sticker.color }"
            :style="{ background: sticker.color }"
            :aria-label="`图标 ${sticker.value}`"
            @click="pickSticker(sticker)"
          >
            <span>{{ sticker.value }}</span>
            <i v-if="draft.type === 'ICON' && draft.value === sticker.value && draft.color === sticker.color" class="ip-check">
              <Check :size="11" />
            </i>
          </button>
        </div>

        <div v-else-if="tabKey === 'EMOJI'" class="ip-body">
          <div class="ip-search">
            <Search :size="15" />
            <input v-model.trim="query" type="search" placeholder="搜索" aria-label="搜索 Emoji" />
          </div>
          <div class="ip-cats">
            <button
              v-for="category in (query ? [] : EMOJI_CATEGORIES)"
              :key="category.key"
              type="button"
              :class="{ on: category.key === activeCategory }"
              :title="category.label"
              @click="activeCategory = category.key"
            >{{ category.icon }}</button>
            <span class="ip-cats-gap"></span>
            <button type="button" class="ip-link" @click="randomEmoji">随机</button>
            <button type="button" class="ip-link" :disabled="!draft.value" @click="resetEmoji">重置</button>
          </div>
          <p class="ip-cat-label">{{ query ? `搜索「${query}」` : currentCategory.label }}</p>
          <div class="ip-emoji-grid">
            <button
              v-for="emoji in visibleEmoji"
              :key="emoji"
              type="button"
              :class="{ on: draft.type === 'EMOJI' && draft.value === emoji }"
              @click="pickEmoji(emoji)"
            >{{ emoji }}</button>
          </div>
          <p v-if="!visibleEmoji.length" class="ip-empty">没有匹配的 Emoji</p>
        </div>

        <div v-else class="ip-body is-text">
          <input
            v-model="textValue"
            class="ip-text"
            maxlength="1"
            placeholder="输入 1 个字"
            aria-label="图标文字"
            @input="onTextInput"
          />
        </div>

        <div class="ip-colors">
          <span class="ip-colors-label">颜色</span>
          <button
            v-for="color in ICON_COLORS"
            :key="color"
            type="button"
            class="ip-color"
            :class="{ on: draft.color === color, 'is-rainbow': color === 'rainbow' }"
            :style="color === 'rainbow' ? {} : { background: color }"
            :aria-label="`颜色 ${color}`"
            @click="draft.color = color"
          ></button>
        </div>

        <footer class="ip-foot">
          <button type="button" class="ip-btn" @click="emit('close')">取消</button>
          <button type="button" class="ip-btn is-primary" :disabled="!canSubmit" @click="submit">确定</button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Check, Search } from 'lucide-vue-next'
import { EMOJI_CATEGORIES, EMOJI_KEYWORDS, ICON_STICKERS, ICON_COLORS } from './emojiSet.js'

const props = defineProps({
  open: Boolean,
  modelValue: {
    type: Object,
    default: () => ({ type: 'EMOJI', value: '⏳', color: '#f2b13c' })
  }
})
const emit = defineEmits(['close', 'submit'])

const TABS = [
  { key: 'ICON', label: '图标' },
  { key: 'EMOJI', label: 'Emoji' },
  { key: 'TEXT', label: '文字' }
]

const tabKey = ref('ICON')
const draft = ref({ ...props.modelValue })
const query = ref('')
const activeCategory = ref('smileys')
const textValue = ref('')

const currentCategory = computed(() => EMOJI_CATEGORIES.find(item => item.key === activeCategory.value) || EMOJI_CATEGORIES[0])
const visibleEmoji = computed(() => {
  const keyword = query.value.trim()
  if (!keyword) return currentCategory.value.items
  return EMOJI_CATEGORIES.flatMap(category => category.items).filter(emoji => {
    const text = `${emoji} ${EMOJI_KEYWORDS[emoji] || ''}`
    return text.includes(keyword)
  })
})
const canSubmit = computed(() => Boolean(draft.value.value))

watch(() => props.open, open => {
  if (!open) return
  const source = props.modelValue || {}
  draft.value = { type: source.type || 'EMOJI', value: source.value || '⏳', color: source.color || '#f2b13c' }
  // 默认落在「图标」页签（与参考图一致），只有本身就是文字图标时才切到文字页
  tabKey.value = draft.value.type === 'TEXT' ? 'TEXT' : 'ICON'
  textValue.value = draft.value.type === 'TEXT' ? draft.value.value : ''
  query.value = ''
  activeCategory.value = 'smileys'
})

function switchTab(key) {
  tabKey.value = key
  if (key === 'TEXT' && draft.value.type !== 'TEXT') textValue.value = ''
}

function pickSticker(sticker) {
  draft.value = { type: 'ICON', value: sticker.value, color: sticker.color }
}

function pickEmoji(emoji) {
  draft.value = { ...draft.value, type: 'EMOJI', value: emoji }
}

function randomEmoji() {
  const pool = visibleEmoji.value.length ? visibleEmoji.value : currentCategory.value.items
  pickEmoji(pool[Math.floor(Math.random() * pool.length)])
}

function resetEmoji() {
  draft.value = { ...draft.value, value: '' }
}

function onTextInput() {
  const value = textValue.value.slice(0, 1)
  textValue.value = value
  if (value) draft.value = { ...draft.value, type: 'TEXT', value }
}

function submit() {
  if (!canSubmit.value) return
  emit('submit', { ...draft.value })
}
</script>

<style scoped>
.ip-mask{position:fixed;inset:0;z-index:90;display:grid;place-items:center;padding:24px;background:rgba(20,24,32,.32)}
.ip-sheet{display:flex;width:min(552px,100%);max-height:min(86vh,700px);flex-direction:column;padding:16px 18px 14px;border-radius:16px;background:var(--card,#fff);box-shadow:0 30px 70px rgba(15,23,42,.3)}
.ip-tabs{display:flex;flex:none;gap:4px;margin:0 auto 14px;padding:4px;border-radius:11px;background:color-mix(in srgb,var(--ink,#1d1d21) 7%,transparent)}
.ip-tabs button{min-width:96px;height:32px;padding:0 16px;border:0;border-radius:8px;background:transparent;color:var(--ink2,#565d6b);font:inherit;font-size:14px;cursor:pointer}
.ip-tabs button.on{background:var(--card,#fff);color:var(--ink,#1d1d21);font-weight:600;box-shadow:0 1px 4px rgba(15,23,42,.12)}
.ip-body{flex:1;min-height:0;overflow-y:auto;padding:2px 2px 6px;scrollbar-gutter:stable}
.ip-body.is-grid{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:14px 10px;padding:10px 4px 8px}
.ip-sticker{position:relative;display:grid;width:52px;height:52px;margin:0 auto;place-items:center;border:0;border-radius:50%;font-size:25px;line-height:1;cursor:pointer;transition:transform .12s ease}
.ip-sticker:hover{transform:scale(1.06)}
.ip-sticker.on{box-shadow:0 0 0 2px var(--card,#fff),0 0 0 4px #3d8fce}
.ip-check{position:absolute;right:-2px;bottom:-2px;display:grid;width:18px;height:18px;place-items:center;border:2px solid var(--card,#fff);border-radius:50%;background:#3d8fce;color:#fff}
.ip-search{display:flex;align-items:center;gap:8px;height:40px;margin:2px 2px 10px;padding:0 12px;border:1.5px solid #3d8fce;border-radius:10px;color:var(--muted,#8b92a3)}
.ip-search input{flex:1;min-width:0;border:0;background:transparent;color:var(--ink,#1d1d21);font:inherit;font-size:14px;outline:none}
.ip-cats{display:flex;align-items:center;gap:6px;padding:0 2px 6px}
.ip-cats button{display:grid;flex:none;width:30px;height:30px;min-width:30px;place-items:center;border:0;border-radius:8px;background:transparent;font-size:18px;cursor:pointer;opacity:.55}
.ip-cats button:hover{background:color-mix(in srgb,var(--ink,#1d1d21) 7%,transparent);opacity:1}
.ip-cats button.on{background:color-mix(in srgb,#3d8fce 14%,transparent);opacity:1}
.ip-cats-gap{flex:1;height:20px;border-left:1px solid var(--line,#e6e6ea);margin:0 4px}
.ip-cats .ip-link{width:auto;padding:0 6px;color:var(--muted,#8b92a3);font-size:13px;opacity:1}
.ip-link:disabled{opacity:.4;cursor:default}
.ip-cat-label{margin:4px 2px 6px;color:var(--muted,#8b92a3);font-size:12.5px}
.ip-emoji-grid{display:grid;grid-template-columns:repeat(10,minmax(0,1fr));gap:2px;padding:0 2px}
.ip-emoji-grid button{height:34px;border:0;border-radius:8px;background:transparent;font-size:21px;line-height:1;cursor:pointer}
.ip-emoji-grid button:hover{background:color-mix(in srgb,var(--ink,#1d1d21) 7%,transparent)}
.ip-emoji-grid button.on{background:color-mix(in srgb,#3d8fce 20%,transparent)}
.ip-empty{margin:24px 0;color:var(--muted,#8b92a3);font-size:13px;text-align:center}
.ip-body.is-text{display:flex;align-items:flex-start;justify-content:center;padding-top:12px}
.ip-text{width:100%;height:46px;padding:0 14px;border:1.5px solid #3d8fce;border-radius:10px;background:transparent;color:var(--ink,#1d1d21);font:inherit;font-size:20px;text-align:center;outline:none}
.ip-colors{display:flex;flex:none;align-items:center;gap:10px;padding:10px 2px 4px}
.ip-colors-label{flex:none;color:var(--ink2,#565d6b);font-size:14px}
.ip-color{width:24px;height:24px;min-width:24px;min-height:24px;border:0;border-radius:50%;cursor:pointer;box-shadow:0 0 0 1px rgba(15,23,42,.06)}
.ip-color.on{box-shadow:0 0 0 2px var(--card,#fff),0 0 0 4px #3d8fce}
.ip-color.is-rainbow{background:conic-gradient(#f5a3a3,#f7d08a,#a8d977,#7ecfe0,#9aa8ef,#e6a3d8,#f5a3a3)}
.ip-foot{display:flex;flex:none;justify-content:center;gap:14px;padding-top:12px}
.ip-btn{min-width:120px;height:38px;padding:0 20px;border:1px solid var(--line,#e2e4ea);border-radius:9px;background:var(--card,#fff);color:var(--ink,#1d1d21);font:inherit;font-size:14.5px;cursor:pointer}
.ip-btn.is-primary{border-color:transparent;background:#3d8fce;color:#fff;font-weight:600}
.ip-btn:disabled{opacity:.45;cursor:default}
@media(max-width:520px){
  .ip-sheet{max-height:88vh;padding:14px}
  .ip-body.is-grid{grid-template-columns:repeat(5,minmax(0,1fr));gap:10px}
  .ip-emoji-grid{grid-template-columns:repeat(8,minmax(0,1fr))}
  .ip-btn{min-width:92px}
}
</style>
