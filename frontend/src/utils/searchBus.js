// 搜索相关浮层的全局开关：图标栏 / 快捷键 / 任务页表头共用
import { ref } from 'vue'

export const searchDialogOpen = ref(false)
/** 打开时预填的关键词/页签，例如点「标签」页签再唤起 */
export const searchDialogSeed = ref(null)

export function openTaskSearch(seed = null) {
  searchDialogSeed.value = seed && typeof seed === 'object' ? seed : null
  searchDialogOpen.value = true
}

export function closeTaskSearch() {
  searchDialogOpen.value = false
}
