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

    <section class="tool-card recovery">
      <header>
        <h2>身体恢复倒计时</h2>
        <small>按最后一支烟的时刻计算</small>
      </header>
      <p v-if="!recovery" class="tool-hint">补全下方设置后，这里会开始倒计时。</p>
      <template v-else>
        <div class="recovery-lead">
          <div class="recovery-head">
            <span v-if="recovery.nextMilestoneLabel">距离「{{ recovery.nextMilestoneLabel }}」</span>
            <span v-else>全部里程碑已达成</span>
            <b v-if="recovery.nextMilestoneLabel">{{ formatRemaining(recovery.nextRemainingMinutes) }}</b>
            <b v-else>✓</b>
          </div>
          <div class="recovery-progress" :aria-label="`进度 ${Math.round((recovery.nextProgress || 0) * 100)}%`">
            <span :style="{ width: `${Math.min(100, Math.max(0, (recovery.nextProgress || 0) * 100))}%` }"></span>
          </div>
          <p class="recovery-note">{{ recovery.encouragement }}</p>
          <p class="tool-hint">已跨过 {{ recovery.reachedCount }} / {{ recovery.totalCount }} 个节点</p>
        </div>

        <div v-for="group in milestoneGroups" :key="group.key" class="milestone-group">
          <h3>{{ group.label }} <small>{{ reachedIn(group) }}/{{ group.items.length }}</small></h3>
          <ul class="milestone-list">
            <li
              v-for="item in group.items"
              :key="item.key"
              :class="{ 'is-reached': item.reached, 'is-next': item.key === nextKey }"
            >
              <span class="milestone-dot" aria-hidden="true"></span>
              <div class="milestone-body">
                <div class="milestone-row">
                  <b>{{ item.label }}</b>
                  <em>{{ item.reached ? '已达成' : formatRemaining(item.remainingMinutes) }}</em>
                </div>
                <p v-if="item.key === nextKey">{{ item.encouragement }}</p>
                <p v-else-if="item.reached && item.key === latestReachedKey">刚达成：{{ item.detail }}</p>
                <p v-else-if="!item.reached">{{ item.atLabel }}后 · {{ item.detail }}</p>
              </div>
            </li>
          </ul>
        </div>
        <p class="tool-disclaimer">
          时间点取自 CDC 与美国卫生总监报告的戒烟获益时间线，以及临床戒烟指南对尼古丁戒断与心理依赖的通行结论；
          属群体统计，个体差异很大，不构成医学诊断。复吸不会清零倒计时，但会让部分修复重新开始。
        </p>
      </template>
    </section>

    <section class="tool-card">
      <header><h2>焦油与一氧化碳</h2><small>按种类与支数自动折算</small></header>
      <div class="tool-stats">
        <div class="tool-stat"><b>{{ formatMg(substance?.todayTarMg) }}</b><span>今日焦油</span></div>
        <div class="tool-stat"><b>{{ formatMg(substance?.todayCoMg) }}</b><span>今日一氧化碳</span></div>
        <div class="tool-stat is-accent"><b>{{ formatMg(substance?.avoidedTarMg) }}</b><span>累计少吸入焦油</span></div>
        <div class="tool-stat"><b>{{ formatMg(substance?.avoidedCoMg) }}</b><span>累计少吸入一氧化碳</span></div>
      </div>
      <p class="tool-hint">
        当前按每支 {{ tarPerCigarette }} mg 焦油、{{ coPerCigarette }} mg 一氧化碳折算；
        种类与含量在下方「设置」里调整，改完立即重算。
      </p>
      <p v-if="Number(substance?.avoidedTarMg) < 0" class="tool-hint">吸得比基线更多，累计少吸入为负值。</p>
      <p class="tool-disclaimer">
        估算值：不同品牌实测差异很大，实际吸入量还受吸食深度、过滤嘴通风和燃烧方式影响，只用来看趋势，不能当作医学剂量。
      </p>
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
      <p class="tool-hint">
        今天 {{ store.todaySmoked }} 支 ≈ 焦油 {{ formatMg(substance?.todayTarMg) }}、一氧化碳 {{ formatMg(substance?.todayCoMg) }}。
      </p>

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
        <label class="tool-field">
          <span>香烟种类</span>
          <select v-model="form.cigaretteType" @change="applyCigarettePreset">
            <option v-for="item in CIGARETTE_TYPES" :key="item.key" :value="item.key">
              {{ item.label }}<template v-if="item.tarMg !== null">（{{ item.tarMg }} / {{ item.coMg }} mg）</template>
            </option>
          </select>
        </label>
        <label class="tool-field"><span>每支焦油（mg）</span><input v-model.number="form.tarPerCigarette" type="number" min="0" max="100" step="0.1" /></label>
        <label class="tool-field"><span>每支一氧化碳（mg）</span><input v-model.number="form.coPerCigarette" type="number" min="0" max="100" step="0.1" /></label>
      </div>
      <div class="tool-row">
        <button type="button" class="tool-btn is-primary" :disabled="store.saving" @click="save">
          {{ store.saving ? '保存中…' : '保存设置' }}
        </button>
        <span class="tool-hint">单支价格约 {{ money(store.unitPrice) }} 元，每支焦油 {{ tarPerCigarette }} mg</span>
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
import {
  CIGARETTE_TYPES, CRAVING_TRIGGERS, cigaretteType, formatMg, formatRemaining, groupMilestones, timeline, triggerLabel
} from './quitSmoking.js'
import { useQuitSmokingStore } from '../../stores/quitSmoking.js'

const store = useQuitSmokingStore()
const cravingOpen = ref(false)
const submitting = ref(false)
const craving = reactive({ intensity: 3, trigger: 'after-meal', note: '', resisted: true })
const form = reactive({
  startAt: '',
  baselinePerDay: 20,
  pricePerPack: 25,
  cigarettesPerPack: 20,
  cigaretteType: 'regular',
  tarPerCigarette: 11,
  coPerCigarette: 12
})

const heat = computed(() => timeline(store.days, store.todayDate || undefined, 30))
const recovery = computed(() => store.recovery)
const substance = computed(() => store.substance)
const nextKey = computed(() => recovery.value?.nextMilestoneKey || '')
const latestReachedKey = computed(() => {
  const reached = store.milestones.filter(item => item.reached)
  return reached.length ? reached[reached.length - 1].key : ''
})
const milestoneGroups = computed(() => groupMilestones(store.milestones))
const tarPerCigarette = computed(() => Number(substance.value?.tarPerCigarette ?? form.tarPerCigarette ?? 0))
const coPerCigarette = computed(() => Number(substance.value?.coPerCigarette ?? form.coPerCigarette ?? 0))

watch(() => store.settings, settings => {
  if (!settings) return
  form.startAt = toLocalInput(settings.startAt)
  form.baselinePerDay = settings.baselinePerDay
  form.pricePerPack = Number(settings.pricePerPack ?? 0)
  form.cigarettesPerPack = settings.cigarettesPerPack
  form.cigaretteType = settings.cigaretteType || 'regular'
  form.tarPerCigarette = Number(settings.tarPerCigarette ?? cigaretteType(form.cigaretteType).tarMg ?? 0)
  form.coPerCigarette = Number(settings.coPerCigarette ?? cigaretteType(form.cigaretteType).coMg ?? 0)
}, { immediate: true })

onMounted(() => {
  if (!store.loaded && !store.loading) store.fetchAll()
})

/** 切换种类时把预设值填进输入框，用户仍可在此基础上按自己常抽的品牌微调。 */
function applyCigarettePreset() {
  const preset = cigaretteType(form.cigaretteType)
  if (preset.tarMg === null) return
  form.tarPerCigarette = preset.tarMg
  form.coPerCigarette = preset.coMg
}

function reachedIn(group) {
  return group.items.filter(item => item.reached).length
}

async function save() {
  try {
    await store.saveSettings({
      startAt: form.startAt ? new Date(form.startAt).toISOString() : undefined,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      baselinePerDay: Number(form.baselinePerDay),
      pricePerPack: Number(form.pricePerPack),
      cigarettesPerPack: Number(form.cigarettesPerPack),
      cigaretteType: form.cigaretteType,
      tarPerCigarette: Number(form.tarPerCigarette),
      coPerCigarette: Number(form.coPerCigarette)
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

.recovery-lead {
  display: grid; gap: 8px; padding: 12px 14px; border-radius: 12px;
  border: 1px solid var(--line); background: color-mix(in srgb, var(--accent) 6%, transparent);
}
.recovery-head { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 8px; }
.recovery-head span { font-size: 13px; color: var(--muted); }
.recovery-head b { font-size: 22px; font-variant-numeric: tabular-nums; }
.recovery-progress { height: 6px; border-radius: 999px; overflow: hidden; background: color-mix(in srgb, var(--ink) 10%, transparent); }
.recovery-progress span { display: block; height: 100%; border-radius: inherit; background: var(--accent); }
.recovery-note { margin: 0; font-size: 13px; line-height: 1.6; }

.milestone-group { margin-top: 14px; }
.milestone-group h3 { display: flex; gap: 6px; align-items: baseline; margin: 0 0 8px; font-size: 13px; }
.milestone-group h3 small { color: var(--muted); font-weight: 400; font-variant-numeric: tabular-nums; }
.milestone-list { display: grid; gap: 8px; margin: 0; padding: 0; list-style: none; }
.milestone-list li { display: grid; grid-template-columns: 10px minmax(0, 1fr); gap: 8px; align-items: start; }
.milestone-dot {
  width: 8px; height: 8px; margin-top: 6px; border-radius: 50%;
  border: 1.5px solid color-mix(in srgb, var(--ink) 30%, transparent);
}
.milestone-list li.is-reached .milestone-dot { background: #2f9e6f; border-color: #2f9e6f; }
.milestone-list li.is-next .milestone-dot {
  border-color: var(--accent); box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 18%, transparent);
}
.milestone-body { min-width: 0; }
.milestone-row { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.milestone-row b { font-size: 13px; font-weight: 600; }
.milestone-row em { font-style: normal; font-size: 12px; color: var(--muted); white-space: nowrap; font-variant-numeric: tabular-nums; }
.milestone-list li.is-reached .milestone-row b { color: var(--muted); }
.milestone-list li.is-next .milestone-row b { color: var(--ink); }
.milestone-body p { margin: 2px 0 0; font-size: 12px; line-height: 1.55; color: var(--muted); }
.milestone-list li.is-next .milestone-body p { color: var(--ink); }

@media (max-width: 560px) {
  .heat { grid-template-columns: repeat(6, minmax(0, 1fr)); }
  .milestone-row { flex-wrap: wrap; }
}
</style>
