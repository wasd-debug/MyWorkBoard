import { CircleAlert, CircleDot, Flag, Leaf, Star, Zap } from 'lucide-vue-next'

/** 象限图标：key -> lucide 组件，未知 key 回退到感叹号圆点 */
const ICONS = {
  alert: CircleAlert,
  star: Star,
  bolt: Zap,
  leaf: Leaf,
  flag: Flag,
  dot: CircleDot
}

export const QUADRANT_ICON_OPTIONS = [
  { value: 'alert', label: '感叹号' },
  { value: 'star', label: '星标' },
  { value: 'bolt', label: '闪电' },
  { value: 'leaf', label: '叶子' },
  { value: 'flag', label: '旗帜' }
]

export function resolveQuadrantIcon(value) {
  return ICONS[value] || CircleAlert
}
