<template>
  <div>
    <section class="tool-card">
      <header><h2>粘贴分享文案</h2><small>整段文案粘进来即可，中文和 emoji 不影响识别</small></header>
      <label class="tool-field">
        <span>分享文案</span>
        <textarea v-model="source" rows="4" placeholder="例如：7.68 复制打开抖音，看看【某某】的作品… https://v.douyin.com/abcd123/"></textarea>
      </label>
      <div class="tool-row">
        <button type="button" class="tool-btn" @click="reset">清空</button>
        <span class="tool-hint">点一下输入框，按 Ctrl/⌘ + V 粘贴整段分享文案。</span>
      </div>
      <p class="tool-hint">提取在你的浏览器里完成，不会把文案发送到服务端；只有点「展开真实地址」才会请求一次。</p>
    </section>

    <section v-if="url" class="tool-card">
      <header>
        <h2>提取结果</h2>
        <small>{{ shortLink ? '短链，可展开真实地址' : '已含视频 ID' }}</small>
      </header>
      <div class="tool-stats">
        <div class="tool-stat is-accent"><b>{{ shownVideoId || '未识别' }}</b><span>视频 ID</span></div>
        <div class="tool-stat"><b>{{ host }}</b><span>域名</span></div>
      </div>

      <div class="copy-row">
        <code>{{ url }}</code>
        <button type="button" class="tool-btn" @click="copy(url, '链接')">复制</button>
      </div>

      <div class="tool-row">
        <button type="button" class="tool-btn is-primary" :disabled="loading" @click="expand">
          {{ loading ? '解析中…' : '展开真实地址' }}
        </button>
        <span class="tool-hint">只跟随跳转地址，不下载视频、不读取页面内容。</span>
      </div>

      <p v-if="error" class="tool-error" role="alert">{{ error }}</p>

      <template v-if="result">
        <div class="copy-row">
          <code>{{ result.resolvedUrl }}</code>
          <button type="button" class="tool-btn" @click="copy(result.resolvedUrl, '真实地址')">复制</button>
        </div>
        <div v-if="result.videoId" class="copy-row">
          <code>{{ result.videoId }}</code>
          <button type="button" class="tool-btn" @click="copy(result.videoId, '视频 ID')">复制</button>
        </div>
        <ol class="hops">
          <li v-for="(hop, index) in result.hops" :key="hop">{{ index + 1 }}. {{ hop }}</li>
        </ol>
        <p v-if="!result.resolved" class="tool-hint">{{ result.reason || '未能拿到视频 ID' }}</p>
      </template>
    </section>

    <section v-else class="tool-card">
      <p class="tool-hint">还没有识别到抖音链接。支持 v.douyin.com 短链，以及带 /video/ 或 modal_id 的长链。</p>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { apiResolveDouyinLink } from '../../../packages/api-client/src/index.js'
import { copyText } from '../../utils/clipboard.js'
import { message } from '../../services/message.js'
import { extractDouyinUrl, extractVideoId, isShortLink, shortDisplay } from './douyinLink.js'

const source = ref('')
const result = ref(null)
const loading = ref(false)
const error = ref('')

const url = computed(() => extractDouyinUrl(source.value))
const videoId = computed(() => (url.value ? extractVideoId(url.value) : ''))
// 短链本身没有视频 ID，展开后服务端解析出来的那个才是最终结果
const shownVideoId = computed(() => result.value?.videoId || videoId.value)
const shortLink = computed(() => (url.value ? isShortLink(url.value) : false))
const host = computed(() => {
  if (!url.value) return ''
  try {
    return new URL(url.value).host
  } catch {
    return ''
  }
})

watch(url, () => {
  result.value = null
  error.value = ''
})

function reset() {
  source.value = ''
  result.value = null
  error.value = ''
}

async function expand() {
  if (!url.value || loading.value) return
  loading.value = true
  error.value = ''
  try {
    result.value = await apiResolveDouyinLink(url.value)
  } catch (failure) {
    const code = failure?.problem?.code
    const detail = failure?.problem?.detail
    if (code === 'UPSTREAM_UNAVAILABLE') error.value = '抖音暂时不可达，请稍后重试；已提取的链接仍可复制使用。'
    else if (code === 'NOT_FOUND') error.value = '分享链接已失效。'
    else if (code === 'RATE_LIMITED') error.value = '操作过于频繁，请稍后再试。'
    // INVALID_REQUEST 的具体原因（域名不支持 / 未识别到视频 ID）由服务端给出，优先展示
    else error.value = detail || failure?.message || '解析失败'
  } finally {
    loading.value = false
  }
}

async function copy(text, label) {
  try {
    await copyText(text)
    message.success(`已复制${label}`)
  } catch {
    message.error('复制失败，请手动选中复制')
  }
}
</script>

<style scoped>
.copy-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.copy-row code {
  min-width: 0; flex: 1 1 220px; overflow-wrap: anywhere;
  padding: 8px 10px; border: 1px solid var(--line); border-radius: 9px;
  background: var(--paper); color: var(--ink); font-size: 12px;
}
.hops { margin: 0; padding-left: 18px; color: var(--muted); font-size: 11.5px; line-height: 1.7; }
.hops li { overflow-wrap: anywhere; }
</style>
