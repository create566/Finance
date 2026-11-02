import type { Router } from 'vue-router'

export interface MenuItem {
  path: string
  title: string
  icon: string
  children?: MenuItem[]
}

/**
 * 侧边栏菜单 —— 唯一真源。
 *
 * ## 本轮重构要点
 * 1. **一级项由 9 项减至 7 项**，并消除两处「假层级」：
 *    - 原「我的合同」只有 1 个子项却套了一层折叠 → 并入「我的账户」（合同属账户资产）
 *    - 原「智能客服」只有 1 个子项 → 移出菜单（全局 `ChatBubble` 悬浮气泡已覆盖全站，
 *      菜单入口属第三条重复入口；`/chat` 路由保留，供气泡内「展开」跳转）
 * 2. 「实名认证」改名 **「认证中心」** —— 该页实际同时承载投资人 / 借款人两种认证
 *    （见 `views/auth/index.vue` 的 `authType` 切换），旧名字既不准确，
 *    也是用户"找不到认证入口"的根源之一。
 * 3. 「投资理财 / 借款服务」改为动词化的 **「我要投资 / 我要借款」**，
 *    与首页快捷区文案统一，直接对应用户意图。
 *
 * ## 防止再次漂移
 * 菜单与路由是两套清单（菜单的父级 path 如 `/account` 只是 `el-sub-menu` 的索引，
 * 并无对应路由），历史上出现过菜单指向已删除路由的情况。
 * `assertMenuRoutes()` 在应用启动时校验所有**叶子**菜单项在路由表中存在，
 * 不一致即告警 —— 保留手写菜单以维持顺序可控，同时用断言消除漂移风险。
 */
export const menuList: MenuItem[] = [
  {
    path: '/home',
    title: '首页',
    icon: 'HomeFilled',
  },
  {
    path: '/invest',
    title: '我要投资',
    icon: 'TrendCharts',
    children: [
      { path: '/invest/list', title: '标的列表', icon: '' },
      { path: '/invest/my', title: '我的投资', icon: '' },
      { path: '/invest/return', title: '回款计划', icon: '' },
    ],
  },
  {
    path: '/borrow',
    title: '我要借款',
    icon: 'Money',
    children: [
      { path: '/borrow/apply', title: '借款申请', icon: '' },
      { path: '/borrow/my', title: '我的借款', icon: '' },
      { path: '/borrow/repay', title: '还款计划', icon: '' },
    ],
  },
  {
    path: '/account',
    title: '我的账户',
    icon: 'Wallet',
    children: [
      { path: '/account/overview', title: '账户总览', icon: '' },
      { path: '/account/flow', title: '资金流水', icon: '' },
      { path: '/contract/my', title: '我的合同', icon: '' },
    ],
  },
  {
    path: '/auth',
    title: '认证中心',
    icon: 'Postcard',
  },
  {
    path: '/message',
    title: '消息中心',
    icon: 'Bell',
  },
  {
    path: '/profile',
    title: '个人中心',
    icon: 'User',
  },
]

/** 展开当前路由所属的一级菜单（供 `el-menu` 的 default-openeds） */
export function openedMenuOf(path: string): string[] {
  for (const item of menuList) {
    if (item.children?.some((child) => child.path === path)) {
      return [item.path]
    }
  }
  return []
}

/**
 * 当前路由应高亮哪个菜单项。
 * 详情页回落到其列表页，避免"进了详情页但侧栏没有任何高亮"。
 */
export function activeMenuOf(path: string): string {
  const all = menuList.flatMap((item) => (item.children?.length ? item.children : [item]))
  if (all.some((item) => item.path === path)) return path
  if (path.startsWith('/invest/detail')) return '/invest/list'
  if (path.startsWith('/contract/detail')) return '/contract/my'
  return path
}

/**
 * 启动期一致性断言：所有叶子菜单项都必须能在路由表中找到。
 * 不一致时给出明确告警，而不是等用户点到空白页才发现。
 */
export function assertMenuRoutes(router: Router): void {
  const known = new Set(router.getRoutes().map((route) => route.path))
  const missing: string[] = []
  for (const item of menuList) {
    const leaves = item.children?.length ? item.children : [item]
    for (const leaf of leaves) {
      if (!known.has(leaf.path)) {
        missing.push(`${leaf.title}(${leaf.path})`)
      }
    }
  }
  if (missing.length > 0) {
    console.warn('[menu] 菜单与路由已漂移，以下菜单项在路由表中不存在：' + missing.join('、'))
  }
}
