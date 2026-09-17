import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { api } from '../api/index.js'
import { useWorktimeStore } from './worktime.js'

function response(config, data) {
  return {
    data: { data },
    status: 200,
    statusText: 'OK',
    headers: {},
    config
  }
}

test('fetch loads worktime settings and records from resource endpoints', async t => {
  const originalAdapter = api.defaults.adapter
  const requests = []
  api.defaults.adapter = async config => {
    requests.push(config.url)
    if (config.url === '/v1/worktime/settings') {
      return response(config, { workStart: '09:00', revision: 3 })
    }
    if (config.url === '/v1/worktime/records') {
      return response(config, [{
        id: 17,
        date: '2026-09-17',
        start: '09:00',
        end: '18:30',
        rest: 90,
        overtimeMin: 30,
        realHourlyWage: 42.5,
        calcVersion: 1,
        timezone: 'Asia/Shanghai',
        revision: 2
      }])
    }
    return response(config, {})
  }
  t.after(() => { api.defaults.adapter = originalAdapter })

  setActivePinia(createPinia())
  const store = useWorktimeStore()
  await store.fetch()

  assert.deepEqual(requests, [
    '/v1/worktime/settings',
    '/v1/worktime/records'
  ])
  assert.equal(store.settings.revision, 3)
  assert.deepEqual(store.records['2026-09-17'], {
    id: 17,
    date: '2026-09-17',
    start: '09:00',
    end: '18:30',
    rest: 90,
    overtimeMin: 30,
    realHourlyWage: 42.5,
    calcVersion: 1,
    timezone: 'Asia/Shanghai',
    revision: 2
  })
})

test('fetch follows record pages so history beyond 200 entries is not lost', async t => {
  const originalAdapter = api.defaults.adapter
  const offsets = []
  const firstPage = Array.from({ length: 200 }, (_, index) => ({
    id: index + 1,
    date: `2026-09-${String((index % 30) + 1).padStart(2, '0')}`,
    revision: 1
  }))
  api.defaults.adapter = async config => {
    if (config.url === '/v1/worktime/settings') return response(config, { revision: 1 })
    offsets.push(Number(config.params?.offset || 0))
    return response(config, Number(config.params?.offset || 0) === 0
      ? firstPage
      : [
          { id: 201, date: '2020-01-02', revision: 1 },
          { id: 202, date: '2020-01-01', revision: 1 }
        ])
  }
  t.after(() => { api.defaults.adapter = originalAdapter })

  setActivePinia(createPinia())
  const store = useWorktimeStore()
  await store.fetch()

  assert.deepEqual(offsets, [0, 200])
  assert.equal(store.records['2020-01-01'].id, 202)
})

test('resource commands use idempotency and revision headers and apply server projections', async t => {
  const originalAdapter = api.defaults.adapter
  const requests = []
  api.defaults.adapter = async config => {
    requests.push({ method: config.method, url: config.url, headers: config.headers })
    if (config.method === 'patch') {
      return response(config, { id: 17, date: '2026-09-17', start: '09:00', end: '19:00', overtimeMin: 60, realHourlyWage: 40, revision: 3 })
    }
    if (config.method === 'post') {
      return response(config, { id: 18, date: '2026-09-18', start: '09:00', end: '18:00', overtimeMin: 0, realHourlyWage: 35, revision: 1 })
    }
    return response(config, { id: 17, revision: 4, deleted: true })
  }
  t.after(() => { api.defaults.adapter = originalAdapter })

  setActivePinia(createPinia())
  const store = useWorktimeStore()
  store.settings = { revision: 2 }
  store.records = {
    '2026-09-17': { id: 17, date: '2026-09-17', start: '09:00', revision: 2 }
  }

  await store.saveRecord({ date: '2026-09-17', start: '09:00', end: '19:00', rest: 90 })
  await store.saveRecord({ date: '2026-09-18', start: '09:00', end: '18:00', rest: 90 })
  await store.deleteRecord('2026-09-17')

  assert.equal(requests[0].method, 'patch')
  assert.equal(requests[0].url, '/v1/worktime/records/17')
  assert.equal(requests[0].headers['If-Match'], '2')
  assert.equal(requests[1].method, 'post')
  assert.equal(requests[1].headers['Idempotency-Key']?.length > 0, true)
  assert.equal(requests[2].method, 'delete')
  assert.equal(requests[2].headers['If-Match'], '3')
  assert.equal(store.records['2026-09-18'].realHourlyWage, 35)
  assert.equal(store.records['2026-09-17'], undefined)
})

test('record conflict keeps the current server projection intact', async t => {
  const originalAdapter = api.defaults.adapter
  api.defaults.adapter = async config => {
    const error = new Error('Request failed with status code 409')
    error.config = config
    error.response = {
      status: 409,
      data: { detail: '资源版本已变化', currentRevision: 3 },
      headers: {},
      config
    }
    throw error
  }
  t.after(() => { api.defaults.adapter = originalAdapter })

  setActivePinia(createPinia())
  const store = useWorktimeStore()
  const projection = {
    id: 17,
    date: '2026-09-17',
    start: '09:00',
    end: '18:00',
    overtimeMin: 0,
    realHourlyWage: 35,
    revision: 2
  }
  store.records = { '2026-09-17': { ...projection } }

  await assert.rejects(
    store.saveRecord({ date: '2026-09-17', start: '08:30', end: '20:00', rest: 30 }),
    error => error.response?.status === 409
  )
  assert.deepEqual(store.records['2026-09-17'], projection)
})

test('saveSettings merges a patch and keeps untouched settings', async t => {
  const originalAdapter = api.defaults.adapter
  let submitted
  api.defaults.adapter = async config => {
    submitted = JSON.parse(config.data)
    return response(config, { ...submitted, revision: 5 })
  }
  t.after(() => { api.defaults.adapter = originalAdapter })

  setActivePinia(createPinia())
  const store = useWorktimeStore()
  store.settings = { workStart: '09:00', workEnd: '18:00', lunchMin: 90, revision: 4 }

  await store.saveSettings({ lunchMin: 60 })

  assert.deepEqual(submitted, { workStart: '09:00', workEnd: '18:00', lunchMin: 60, revision: 4 })
  assert.equal(store.settings.workStart, '09:00')
  assert.equal(store.settings.lunchMin, 60)
  assert.equal(store.settings.revision, 5)
})

test('clearResources deletes records and resets settings through resource endpoints', async t => {
  const originalAdapter = api.defaults.adapter
  const requests = []
  api.defaults.adapter = async config => {
    requests.push({ method: config.method, url: config.url })
    if (config.url === '/v1/worktime/settings') return response(config, { workStart: '09:00', revision: 3 })
    return response(config, { deleted: true })
  }
  t.after(() => { api.defaults.adapter = originalAdapter })

  setActivePinia(createPinia())
  const store = useWorktimeStore()
  store.settings = { workStart: '08:30', revision: 2 }
  store.records = {
    '2026-09-17': { id: 17, date: '2026-09-17', revision: 2 },
    '2026-09-18': { id: 18, date: '2026-09-18', revision: 1 }
  }

  await store.clearResources({ workStart: '09:00' })

  assert.deepEqual(requests, [
    { method: 'delete', url: '/v1/worktime/records/17' },
    { method: 'delete', url: '/v1/worktime/records/18' },
    { method: 'put', url: '/v1/worktime/settings' }
  ])
  assert.deepEqual(store.records, {})
  assert.equal(store.settings.workStart, '09:00')
})
