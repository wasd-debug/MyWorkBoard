<template>
  <nav v-if="moduleItems.length" ref="navRef" class="bottom-nav" :aria-label="`${moduleLabel}移动端二级导航`">
    <div class="inner module-bottom-nav">
      <button
        v-for="item in visibleItems"
        :key="item.key"
        class="bottom-nav-item"
        :class="{ on: isActive(item) }"
        type="button"
        :aria-current="isActive(item) ? 'page' : undefined"
        @click="go(item)"
      ><component :is="iconFor(item)" aria-hidden="true" /><span>{{ item.label }}</span></button>
      <button
        v-if="hiddenItems.length"
        class="bottom-nav-item bottom-nav-more"
        :class="{ on: moreOpen || hiddenItems.some(item => isActive(item)) }"
        type="button"
        :aria-expanded="moreOpen"
        aria-controls="mobile-module-more"
        @click="moreOpen = !moreOpen"
      ><MoreFilled aria-hidden="true" /><span>更多</span></button>
    </div>

    <div class="bottom-nav-measure" aria-hidden="true">
      <span v-for="item in orderedItems" :key="item.key" :data-measure-key="item.key"><component :is="iconFor(item)" /><i>{{ item.label }}</i></span>
      <span data-measure-more><MoreFilled /><i>更多</i></span>
    </div>

    <Transition name="dock-sheet">
      <button v-if="moreOpen" class="mobile-more-backdrop" type="button" aria-label="关闭更多菜单" @click="moreOpen = false"></button>
    </Transition>
    <Transition name="dock-sheet">
      <section v-if="moreOpen" id="mobile-module-more" class="mobile-more-sheet" aria-label="二级菜单排序">
        <header class="mobile-more-head">
          <div><b>{{ moduleLabel }}菜单</b><small>上下移动可调整底栏与“更多”中的顺序</small></div>
          <button type="button" aria-label="关闭" @click="moreOpen = false"><Close /></button>
        </header>
        <ol class="mobile-module-order">
          <li v-for="(item, index) in orderedItems" :key="item.key" :class="{ active: isActive(item) }">
            <button class="mobile-order-link" type="button" @click="go(item)">
              <component :is="iconFor(item)" aria-hidden="true" /><span>{{ item.label }}</span><small>{{ visibleKeys.has(item.key) ? '底栏' : '更多' }}</small>
            </button>
            <div class="mobile-order-actions">
              <button type="button" :aria-label="`${item.label}上移`" :disabled="index === 0" @click="move(item.key, -1)"><SortUp /></button>
              <button type="button" :aria-label="`${item.label}下移`" :disabled="index === orderedItems.length - 1" @click="move(item.key, 1)"><SortDown /></button>
            </div>
          </li>
        </ol>
      </section>
    </Transition>
  </nav>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Calendar, DataAnalysis, Delete, Document, Grid, Management, MoreFilled, Setting, SortDown, SortUp, Tickets, Timer, User, Wallet, Close } from '../icons.js'
import { ledgerNavigation, navigationItemIsActive, worktimeNavigation } from '../config/moduleNavigation.js'

const route = useRoute()
const router = useRouter()
const navRef = ref(null)
const moreOpen = ref(false)
const visibleCount = ref(1)
let resizeObserver
const itemIcons = {
  punch: Timer, records: Calendar, stats: DataAnalysis,
  overview: Grid, transactions: Tickets, accounts: Wallet, reports: DataAnalysis,
  scheduled: Timer, manage: Management, members: User, recycle: Delete, audit: Document
}

const moduleKey = computed(() => route.path.startsWith('/ledger') ? 'ledger' : route.path === '/settings' ? 'settings' : 'worktime')
const moduleLabel = computed(() => moduleKey.value === 'ledger' ? '账本' : moduleKey.value === 'worktime' ? '工时' : '设置')
const moduleItems = computed(() => moduleKey.value === 'ledger' ? ledgerNavigation : moduleKey.value === 'worktime' ? worktimeNavigation : [])
const orderKeys = ref([])
const storageKey = computed(() => `workspace_mobile_${moduleKey.value}_nav_order_v1`)

function readOrder() {
  const validKeys = moduleItems.value.map(item => item.key)
  let stored = []
  try { stored = JSON.parse(localStorage.getItem(storageKey.value) || '[]') } catch { stored = [] }
  orderKeys.value = [...stored.filter(key => validKeys.includes(key)), ...validKeys.filter(key => !stored.includes(key))]
}

const orderedItems = computed(() => orderKeys.value.map(key => moduleItems.value.find(item => item.key === key)).filter(Boolean))
const activeItem = computed(() => orderedItems.value.find(item => isActive(item)))
const visibleItems = computed(() => {
  const items = orderedItems.value.slice(0, visibleCount.value)
  if (!activeItem.value || items.some(item => item.key === activeItem.value.key) || items.length >= orderedItems.value.length) return items
  return [...items.slice(0, Math.max(0, items.length - 1)), activeItem.value]
})
const visibleKeys = computed(() => new Set(visibleItems.value.map(item => item.key)))
const hiddenItems = computed(() => orderedItems.value.filter(item => !visibleKeys.value.has(item.key)))

function isActive(item) { return navigationItemIsActive(route, item) }
function iconFor(item) { return itemIcons[item.key] || Setting }
function go(item) { moreOpen.value = false; router.push(item.to) }
function saveOrder() { localStorage.setItem(storageKey.value, JSON.stringify(orderKeys.value)) }
function move(key, direction) {
  const index = orderKeys.value.indexOf(key)
  const target = index + direction
  if (index < 0 || target < 0 || target >= orderKeys.value.length) return
  const next = [...orderKeys.value]
  ;[next[index], next[target]] = [next[target], next[index]]
  orderKeys.value = next
  saveOrder()
  nextTick(measureCapacity)
}

function measureCapacity() {
  const root = navRef.value
  if (!root || !orderedItems.value.length) return
  const inner = root.querySelector('.module-bottom-nav')
  const measure = root.querySelector('.bottom-nav-measure')
  if (!inner || !measure) return
  const moreWidth = measure.querySelector('[data-measure-more]')?.getBoundingClientRect().width || 0
  const widths = new Map(orderedItems.value.map(item => [item.key, measure.querySelector(`[data-measure-key="${item.key}"]`)?.getBoundingClientRect().width || 64]))
  const gap = 4
  const available = inner.clientWidth
  let count = 1
  for (let candidate = 1; candidate <= orderedItems.value.length; candidate += 1) {
    const items = orderedItems.value.slice(0, candidate)
    if (activeItem.value && !items.some(item => item.key === activeItem.value.key) && candidate < orderedItems.value.length) {
      items.splice(candidate - 1, 1, activeItem.value)
    }
    const itemsWidth = items.reduce((total, item) => total + (widths.get(item.key) || 64), 0)
    const moreReserve = candidate < orderedItems.value.length ? moreWidth : 0
    const gapCount = items.length + (moreReserve ? 1 : 0)
    const required = itemsWidth + moreReserve + (Math.max(0, gapCount - 1) * gap)
    if (required > available) break
    count = candidate
  }
  visibleCount.value = Math.max(1, Math.min(count, orderedItems.value.length))
}

function onEscape(event) { if (event.key === 'Escape') moreOpen.value = false }
watch(moduleKey, async () => { moreOpen.value = false; readOrder(); await nextTick(); measureCapacity() }, { immediate: true })
watch(() => route.fullPath, () => { moreOpen.value = false })
onMounted(async () => {
  await nextTick()
  resizeObserver = new ResizeObserver(measureCapacity)
  if (navRef.value) resizeObserver.observe(navRef.value)
  window.addEventListener('keydown', onEscape)
  measureCapacity()
})
onBeforeUnmount(() => { resizeObserver?.disconnect(); window.removeEventListener('keydown', onEscape) })
</script>
