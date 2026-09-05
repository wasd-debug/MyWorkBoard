<template>
  <div class="ui-empty" role="status">
    <span class="ui-empty-icon">◌</span>
    <p>{{ description }}</p>
  </div>
</template>

<script setup>
defineProps({ description: { type: String, default: '暂无数据' } })
</script>
