/**
 * 全局浮层总线。
 *
 * 页面上很多浮层各自用 `v-if` 控制，点击 A 的触发按钮再点 B 的触发按钮时，
 * A 的「点外面关闭」逻辑因为 target 命中了另一个按钮而提前 return，于是两个浮层同时挂着。
 * 这里让每个浮层在打开时广播一个带命名空间的 id，其它浮层收到「不是自己人」的广播就全部收起。
 */
const listeners = new Set()

/** 广播某个浮层打开了，id 形如 `quickadd:list`、`workspace:date` */
export function broadcastPanel(id) {
  for (const listener of [...listeners]) {
    try {
      listener(id)
    } catch {
      // 单个订阅者出错不影响其它浮层
    }
  }
}

/** 订阅浮层广播，返回取消订阅函数 */
export function subscribePanel(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
