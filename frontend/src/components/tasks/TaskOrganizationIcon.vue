<script setup>
import { computed } from "vue";
import { resolveOrganizationIcon } from "../../views/taskOrganizationIcons.js";

const props = defineProps({
  value: { type: String, default: "" },
  size: { type: Number, default: 15 },
});

defineOptions({ inheritAttrs: false });

const iconComponent = computed(() => resolveOrganizationIcon(props.value));
</script>

<template>
  <component v-if="iconComponent" :is="iconComponent" v-bind="$attrs" :size="size" :stroke-width="2" aria-hidden="true" />
  <span v-else v-bind="$attrs">{{ value }}</span>
</template>
