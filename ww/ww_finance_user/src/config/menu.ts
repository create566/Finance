export interface MenuItem {
  path: string
  title: string
  icon: string
  children?: MenuItem[]
}

export const menuList: MenuItem[] = [
  {
    path: '/home',
    title: '首页',
    icon: 'HomeFilled',
  },
  {
    path: '/auth',
    title: '实名认证',
    icon: 'Postcard',
  },
  {
    path: '/account',
    title: '我的账户',
    icon: 'Wallet',
    children: [
      { path: '/account/overview', title: '账户总览', icon: '' },
      { path: '/account/flow', title: '资金流水', icon: '' },
    ],
  },
  {
    path: '/invest',
    title: '投资理财',
    icon: 'TrendCharts',
    children: [
      { path: '/invest/list', title: '标的列表', icon: '' },
      { path: '/invest/my', title: '我的投资', icon: '' },
      { path: '/invest/return', title: '回款计划', icon: '' },
    ],
  },
  {
    path: '/borrow',
    title: '借款服务',
    icon: 'Money',
    children: [
      { path: '/borrow/apply', title: '借款申请', icon: '' },
      { path: '/borrow/my', title: '我的借款', icon: '' },
      { path: '/borrow/repay', title: '还款计划', icon: '' },
    ],
  },
  {
    path: '/contract',
    title: '我的合同',
    icon: 'Document',
    children: [{ path: '/contract/my', title: '合同列表', icon: '' }],
  },
  {
    path: '/message',
    title: '消息中心',
    icon: 'Bell',
  },
  {
    path: '/chat',
    title: '智能客服',
    icon: 'ChatDotRound',
    children: [
      { path: '/chat', title: '在线咨询', icon: '' },
    ],
  },
  {
    path: '/profile',
    title: '个人中心',
    icon: 'User',
  },
]