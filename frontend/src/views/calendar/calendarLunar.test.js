import test from 'node:test'
import assert from 'node:assert/strict'
import { formatDaySubLabel, formatLunarLabel } from './calendarLunar.js'

test('初一显示农历月份而不是「初一」', () => {
  assert.equal(formatLunarLabel({ lunarDate: '九月初一' }), '九月')
  assert.equal(formatLunarLabel({ lunarDate: '正月初一' }), '正月')
  assert.equal(formatLunarLabel({ lunarDate: '闰四月初一' }), '闰四月')
})

test('非初一只显示农历日', () => {
  assert.equal(formatLunarLabel({ lunarDate: '九月十六' }), '十六')
  assert.equal(formatLunarLabel({ lunarDate: '八月廿一' }), '廿一')
  assert.equal(formatLunarLabel({ lunarDate: '冬月三十' }), '三十')
})

test('节日与节气优先于农历日', () => {
  assert.equal(formatLunarLabel({ lunarDate: '九月初一', festival: '国庆节' }), '国庆节')
  assert.equal(formatLunarLabel({ lunarDate: '九月十六', solarTerm: '寒露' }), '寒露')
})

test('缺少农历字段时返回空串而不是 undefined', () => {
  assert.equal(formatLunarLabel(null), '')
  assert.equal(formatLunarLabel({}), '')
  assert.equal(formatLunarLabel({ lunarDate: '九月' }), '九月')
})

test('没有月份前缀的标签原样返回', () => {
  assert.equal(formatLunarLabel({ lunarDate: '十六' }), '十六')
  assert.equal(formatLunarLabel({ lunarDate: '万圣节' }), '万圣节')
})

test('节假日名称优先于农历副标', () => {
  assert.equal(formatDaySubLabel({ holiday: { name: '国庆节' }, lunar: { lunarDate: '九月初一' } }), '国庆节')
  assert.equal(formatDaySubLabel({ lunar: { lunarDate: '九月十六' } }), '十六')
  assert.equal(formatDaySubLabel({}), '')
})

test('超过三个字的节日名退回农历日，避免格子里被截断', () => {
  assert.equal(formatLunarLabel({ lunarDate: '九月廿一', festival: '万圣节前夜' }), '廿一')
  assert.equal(formatLunarLabel({ lunarDate: '九月初一', festival: '国庆节' }), '国庆节')
})
