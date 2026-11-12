<template>
  <div>
    <section class="tool-card">
      <header>
        <h2>戒烟概览</h2>
        <small v-if="store.summary">按 {{ store.settings?.timezone || 'Asia/Shanghai' }} 统计</small>
      </header>
      <p v-if="store.error" class="tool-error" role="alert">{{ store.error }}</p>
      <p v-else-if="store.loading && !store.loaded" class="tool-hint">正在加载…</p>
      <template v-else>
        <div class="tool-stats">
          <div class="tool-stat is-accent"><b>{{ store.summary?.elapsedDays ?? 0 }}</b><span>已戒烟天数</span></div>
          <div class="tool-stat"><b>{{ store.currentStreak }}</b><span>连续无烟天数</span></div>
          <div class="tool-stat"><b>{{ store.summary?.totalSmoked ?? 0 }}</b><span>累计吸烟支数</span></div>
          <div class="tool-stat"><b>{{ money(store.savedAmount) }}</b><span>省下金额（元）</span></div>
        </div>
        <p v-if="Number(store.savedAmount) < 0" class="tool-hint">当前吸得比基线更多，省下金额为负值。</p>
      </template>
    </section>

    <section class="tool-card">
      <header><h2>今日记录</h2><small>{{ store.todayDate }}</small></header>
      <div class="tool-row">
        <button type="button" class="tool-btn" :disabled="store.todaySmoked <= 0" @click="store.setTodaySmoked(store.todaySmoked - 1)">−1 支</button>
        <b style="font-size:20px;min-width:3ch;text-align:center">{{ store.todaySmoked }}</b>
        <button type="button" class="tool-btn is-primary" @click="store.setTodaySmoked(store.todaySmoked + 1)">+1 支</button>
        <button type="button" class="tool-btn" @click="store.setTodaySmoked(0)">今天一支没抽</button>
      </div>
      <p class="tool-hint">今天不吸烟就保持 0；抽了就点 +1。跨过零点后「今天」由服务端按账号时区切换。</p>

      <div class="tool-row">
        <button type="button" class="tool-btn" @click="cravingOpen = !cravingOpen">
          {{ cravingOpen ? '收起' : '记录一次渴望' }}
        </button>
        <span class="tool-hint">今天 {{ store.summary?.todayCravings ?? 0 }} 次，其中忍住 {{ store.summary?.todayResisted ?? 0 }} 次</span>
      </div>

      <div v-if="cravingOpen" class="craving-form">
        <label class="tool-field">
          <span>强度（1–5）</span>
          <input v-model.number="craving.intensity" type="range" min="1" max="5" step="1" />
        </label>
        <label class="tool-field">
          <span>触发场景</span>
          <select v-model="craving.trigger">
            <option v-for="item in CRAVING_TRIGGERS" :key="item.key" :value="item.key">{{ item.label }}</option>
          </select>
        </label>
        <label class="tool-field">
          <span>备注</span>
          <input v-model="craving.note" type="text" maxlength="120" placeholder="可选" />
        </label>
        <label class="tool-field">
          <span>结果</span>
          <select v-model="craving.resisted">
            <option :value="true">忍住了</option>
            <option :value="false">没忍住</option>
          </select>
        </label>
        <div class="tool-row">
          <button type="button" class="tool-btn is-primary" :disabled="submitting" @click="submitCraving">
            {{ submitting ? '记录中…' : '记录' }}
          </button>
        </div>
      </div>
    </section>

    <section class="tool-card">
      <header><h2>近 30 天</h2><small>灰 = 未记录，绿 = 无烟，红 = 有吸烟</small></header>
      <ol class="heat">
        <li v-for="day in heat" :key="day.date" :class="day.status" :title="`${day.date}：${heatLabel(day)}`">
          <span>{{ day.date.slice(8) }}</span>
        </li>
      </ol>
      <p v-if="!store.configured" class="tool-hint">先补全下方设置，连续天数与省下金额才会开始累计。</p>
    </section>

    <section class="tool-card">
      <header><h2>设置</h2><small>起始时间与基线消耗</small></header>
      <div class="tool-grid">
        <label class="tool-field"><span>开始戒烟时间</span><input v-model="form.startAt" type="datetime-local" /></label>
        <label class="tool-field"><span>原来每天抽多少支</span><input v-model.number="form.baselinePerDay" type="number" min="0" max="200" /></label>
        <label class="tool-field"><span>每包价格（元）</span><input v-model.number="form.pricePerPack" type="number" min="0" step="0.5" /></label>
        <label class="tool-field"><span>每包支数</span><input v-model.number="form.cigarettesPerPack" type="number" min="1" max="100" /></label>
      </div>
      <div class="tool-row">
        <button type="button" class="tool-btn is-primary" :disabled="store.saving" @click="save">
          {{ store.saving ? '保存中…' : '保存设置' }}
        </button>
        <span class="tool-hint">单支价格约 {{ money(store.unitPrice) }} 元</span>
      </div>
      <p class="tool-disclaimer">所有记录按账号保存在服务端，换设备登录后仍在；不参与离线同步。</p>
    </section>

    <section v-if="store.cravings.length" class="tool-card">
      <header><h2>渴望记录</h2><small>最近 30 天</small></header>
      <div class="tool-table-wrap">
        <table class="tool-table">
          <thead><tr><th>时间</th><th>强度</th><th>场景</th><th>结果</th><th></th></tr></thead>
          <tbody>
            <tr v-for="item in store.cravings" :key="item.id">
              <td>{{ timeLabel(item.occurredAt) }}</td>
              <td>{{ item.intensity }}</td>
              <td>{{ triggerLabel(item.trigger) }}</td>
              <td>
                <button type="button" class="tool-btn" style="padding:3px 8px" @click="store.toggleResisted(item)">
                  {{ item.resisted ? '忍住了' : '没忍住' }}
                </button>
              </td>
              <td><button type="button" class="tool-btn" style="padding:3px 8px" @click="store.removeCraving(item)">删除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { CRAVING_TRIGGERS, timeline, triggerLabel } from './quitSmoking.js'
import { useQuitSmokingStore } from '../../stores/quitSmoking.js'

const store = useQuitSmokingStore()
const cravingOpen = ref(false)
const submitting = ref(false)
const craving = reactive({ intensity: 3, trigger: 'after-meal', note: '', resisted: true })
const form = reactive({ startAt: '', baselinePerDay: 20, pricePerPack: 25, cigarettesPerPack: 20 })

const heat = computed(() => timeline(store.days, store.todayDate || undefined, 30))

watch(() => store.settings, settings => {
  if (!settings) return
  form.startAt = toLocalInput(settings.startAt)
  form.baselinePerDay = settings.baselinePerDay
  form.pricePerPack = Number(settings.pricePerPack ?? 0)
  form.cigarettesPerPack = settings.cigarettesPerPack
}, { immediate: true })

onMounted(() => {
  if (!store.loaded && !store.loading) store.fetchAll()
})

async function save() {
  try {
    await store.saveSettings({
      startAt: form.startAt ? new Date(form.startAt).toISOString() : undefined,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      baselinePerDay: Number(form.baselinePerDay),
      pricePerPack: Number(form.pricePerPack),
      cigarettesPerPack: Number(form.cigarettesPerPack)
    })
  } catch {
    // store 已经提示过具体原因
  }
}

async function submitCraving() {
  submitting.value = true
  try {
    await store.addCraving({
      intensity: Number(craving.intensity),
      resisted: craving.resisted,
      trigger: craving.trigger,
      note: craving.note
    })
    craving.note = ''
    cravingOpen.value = false
  } catch {
    // store 已经提示过具体原因
  } finally {
    submitting.value = false
  }
}

function toLocalInput(iso) {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function timeLabel(iso) {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  const pad = value => String(value).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function heatLabel(day) {
  if (day.status === 'unlogged') return '未记录'
  return day.status === 'clean' ? '无烟' : `吸烟 ${day.smoked} 支`
}

function money(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '0.00'
  return number.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>

<style scoped>
.craving-form { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; padding: 12px; border: 1px dashed var(--line); border-radius: 12px; }

.heat { display: grid; grid-template-columns: repeat(10, minmax(0, 1fr)); gap: 6px; margin: 0; padding: 0; list-style: none; }
.heat li {
  display: flex; min-height: 34px; align-items: center; justify-content: center;
  border-radius: 8px; font-size: 11px; font-variant-numeric: tabular-nums;
}
.heat li.clean { background: color-mix(in srgb, #2f9e6f 26%, transparent); color: var(--ink); }
.heat li.relapse { background: color-mix(in srgb, #d2564b 30%, transparent); color: var(--ink); }
.heat li.unlogged { background: color-mix(in srgb, var(--ink) 6%, transparent); color: var(--muted); }

@media (max-width: 560px) {
  .heat { grid-template-columns: repeat(6, minmax(0, 1fr)); }
}
</style>
