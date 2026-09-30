const listeners = new WeakMap()

export function resizeAutoGrowTextarea(element, maxHeight = 160) {
  if (!element) return
  const limit = Math.max(48, Number(maxHeight) || 160)
  element.style.height = 'auto'
  const contentHeight = element.scrollHeight
  element.style.height = `${Math.min(contentHeight, limit)}px`
  element.style.overflowY = contentHeight > limit ? 'auto' : 'hidden'
}

export const autoGrowTextarea = {
  mounted(element, binding) {
    const resize = () => resizeAutoGrowTextarea(element, binding.value)
    listeners.set(element, resize)
    element.addEventListener('input', resize)
    resize()
  },
  updated(element, binding) {
    resizeAutoGrowTextarea(element, binding.value)
  },
  beforeUnmount(element) {
    const resize = listeners.get(element)
    if (resize) element.removeEventListener('input', resize)
    listeners.delete(element)
  }
}
