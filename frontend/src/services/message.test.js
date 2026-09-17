import test from 'node:test'
import assert from 'node:assert/strict'
import { ElMessage, dismissMessage, messages, resetMessages } from './message.js'

test('message service queues typed notices and dismisses a single duplicate', () => {
  resetMessages()
  const first = ElMessage.success({ message: '已保存', duration: 0 })
  const second = ElMessage.success({ message: '已保存', duration: 0 })
  ElMessage.warning({ message: '当前离线', duration: 0 })
  ElMessage.error({ message: '同步失败', duration: 0 })

  assert.deepEqual(messages.map(item => [item.type, item.text]), [
    ['success', '已保存'],
    ['success', '已保存'],
    ['warning', '当前离线'],
    ['error', '同步失败']
  ])
  dismissMessage(first)
  assert.equal(messages.length, 3)
  assert.equal(messages[0].id, second)
  resetMessages()
})
