// 外观主题缩略图资源。图片只在 Vite 构建里可解析，因此单独成模块，
// 不要放进 settingsState.js（那个文件会被 node --test 直接 import，解析不了 .webp）。
import seasonSpring from '../../assets/themes/season-spring.webp'
import seasonSummer from '../../assets/themes/season-summer.webp'
import seasonAutumn from '../../assets/themes/season-autumn.webp'
import seasonWinter from '../../assets/themes/season-winter.webp'
import cityShanghai from '../../assets/themes/city-shanghai.webp'

export const THEME_ART = {
  seasonTheme: {
    SPRING: seasonSpring,
    SUMMER: seasonSummer,
    AUTUMN: seasonAutumn,
    WINTER: seasonWinter
  },
  cityTheme: {
    SHANGHAI: cityShanghai
  },
  photoTheme: {}
}
