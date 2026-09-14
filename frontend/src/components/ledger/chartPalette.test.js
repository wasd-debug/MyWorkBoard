import test from 'node:test'
import assert from 'node:assert/strict'
import { categoryColor, stableResourceColor } from './chartPalette.js'

test('二级分类沿用同一父分类色系并形成不同明度', () => {
  const first = categoryColor({ name: '早餐', parentId: 'food' }, 0)
  const second = categoryColor({ name: '晚餐', parentId: 'food' }, 1)
  assert.match(first, /^hsl\(\d+ /)
  assert.equal(first.match(/^hsl\((\d+)/)?.[1], second.match(/^hsl\((\d+)/)?.[1])
  assert.notEqual(first, second)
})

test('实体颜色在同一键下保持稳定', () => {
  assert.equal(stableResourceColor('merchant-1'), stableResourceColor('merchant-1'))
  assert.notEqual(stableResourceColor('merchant-1'), stableResourceColor('merchant-2'))
})

test('历史默认分类色会被语义色板替换', () => {
  assert.notEqual(categoryColor({ name: '餐饮', color: '#0f5132' }, 0), '#0f5132')
})

test('同一统计中的一级分类按序号获得不同色系', () => {
  const first = categoryColor({ name: '食品酒水' }, 0)
  const second = categoryColor({ name: '工资收入' }, 1)
  assert.notEqual(first.match(/^hsl\((\d+)/)?.[1], second.match(/^hsl\((\d+)/)?.[1])
})

test('历史二级分类可继承父分类补算出的色相', () => {
  const parent = categoryColor({ name: '食品酒水', paletteIndex: 3 }, 0)
  const child = categoryColor({ name: '早餐', parentId: 'food', parentColor: parent }, 0)
  assert.equal(parent.match(/^hsl\((\d+)/)?.[1], child.match(/^hsl\((\d+)/)?.[1])
})
