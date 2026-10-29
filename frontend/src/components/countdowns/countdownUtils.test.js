import assert from 'node:assert/strict'
import test from 'node:test'
import {
  coverBackground,
  cycleUnit,
  daysUntil,
  defaultExtras,
  formatCount,
  matchesTab,
  relativeText,
  resolveTargetDate,
  sortItems,
  targetText,
  typeOf
} from './countdownUtils.js'

test('倒数纪念日：非重复条目原样返回目标日期', () => {
  const item = { targetDate: '2027-01-01', repeatYearly: false }
  assert.equal(resolveTargetDate(item, '2026-10-29'), '2027-01-01')
})

test('倒数纪念日：每年重复且今年未到，滚到今年', () => {
  const item = { targetDate: '2020-12-25', repeatYearly: true }
  assert.equal(resolveTargetDate(item, '2026-10-29'), '2026-12-25')
})

test('倒数纪念日：每年重复且今年已过，滚到明年', () => {
  const item = { targetDate: '2020-03-08', repeatYearly: true }
  assert.equal(resolveTargetDate(item, '2026-10-29'), '2027-03-08')
})

test('倒数纪念日：生日类型默认按年重复', () => {
  const item = { targetDate: '1996-02-14', type: 'BIRTHDAY' }
  assert.equal(resolveTargetDate(item, '2026-10-29'), '2027-02-14')
})

test('倒数纪念日：天数差支持今天/未来/过去', () => {
  assert.equal(daysUntil({ targetDate: '2026-10-29' }, '2026-10-29'), 0)
  assert.equal(daysUntil({ targetDate: '2026-11-03' }, '2026-10-29'), 5)
  assert.equal(daysUntil({ targetDate: '2026-10-24' }, '2026-10-29'), -5)
})

test('倒数纪念日：天单位只输出一个数字段', () => {
  assert.deepEqual(formatCount({ targetDate: '2026-11-03' }, 'DAY', '2026-10-29'), [{ value: '5', unit: '' }])
})

test('倒数纪念日：周单位拆成 周+天', () => {
  assert.deepEqual(formatCount({ targetDate: '2026-11-08' }, 'WEEK', '2026-10-29'), [
    { value: '1', unit: '周' },
    { value: '3', unit: '天' }
  ])
})

test('倒数纪念日：月单位按 30 天折算', () => {
  assert.deepEqual(formatCount({ targetDate: '2026-12-31' }, 'MONTH', '2026-10-29'), [
    { value: '2', unit: '月' },
    { value: '3', unit: '天' }
  ])
})

test('倒数纪念日：过去日期用绝对值，方向交给文案', () => {
  assert.equal(relativeText({ targetDate: '2026-10-24' }, '2026-10-29'), '已经')
  assert.equal(relativeText({ targetDate: '2026-11-03' }, '2026-10-29'), '还有')
  assert.equal(relativeText({ targetDate: '2026-10-29' }, '2026-10-29'), '就是今天')
  assert.equal(formatCount({ targetDate: '2026-10-24' }, 'DAY', '2026-10-29')[0].value, '5')
})

test('倒数纪念日：目标日期按中文日期展示', () => {
  assert.equal(targetText({ targetDate: '2027-01-01' }, '2026-10-29'), '2027年1月1日')
})

test('倒数纪念日：单位按 天→周→月→时分秒→分秒 循环', () => {
  assert.equal(cycleUnit('DAY'), 'WEEK')
  assert.equal(cycleUnit('MINUTE'), 'DAY')
})

test('倒数纪念日：排序置顶优先，其余按远近', () => {
  const items = [
    { publicId: 'a', title: 'A', targetDate: '2026-11-20' },
    { publicId: 'b', title: 'B', targetDate: '2026-10-30', pinned: true },
    { publicId: 'c', title: 'C', targetDate: '2026-11-01' }
  ]
  assert.deepEqual(sortItems(items, '2026-10-29').map(item => item.publicId), ['b', 'c', 'a'])
})

test('倒数纪念日：分类 tab 过滤按类型', () => {
  const birthday = { type: 'BIRTHDAY' }
  assert.equal(matchesTab(birthday, 'ALL'), true)
  assert.equal(matchesTab(birthday, 'BIRTHDAY'), true)
  assert.equal(matchesTab(birthday, 'COUNTDOWN'), false)
})

test('倒数纪念日：缺 type 时回落到后端 kind', () => {
  assert.equal(typeOf({ kind: 'ANNIVERSARY' }).key, 'ANNIVERSARY')
  assert.equal(typeOf({}).key, 'COUNTDOWN')
})

test('倒数纪念日：封面背景随样式变化且带颜色', () => {
  assert.match(coverBackground('SOLID', '#4d78db'), /#4d78db/)
  assert.match(coverBackground('STRIPE', '#e2685f'), /repeating-linear-gradient/)
  assert.notEqual(coverBackground('BOKEH', '#4d78db'), coverBackground('SOLID', '#4d78db'))
})

test('倒数纪念日：新建默认扩展字段跟随类型图标', () => {
  assert.equal(defaultExtras('BIRTHDAY').icon, '🎂')
  assert.equal(defaultExtras('FESTIVAL').icon, '🎊')
  assert.equal(defaultExtras().unit, 'DAY')
  assert.equal(defaultExtras().archived, false)
})
