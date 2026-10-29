/**
 * 专注页的声音设置与合成。
 *
 * 提示音和白噪音全部用 Web Audio 现场合成，不引入任何音频文件：
 * 离线可用、零体积，也避免版权素材。
 */

const STORAGE_KEY = 'task_focus_sound_v1'

export const CHIME_OPTIONS = [
  { id: 'bell', label: '清脆铃声' },
  { id: 'wood', label: '木质敲击' },
  { id: 'chord', label: '柔和和弦' },
  { id: 'none', label: '不提示' }
]

export const NOISE_OPTIONS = [
  { id: 'none', label: '关闭' },
  { id: 'white', label: '白噪音' },
  { id: 'rain', label: '雨声' },
  { id: 'wave', label: '海浪' }
]

export const DEFAULT_SOUND = { enabled: true, volume: 60, chime: 'bell', noise: 'none' }

export function readFocusSound() {
  if (typeof localStorage === 'undefined') return { ...DEFAULT_SOUND }
  try {
    const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') || {}
    const volume = Number(raw.volume)
    return {
      enabled: raw.enabled !== false,
      volume: Number.isFinite(volume) ? Math.min(100, Math.max(0, Math.round(volume))) : DEFAULT_SOUND.volume,
      chime: CHIME_OPTIONS.some(item => item.id === raw.chime) ? raw.chime : DEFAULT_SOUND.chime,
      noise: NOISE_OPTIONS.some(item => item.id === raw.noise) ? raw.noise : DEFAULT_SOUND.noise
    }
  } catch {
    return { ...DEFAULT_SOUND }
  }
}

export function writeFocusSound(value) {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(value))
  } catch {
    /* 隐私模式下写入失败，忽略即可：本次会话仍然生效 */
  }
}

let context = null
let playing = null

function ensureContext() {
  if (typeof window === 'undefined') return null
  const Ctor = window.AudioContext || window.webkitAudioContext
  if (!Ctor) return null
  if (!context) context = new Ctor()
  // 浏览器要求用户手势后才能出声；挂起时主动恢复
  if (context.state === 'suspended') void context.resume()
  return context
}

/** 音量 0~100 映射到增益：人耳感知接近对数，用指数曲线更自然 */
function gainFor(volume) {
  const ratio = Math.min(100, Math.max(0, Number(volume) || 0)) / 100
  return Math.pow(ratio, 1.7) * 0.6
}

const CHIME_NOTES = {
  // [频率, 延迟秒, 衰减时长]
  bell: [[880, 0, 1.5], [1320, 0.02, 1.1], [1760, 0.05, 0.8]],
  wood: [[392, 0, 0.16], [784, 0, 0.08]],
  chord: [[523.25, 0, 1.7], [659.25, 0.04, 1.5], [783.99, 0.08, 1.35]]
}

export function playFocusChime(chime, volume = DEFAULT_SOUND.volume) {
  if (!chime || chime === 'none') return
  const notes = CHIME_NOTES[chime]
  const ctx = ensureContext()
  if (!ctx || !notes) return
  const now = ctx.currentTime
  const master = ctx.createGain()
  master.gain.value = gainFor(volume)
  master.connect(ctx.destination)
  for (const [frequency, delay, duration] of notes) {
    const osc = ctx.createOscillator()
    const envelope = ctx.createGain()
    osc.type = chime === 'wood' ? 'triangle' : 'sine'
    osc.frequency.value = frequency
    envelope.gain.setValueAtTime(0, now + delay)
    envelope.gain.linearRampToValueAtTime(0.9, now + delay + 0.012)
    envelope.gain.exponentialRampToValueAtTime(0.0008, now + delay + duration)
    osc.connect(envelope).connect(master)
    osc.start(now + delay)
    osc.stop(now + delay + duration + 0.06)
  }
  window.setTimeout(() => {
    try { master.disconnect() } catch { /* 已断开 */ }
  }, 2800)
}

function noiseBuffer(ctx, kind) {
  const length = Math.floor(ctx.sampleRate * 3)
  const buffer = ctx.createBuffer(1, length, ctx.sampleRate)
  const data = buffer.getChannelData(0)
  if (kind === 'rain') {
    // 棕噪音（白噪音积分）：低频更重，接近雨声而不是"沙沙"的白噪音
    let last = 0
    for (let index = 0; index < length; index += 1) {
      last = (last + 0.02 * (Math.random() * 2 - 1)) / 1.02
      data[index] = last * 3.2
    }
    return buffer
  }
  for (let index = 0; index < length; index += 1) data[index] = Math.random() * 2 - 1
  return buffer
}

export function startFocusNoise(noise, volume = DEFAULT_SOUND.volume) {
  if (!noise || noise === 'none') {
    stopFocusNoise()
    return
  }
  const ctx = ensureContext()
  if (!ctx) return
  stopFocusNoise()

  const source = ctx.createBufferSource()
  source.buffer = noiseBuffer(ctx, noise)
  source.loop = true

  const filter = ctx.createBiquadFilter()
  // 三种噪音靠滤波器拉开音色差别，而不是只换名字
  if (noise === 'white') {
    filter.type = 'highpass'
    filter.frequency.value = 320
  } else if (noise === 'rain') {
    filter.type = 'bandpass'
    filter.frequency.value = 1600
    filter.Q.value = 0.6
  } else {
    filter.type = 'lowpass'
    filter.frequency.value = 620
    filter.Q.value = 0.9
  }

  const factor = noise === 'white' ? 0.34 : 0.8
  const gain = ctx.createGain()
  gain.gain.setValueAtTime(0, ctx.currentTime)
  gain.gain.linearRampToValueAtTime(gainFor(volume) * factor, ctx.currentTime + 0.6)

  source.connect(filter).connect(gain).connect(ctx.destination)
  source.start()

  let lfo = null
  if (noise === 'wave') {
    // 让低通频率缓慢起伏，听起来像潮水一涨一落
    lfo = ctx.createOscillator()
    const depth = ctx.createGain()
    lfo.frequency.value = 0.08
    depth.gain.value = 380
    lfo.connect(depth).connect(filter.frequency)
    lfo.start()
  }
  playing = { source, gain, lfo, factor }
}

export function setFocusNoiseVolume(volume) {
  if (!playing || !context) return
  playing.gain.gain.setTargetAtTime(gainFor(volume) * playing.factor, context.currentTime, 0.08)
}

export function stopFocusNoise() {
  if (!playing) return
  const { source, gain, lfo } = playing
  playing = null
  if (!context) return
  const at = context.currentTime + 0.35
  try {
    gain.gain.cancelScheduledValues(context.currentTime)
    gain.gain.setTargetAtTime(0, context.currentTime, 0.1)
    source.stop(at)
    lfo?.stop(at)
  } catch {
    /* 已经停止过 */
  }
}

export function isFocusNoisePlaying() {
  return Boolean(playing)
}
