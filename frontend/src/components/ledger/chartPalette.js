const PRIMARY_HUE_BASE = 178
const PRIMARY_HUE_STEP = 137.508
const RESOURCE_COLORS = ['#59c0c5', '#5b8def', '#7652ef', '#ff7181', '#f2b44d', '#45a78e', '#de7549', '#8f70c5']

function hashCode(value) {
  let hash = 0
  for (const char of String(value || '')) hash = (hash * 31 + char.charCodeAt(0)) | 0
  return Math.abs(hash)
}

function hsl(hue, saturation, lightness) {
  return `hsl(${Math.round(hue % 360)} ${saturation}% ${lightness}%)`
}

function primaryHue(index) {
  return (PRIMARY_HUE_BASE + Math.max(0, Number(index) || 0) * PRIMARY_HUE_STEP) % 360
}

function colorHue(color) {
  const match = String(color || '').match(/^hsl\(\s*(\d+(?:\.\d+)?)/i)
  return match ? Number(match[1]) % 360 : null
}

export function stableResourceColor(key, index = 0) {
  return RESOURCE_COLORS[(hashCode(key) + index) % RESOURCE_COLORS.length]
}

export function categoryColor(item, index = 0) {
  if (item?.color && String(item.color).toLowerCase() !== '#0f5132') return item.color
  const parentKey = item?.parentCategoryId || item?.parentId || item?.parentCategoryName || item?.parentName
  const inheritedHue = colorHue(item?.parentColor || item?.parentCategoryColor)
  const hue = parentKey
    ? inheritedHue ?? (item?.parentPaletteIndex != null ? primaryHue(item.parentPaletteIndex) : hashCode(parentKey) % 360)
    : primaryHue(item?.paletteIndex ?? index)
  const childIndex = Number(item?.siblingIndex ?? item?.index ?? index) % 6
  return hsl(hue, 58 + (childIndex % 2) * 8, 42 + childIndex * 7)
}

export function categorySeries(rows = []) {
  return rows.map((item, index) => categoryColor(item, index))
}

export function resourceSeries(rows = []) {
  return rows.map((item, index) => item?.color || stableResourceColor(item?.id || item?.key || item?.name, index))
}

export const chartPalette = {
  primary: Array.from({ length: 12 }, (_, index) => hsl(primaryHue(index), 64, 48 + (index % 3) * 6)),
  resources: RESOURCE_COLORS
}
