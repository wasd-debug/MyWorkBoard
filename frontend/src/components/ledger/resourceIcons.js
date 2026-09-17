import { defineComponent, h } from 'vue'
import {
  Bicycle,
  Briefcase,
  Coin,
  CollectionTag,
  CreditCard,
  Film,
  FirstAidKit,
  FolderOpened,
  Food,
  House,
  Money,
  MoreFilled,
  Reading,
  Shop,
  ShoppingBag,
  Suitcase,
  User,
  Wallet,
  ChatDotRound,
  Document,
  Goods,
  Location,
  Present,
  Service,
  Tickets,
  Trophy
} from '../../icons.js'

function bankMark(character, tone) {
  return defineComponent({
    name: `LedgerBank${character}`,
    render: () => h('span', {
      style: { color: tone, fontSize: '14px', fontWeight: '800', lineHeight: '1' },
      'aria-hidden': 'true'
    }, character)
  })
}

const accountIcons = [
  { value: 'wallet', label: '钱包', component: Wallet },
  { value: 'bank-card', label: '银行卡', component: CreditCard },
  { value: 'cash', label: '现金', component: Money },
  { value: 'coin', label: '资金', component: Coin },
  { value: 'bank-boc', label: '中国银行', component: bankMark('中', '#a6212a') },
  { value: 'bank-abc', label: '农业银行', component: bankMark('农', '#168766') },
  { value: 'bank-icbc', label: '工商银行', component: bankMark('工', '#b52c32') },
  { value: 'bank-ccb', label: '建设银行', component: bankMark('建', '#246a9b') },
  { value: 'bank-cmb', label: '招商银行', component: bankMark('招', '#b32934') },
  { value: 'bank-bocom', label: '交通银行', component: bankMark('交', '#27698d') },
  { value: 'bank-psbc', label: '邮储银行', component: bankMark('邮', '#167c58') },
  { value: 'alipay', label: '支付宝', component: bankMark('支', '#2376bb') },
  { value: 'wechat-pay', label: '微信支付', component: bankMark('微', '#23875b') }
]
const categoryIcons = [
  { value: 'tag', label: '分类', component: CollectionTag },
  { value: 'food', label: '餐饮', component: Food },
  { value: 'transport', label: '交通', component: Bicycle },
  { value: 'home', label: '居住', component: House },
  { value: 'shopping', label: '购物', component: ShoppingBag },
  { value: 'health', label: '医疗', component: FirstAidKit },
  { value: 'education', label: '教育', component: Reading },
  { value: 'entertainment', label: '娱乐', component: Film },
  { value: 'travel', label: '旅行', component: Suitcase },
  { value: 'work', label: '工作', component: Briefcase },
  { value: 'bills', label: '账单', component: Document },
  { value: 'gift', label: '礼物', component: Present },
  { value: 'service', label: '服务', component: Service },
  { value: 'location', label: '出行', component: Location },
  { value: 'goods', label: '日用品', component: Goods },
  { value: 'ticket', label: '票券', component: Tickets },
  { value: 'trophy', label: '奖金', component: Trophy }
]
const memberIcons = [
  { value: 'user', label: '成员', component: User },
  { value: 'chat', label: '联系人', component: ChatDotRound },
  { value: 'work', label: '工作伙伴', component: Briefcase },
  { value: 'home', label: '家庭成员', component: House },
  { value: 'service', label: '协作者', component: Service }
]
const otherIcons = [
  { value: 'shop', label: '商家', component: Shop },
  { value: 'folder', label: '项目', component: FolderOpened },
  { value: 'other', label: '其他', component: MoreFilled },
  { value: 'shopping', label: '购物', component: ShoppingBag },
  { value: 'work', label: '工作', component: Briefcase }
]

export const ledgerIconOptions = [...accountIcons, ...categoryIcons, ...memberIcons, ...otherIcons]
export function ledgerIconOptionsFor(type) {
  return ({
    account: accountIcons,
    category: categoryIcons,
    member: memberIcons,
    merchant: otherIcons,
    project: otherIcons
  })[type] || otherIcons
}

const iconComponents = new Map(ledgerIconOptions.map(item => [item.value, item.component]))
const iconComponentsByType = new Map([
  ['account', new Map(accountIcons.map(item => [item.value, item.component]))],
  ['category', new Map(categoryIcons.map(item => [item.value, item.component]))],
  ['member', new Map(memberIcons.map(item => [item.value, item.component]))],
  ['merchant', new Map(otherIcons.map(item => [item.value, item.component]))],
  ['project', new Map(otherIcons.map(item => [item.value, item.component]))]
])

export function ledgerIconComponent(value, fallback = 'other', type = 'other') {
  const typed = iconComponentsByType.get(type)
  return typed?.get(value) || typed?.get(fallback) || iconComponents.get(value) || iconComponents.get(fallback) || MoreFilled
}

export function ledgerIconFallback(type) {
  return ({
    account: 'wallet',
    category: 'tag',
    merchant: 'shop',
    member: 'user',
    project: 'folder'
  })[type] || 'other'
}
