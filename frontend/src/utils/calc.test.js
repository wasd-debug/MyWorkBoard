import test from 'node:test'
import assert from 'node:assert/strict'
import { CALC } from './calc.js'

test('period stats use persisted server overtime for saved records', () => {
  const settings = {
    workStart: '09:00',
    workEnd: '18:00',
    lunchMin: 90,
    daysPerMonth: 21.75,
    salaryPost: 8700
  }
  const records = {
    '2026-09-17': {
      id: 17,
      start: '09:00',
      end: '18:30',
      rest: 0,
      overtimeMin: 15
    }
  }

  const result = CALC.periodStats(['2026-09-17'], records, settings, 'post')

  assert.equal(result.totalMin, 480)
  assert.equal(result.otMin, 15)
})

test('period stats still calculate overtime for an unsaved preview', () => {
  const settings = {
    workStart: '09:00',
    workEnd: '18:00',
    lunchMin: 90,
    daysPerMonth: 21.75,
    salaryPost: 8700
  }
  const records = {
    '2026-09-17': { start: '09:00', end: '18:30', rest: 0 }
  }

  const result = CALC.periodStats(['2026-09-17'], records, settings, 'post')

  assert.equal(result.otMin, 30)
})

test('day rate changes with the selected salary basis for saved records', () => {
  const settings = {
    workStart: '09:00',
    workEnd: '18:00',
    lunchMin: 90,
    daysPerMonth: 21.75,
    salaryPre: 13000,
    salaryPost: 9000
  }
  const record = { id: 17, start: '09:00', end: '18:00', rest: 0, realHourlyWage: 62.84 }

  assert.equal(CALC.dayRate(record, settings, 'pre', 21.75).toFixed(2), '79.69')
  assert.equal(CALC.dayRate(record, settings, 'post', 21.75).toFixed(2), '55.17')
})
