/**
 * 抖音分享文案里的链接提取。纯前端完成，不发起任何请求；
 * 只有用户点「展开真实地址」时才会调用后端解析接口。
 */

export const DOUYIN_HOSTS = ['douyin.com', 'www.douyin.com', 'v.douyin.com', 'iesdouyin.com', 'www.iesdouyin.com']

// 分享文案里链接后面常紧跟中文标点或 emoji，取到这些字符就截断。
const URL_PATTERN = /https?:\/\/[^\s"'`<>，。；、！？：（）【】《》“”‘’…]+/gi
const TRAILING_NOISE = /[),.;:!?，。；、！？：）】》”’"'`]+$/g

function normalizeHost(host) {
  return String(host || '').toLowerCase().replace(/\.+$/, '')
}

export function isSupportedDouyinUrl(url) {
  try {
    const parsed = new URL(url)
    return DOUYIN_HOSTS.includes(normalizeHost(parsed.hostname))
  } catch {
    return false
  }
}

/** 从任意文本中提取第一个受支持的抖音链接；找不到返回空串。 */
export function extractDouyinUrl(text) {
  const source = String(text ?? '')
  const matches = source.match(URL_PATTERN)
  if (!matches) return ''
  for (const raw of matches) {
    const candidate = raw.replace(TRAILING_NOISE, '')
    if (isSupportedDouyinUrl(candidate)) return candidate
  }
  return ''
}

/** 从链接中取视频 ID：/video/123、/share/video/123 或 query 里的 modal_id / aweme_id。 */
export function extractVideoId(url) {
  try {
    const parsed = new URL(url)
    const byPath = /\/(?:share\/)?video\/(\d+)/.exec(parsed.pathname)
    if (byPath) return byPath[1]
    return parsed.searchParams.get('modal_id') || parsed.searchParams.get('aweme_id') || ''
  } catch {
    return ''
  }
}

export function isShortLink(url) {
  try {
    return normalizeHost(new URL(url).hostname) === 'v.douyin.com'
  } catch {
    return false
  }
}

/** 展示用的「域名 + 路径」，避免把长 query 铺在界面上。 */
export function shortDisplay(url) {
  try {
    const parsed = new URL(url)
    return `${parsed.host}${parsed.pathname}`
  } catch {
    return url
  }
}
