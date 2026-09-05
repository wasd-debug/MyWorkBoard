<template>
  <nav class="bottom-nav" aria-label="移动端主导航">
    <div class="inner">
      <router-link v-for="(item, index) in items" :key="item.to" :to="item.to" active-class="on">
        <span class="nav-index">[{{ String(index + 1).padStart(2, '0') }}]</span>
        <span>{{ item.label }}</span>
      </router-link>
    </div>
  </nav>
</template>

<script setup>
const items = [
  { to: '/punch', label: '打卡' },
  { to: '/records', label: '记录' },
  { to: '/stats', label: '统计' },
  { to: '/settings', label: '设置' }
]
</script>
