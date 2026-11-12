import assert from 'node:assert/strict'
import { test } from 'node:test'
import { extractDouyinUrl, extractVideoId, isShortLink, isSupportedDouyinUrl, shortDisplay } from './douyinLink.js'

test('从完整分享文案里提取短链', () => {
  const text = '7.68 复制打开抖音，看看【某某】的作品，生活记录 # 日常 https://v.douyin.com/abcd123/ 复制此链接'
  assert.equal(extractDouyinUrl(text), 'https://v.douyin.com/abcd123/')
})

test('链接后紧跟中文标点时截断干净', () => {
  assert.equal(extractDouyinUrl('看这个 https://www.douyin.com/video/7400，很好看'), 'https://www.douyin.com/video/7400')
  assert.equal(extractDouyinUrl('（https://v.douyin.com/xyz789/）'), 'https://v.douyin.com/xyz789/')
})

test('多条链接取第一条受支持的那个', () => {
  const text = 'https://example.com/abc https://v.douyin.com/first1/ https://v.douyin.com/second2/'
  assert.equal(extractDouyinUrl(text), 'https://v.douyin.com/first1/')
})

test('没有抖音链接时返回空串', () => {
  assert.equal(extractDouyinUrl(''), '')
  assert.equal(extractDouyinUrl('只有文字没有链接'), '')
  assert.equal(extractDouyinUrl('https://example.com/video/1'), '')
})

test('域名白名单覆盖长短链与 iesdouyin', () => {
  assert.equal(isSupportedDouyinUrl('https://v.douyin.com/abcd/'), true)
  assert.equal(isSupportedDouyinUrl('https://www.douyin.com/video/1'), true)
  assert.equal(isSupportedDouyinUrl('https://www.iesdouyin.com/share/video/1'), true)
  assert.equal(isSupportedDouyinUrl('https://douyin.com.evil.com/video/1'), false)
  assert.equal(isSupportedDouyinUrl('不是链接'), false)
})

test('视频 ID 支持三种形态', () => {
  assert.equal(extractVideoId('https://www.douyin.com/video/7400000000000000001'), '7400000000000000001')
  assert.equal(extractVideoId('https://www.douyin.com/share/video/7400?region=CN'), '7400')
  assert.equal(extractVideoId('https://www.douyin.com/discover?modal_id=7400'), '7400')
  assert.equal(extractVideoId('https://v.douyin.com/abcd123/'), '')
})

test('短链识别与展示用域名', () => {
  assert.equal(isShortLink('https://v.douyin.com/abcd123/'), true)
  assert.equal(isShortLink('https://www.douyin.com/video/7400'), false)
  assert.equal(shortDisplay('https://www.douyin.com/video/7400?region=CN&share=1'), 'www.douyin.com/video/7400')
})
