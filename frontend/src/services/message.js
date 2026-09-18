import { reactive } from 'vue'

export const messages = reactive([])
const timers = new Map()
let sequence = 0

function normalize(input) {
  if (input && typeof input === 'object') {
    return {
      text: String(input.message ?? ''),
      duration: input.duration === undefined ? 3200 : Number(input.duration)
    }
  }
  return { text: String(input ?? ''), duration: 3200 }
}

function enqueue(type, input) {
  const { text, duration } = normalize(input)
  const id = ++sequence
  messages.push({ id, type, text })
  if (duration > 0) {
    const timer = setTimeout(() => dismissMessage(id), duration)
    timer.unref?.()
    timers.set(id, timer)
  }
  return id
}

export function dismissMessage(id) {
  const timer = timers.get(id)
  if (timer) clearTimeout(timer)
  timers.delete(id)
  const index = messages.findIndex(item => item.id === id)
  if (index >= 0) messages.splice(index, 1)
}

export function resetMessages() {
  for (const timer of timers.values()) clearTimeout(timer)
  timers.clear()
  messages.splice(0)
}

export const message = {
  success: input => enqueue('success', input),
  warning: input => enqueue('warning', input),
  error: input => enqueue('error', input),
  info: input => enqueue('info', input)
}
