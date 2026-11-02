// 完成提示音：用 WebAudio 合成，避免额外音频资源
let context = null

function audioContext() {
  if (typeof window === 'undefined') return null
  const Ctor = window.AudioContext || window.webkitAudioContext
  if (!Ctor) return null
  if (!context) context = new Ctor()
  return context
}

/** kind: WATER（水滴）/ CLICK（清脆）/ NONE（静音） */
export function playCompleteSound(kind = 'WATER') {
  if (kind === 'NONE') return
  const ctx = audioContext()
  if (!ctx) return
  if (ctx.state === 'suspended') ctx.resume().catch(() => {})

  const now = ctx.currentTime
  const gain = ctx.createGain()
  gain.connect(ctx.destination)

  if (kind === 'CLICK') {
    const osc = ctx.createOscillator()
    osc.type = 'triangle'
    osc.frequency.setValueAtTime(1320, now)
    osc.frequency.exponentialRampToValueAtTime(880, now + 0.06)
    gain.gain.setValueAtTime(0.0001, now)
    gain.gain.exponentialRampToValueAtTime(0.12, now + 0.008)
    gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.09)
    osc.connect(gain)
    osc.start(now)
    osc.stop(now + 0.1)
    return
  }

  // 水滴：先上扬再滑落的正弦，带一点余韵
  const osc = ctx.createOscillator()
  osc.type = 'sine'
  osc.frequency.setValueAtTime(520, now)
  osc.frequency.exponentialRampToValueAtTime(1180, now + 0.05)
  osc.frequency.exponentialRampToValueAtTime(760, now + 0.22)
  gain.gain.setValueAtTime(0.0001, now)
  gain.gain.exponentialRampToValueAtTime(0.16, now + 0.012)
  gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.26)
  osc.connect(gain)
  osc.start(now)
  osc.stop(now + 0.28)
}
