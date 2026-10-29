import { onBeforeUnmount, watch } from 'vue'
import { broadcastPanel, subscribePanel } from './panelBus.js'

/**
 * 让一组浮层互斥：任意一个打开时，同组的其余浮层自动关闭；
 * 同时把这个动作广播出去，其它组件（命名空间不同）收到后会把自己那组全关掉。
 *
 * @param {string} namespace 命名空间，如 'quickadd' / 'workspace' / 'calendar'
 * @param {Array<[string, () => boolean, (value: boolean) => void]>} panels
 *        每项是 [名字, 读取状态, 写入状态]，用函数而不是 ref 是为了能包住 reactive 对象里的字段
 * @param {Object} [options]
 * @param {(foreignId: string) => void} [options.onForeign] 收到别人的广播时额外要做的事
 * @returns {() => void} 手动取消订阅
 */
export function useExclusivePanels(namespace, panels, options = {}) {
  const prefix = `${namespace}:`
  const closeAll = (except) => {
    for (const [name, , set] of panels) {
      if (name !== except) set(false)
    }
  }

  for (const [name, get] of panels) {
    watch(get, (value) => {
      if (!value) return
      closeAll(name)
      broadcastPanel(`${prefix}${name}`)
    })
  }

  const stop = subscribePanel((id) => {
    if (!id) return
    if (String(id).startsWith(prefix)) return
    closeAll('')
    options.onForeign?.(id)
  })

  onBeforeUnmount(stop)
  return stop
}
