// 侧边栏菜单配置
export interface MenuItem {
  path: string
  title: string
  icon: string
  children?: MenuItem[]
}

export const menuList: MenuItem[] = [
  {
    path: '/point',
    title: '积分等级管理',
    icon: 'Medal',
    children: [
      { path: '/point/level', title: '积分等级列表', icon: '' },
    ],
  },
  {
    path: '/member',
    title: '会员管理',
    icon: 'User',
    children: [
      { path: '/member/list', title: '会员列表', icon: '' },
    ],
  },
  {
    path: '/borrow',
    title: '借款管理',
    icon: 'Money',
    children: [
      { path: '/borrow/record', title: '借款列表', icon: '' },
      { path: '/borrow/person', title: '借款人列表', icon: '' },
    ],
  },
  {
    path: '/invest',
    title: '投资管理',
    icon: 'TrendCharts',
    children: [
      { path: '/invest/person', title: '投资人列表', icon: '' },
    ],
  },
  {
    path: '/loan',
    title: '标的管理',
    icon: 'Document',
    children: [
      { path: '/loan/project', title: '标的列表', icon: '' },
    ],
  },
  {
    path: '/contract',
    title: '合同管理',
    icon: 'Tickets',
    children: [
      { path: '/contract/record', title: '合同台账', icon: '' },
      { path: '/contract/template', title: '合同模板管理', icon: '' },
    ],
  },
  {
    path: '/finance',
    title: '资金管理',
    icon: 'Wallet',
    children: [{ path: '/finance/flow', title: '资金流水', icon: '' }],
  },
  {
    path: '/knowledge',
    title: '知识库管理',
    icon: 'Collection',
    children: [
      { path: '/knowledge/list', title: '知识库列表', icon: '' },
    ],
  },
  {
    path: '/model',
    title: '模型配置',
    icon: 'Setting',
    children: [
      { path: '/model/list', title: '模型列表', icon: '' },
    ],
  },
  {
    path: '/chat',
    title: '智能客服',
    icon: 'ChatDotRound',
    children: [
      { path: '/chat/record', title: '咨询记录', icon: '' },
      { path: '/chat/prompt', title: 'Prompt设置', icon: '' },
      { path: '/chat/model', title: '对话模型选择', icon: '' },
    ],
  },
]
