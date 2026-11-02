import assert from 'node:assert/strict'
import test from 'node:test'
import {
  cardTheme,
  countParts,
  cycleUnit,
  daysUntil,
  defaultCountdown,
  directionText,
  effectiveTarget,
  footerText,
  matchesTab,
  reminderText,
  repeatText,
  sortItems,
  typeOf
} from './countdownUtils.js'
import {
  builtinTargetDate,
  decodeLunar,
  encodeLunar,
  lunarDayOptions,
  lunarMonthOptions,
  lunarText,
  lunarToSolar,
  nextHoliday,
  nextLunarOccurrence,
  solarToLunar,
  yearLabel
} from './lunar.js'

test('农历：2026-11-02 是九月廿四', () => {
  assert.deepEqual(solarToLunar('2026-11-02'), { year: 2026, month: 9, day: 24, leap: false })
  assert.equal(lunarText('2026-11-02'), '九月廿四')
})

test('农历：春节按官方公布日期换算', () => {
  assert.equal(solarToLunar('2026-02-17').month, 1)
  assert.equal(solarToLunar('2026-02-17').day, 1)
  assert.equal(solarToLunar('2027-02-06').day, 1)
  assert.equal(solarToLunar('2028-01-26').day, 1)
})

test('农历：闰月能被识别并反算', () => {
  const leap = lunarToSolar(2025, 6, 8, true)
  assert.equal(leap.getFullYear(), 2025)
  assert.equal(solarToLunar(leap).leap, true)
  assert.equal(solarToLunar(leap).day, 8)
})

test('农历：选择器选项覆盖闰月与大小月', () => {
  assert.equal(lunarMonthOptions(2025).some(item => item.leap), true)
  assert.equal(lunarMonthOptions(2026).length, 12)
  assert.equal(lunarDayOptions(2026, 9, false).length, 30)
})

test('农历：干支年纪年正确', () => {
  assert.equal(yearLabel(2026), '丙午（2026）')
})

test('农历：月日编码可往返', () => {
  assert.equal(encodeLunar(6, 8, true), '6L-8')
  assert.deepEqual(decodeLunar('6L-8'), { month: 6, leap: true, day: 8 })
  assert.equal(decodeLunar('bad'), null)
})

test('农历：下一次农历月日落在今年或明年', () => {
  const next = nextLunarOccurrence('9-24', new Date(2026, 10, 2))
  assert.equal(next.getFullYear(), 2026)
  assert.equal(next.getMonth(), 10)
  assert.equal(next.getDate(), 2)
})

test('节假日：今天之后第一个法定节假日是元旦', () => {
  const holiday = nextHoliday(new Date(2026, 10, 2))
  assert.equal(holiday.name, '元旦')
  assert.equal(holiday.date.getFullYear(), 2027)
  assert.equal(holiday.date.getMonth(), 0)
  assert.equal(holiday.date.getDate(), 1)
})

test('内置倒数日：周末/本月结束/本年结束按当天推算', () => {
  const today = new Date(2026, 10, 2)
  assert.equal(builtinTargetDate('weekend', today), '2026-11-07')
  assert.equal(builtinTargetDate('month-end', today), '2026-11-30')
  assert.equal(builtinTargetDate('year-end', today), '2026-12-31')
})

test('目标日期：非重复条目原样返回', () => {
  assert.equal(effectiveTarget({ targetDate: '2027-01-01' }, '2026-11-02'), '2027-01-01')
})

test('目标日期：每年重复滚到今年未到的日子', () => {
  assert.equal(effectiveTarget({ targetDate: '2020-12-25', repeatRule: 'YEARLY' }, '2026-11-02'), '2026-12-25')
  assert.equal(effectiveTarget({ targetDate: '2020-03-08', repeatRule: 'YEARLY' }, '2026-11-02'), '2027-03-08')
})

test('目标日期：生日默认按年重复', () => {
  assert.equal(effectiveTarget({ targetDate: '1996-02-14', kind: 'BIRTHDAY' }, '2026-11-02'), '2027-02-14')
})

test('目标日期：农历生日按农历月日换算', () => {
  const target = effectiveTarget({ calendarType: 'LUNAR', lunarDate: '9-24', repeatRule: 'YEARLY', targetDate: '2026-11-02' }, '2026-11-02')
  assert.equal(target, '2026-11-02')
})

test('目标日期：内置倒数日覆盖存储的目标日期', () => {
  assert.equal(effectiveTarget({ builtinKey: 'weekend', targetDate: '2020-01-01' }, '2026-11-02'), '2026-11-07')
})

test('天数差：支持今天/未来/过去', () => {
  assert.equal(daysUntil({ targetDate: '2026-11-02' }, '2026-11-02'), 0)
  assert.equal(daysUntil({ targetDate: '2026-11-07' }, '2026-11-02'), 5)
  assert.equal(daysUntil({ targetDate: '2026-10-28' }, '2026-11-02'), -5)
})

test('数字分段：天/周/月/年月', () => {
  assert.deepEqual(countParts({ targetDate: '2026-11-07', unit: 'DAY' }, '2026-11-02'), [{ value: '5', unit: '' }])
  assert.deepEqual(countParts({ targetDate: '2026-11-08', unit: 'WEEK' }, '2026-11-02'), [
    { value: '0', unit: '周' },
    { value: '6', unit: '天' }
  ])
  assert.deepEqual(countParts({ targetDate: '2027-01-01', unit: 'MONTH' }, '2026-11-02'), [
    { value: '1', unit: '月' },
    { value: '30', unit: '天' }
  ])
  assert.deepEqual(countParts({ targetDate: '2029-01-05', unit: 'YEAR' }, '2026-11-02'), [
    { value: '2', unit: '年' },
    { value: '2', unit: '月' },
    { value: '3', unit: '天' }
  ])
})

test('数字分段：过去日期用绝对值', () => {
  assert.deepEqual(countParts({ targetDate: '2026-10-28', unit: 'DAY' }, '2026-11-02'), [{ value: '5', unit: '' }])
})

test('文案：还有/已经/今天', () => {
  assert.equal(directionText({ targetDate: '2026-11-07' }, '2026-11-02'), '还有')
  assert.equal(directionText({ targetDate: '2026-10-28' }, '2026-11-02'), '已经')
  assert.equal(footerText({ targetDate: '2026-11-02' }, '2026-11-02'), '今天 2026-11-02')
  assert.equal(footerText({ targetDate: '2026-11-07' }, '2026-11-02'), '距离 2026-11-07 还有')
})

test('单位循环：按参考图顺序 天→周→月→秒→分→时→年月', () => {
  assert.equal(cycleUnit('DAY'), 'WEEK')
  assert.equal(cycleUnit('MONTH'), 'SECOND')
  assert.equal(cycleUnit('HOUR'), 'YEAR')
  assert.equal(cycleUnit('YEAR'), 'DAY')
})

test('排序：置顶优先，其余按远近', () => {
  const items = [
    { publicId: 'a', title: 'A', targetDate: '2026-11-20' },
    { publicId: 'b', title: 'B', targetDate: '2026-10-30', pinned: true },
    { publicId: 'c', title: 'C', targetDate: '2026-11-01' }
  ]
  assert.deepEqual(sortItems(items, '2026-11-02').map(item => item.publicId), ['b', 'c', 'a'])
})

test('页签：生日归入纪念日，节日单列', () => {
  assert.equal(matchesTab({ kind: 'BIRTHDAY' }, 'ANNIVERSARY'), true)
  assert.equal(matchesTab({ kind: 'BIRTHDAY' }, 'COUNTDOWN'), false)
  assert.equal(matchesTab({ kind: 'FESTIVAL' }, 'FESTIVAL'), true)
  assert.equal(matchesTab({}, 'ALL'), true)
})

test('类型：缺 kind 时回落到倒数日', () => {
  assert.equal(typeOf({ kind: 'ANNIVERSARY' }).key, 'ANNIVERSARY')
  assert.equal(typeOf({}).key, 'COUNTDOWN')
})

test('提醒与重复：展示文案', () => {
  assert.equal(reminderText({ remind: '0,3' }), '当天, 提前 3 天')
  assert.equal(reminderText({ remind: '' }), '无')
  assert.equal(repeatText({ repeatRule: 'WEEKLY', targetDate: '2026-11-02' }), '每周（周一）')
  assert.equal(repeatText({ repeatRule: 'YEARLY', targetDate: '2026-11-02' }), '每年（11月2日）')
  assert.equal(repeatText({}), '无')
})

test('主题：浅色卡片走浅底深字，图片卡片走深底白字', () => {
  assert.equal(cardTheme({ cover: 'LIGHT', color: '#4d78db' }).tone, 'light')
  assert.match(cardTheme({ cover: 'LIGHT', color: '#4d78db' }).style.background, /color-mix/)
  assert.equal(cardTheme({ cover: 'IMAGE', color: '#4d78db' }).tone, 'dark')
  assert.match(cardTheme({ cover: 'IMAGE' }).style.background, /radial-gradient/)
  assert.match(cardTheme({ cover: 'IMAGE', coverImage: 'data:image/png;base64,xx' }).style.background, /url\(/)
})

test('新建默认值：跟随类型图标与颜色', () => {
  assert.equal(defaultCountdown('BIRTHDAY').iconValue, '🎂')
  assert.equal(defaultCountdown('BIRTHDAY').repeatRule, 'YEARLY')
  assert.equal(defaultCountdown('FESTIVAL').kind, 'FESTIVAL')
  assert.equal(defaultCountdown().unit, 'DAY')
  assert.equal(defaultCountdown().archived, false)
})
