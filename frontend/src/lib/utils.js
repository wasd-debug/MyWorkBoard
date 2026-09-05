export function cn(...values) {
  return values.flatMap(value => typeof value === 'string' ? value.split(' ') : value ? [value] : []).join(' ')
}
