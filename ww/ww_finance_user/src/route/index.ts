import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import Layout from '@/layout/index.vue'
import { grantEntry, hasEntry } from '@/utils/entryGuard'

/** 唯一入口：只能从这里进项目 */
const LOGIN_PATH = '/login'
/** 注册页：只允许从登录页点「立即注册」进入 */
const REGISTER_PATH = '/register'
/**
 * 银行回跳落地页：**必须免登录**。
 * 银行走完流程会按 `returnUrl` 做一次整页跳转，而 `user_token` 是**按 host 存**的 cookie，
 * 回跳 host 若与应用访问 host 不一致（127.0.0.1 vs localhost、或换 IP/域名）就带不过来；
 * 此前 returnUrl 直指 `/home`，用户付完款反而被丢到登录页，观感是"付完款就退登了"。
 */
const BANK_RETURN_PATH = '/bank/return'

function getToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)user_token=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/register.vue'),
    meta: { title: '注册', public: true },
  },
  {
    // 银行回跳落地页：独立于 Layout（不拉用户信息、不需要登录态），
    // 后端 HfbConst 的 6 个 *_RETURN_URL 都指向这里。
    path: '/bank/return',
    name: 'BankReturn',
    component: () => import('@/views/bank/return.vue'),
    meta: { title: '操作结果', public: true },
  },
  {
    path: '/',
    component: Layout,
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/home/index.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'account/overview',
        name: 'AccountOverview',
        component: () => import('@/views/account/overview.vue'),
        meta: { title: '账户总览' },
      },
      {
        path: 'account/flow',
        name: 'AccountFlow',
        component: () => import('@/views/account/flow.vue'),
        meta: { title: '资金流水' },
      },
      {
        path: 'invest/list',
        name: 'InvestList',
        component: () => import('@/views/invest/list.vue'),
        meta: { title: '标的列表' },
      },
      {
        path: 'invest/detail/:id',
        name: 'InvestDetail',
        component: () => import('@/views/invest/detail.vue'),
        meta: { title: '标的详情' },
      },
      {
        path: 'invest/my',
        name: 'MyInvest',
        component: () => import('@/views/invest/my.vue'),
        meta: { title: '我的投资' },
      },
      {
        path: 'invest/return',
        name: 'InvestReturn',
        component: () => import('@/views/invest/return.vue'),
        meta: { title: '回款计划' },
      },
      {
        path: 'contract/my',
        name: 'MyContracts',
        component: () => import('@/views/contract/my.vue'),
        meta: { title: '我的合同' },
      },
      {
        path: 'contract/detail/:id',
        name: 'ContractDetail',
        component: () => import('@/views/contract/detail.vue'),
        meta: { title: '合同详情' },
      },
      {
        path: 'auth',
        name: 'RealnameAuth',
        component: () => import('@/views/auth/index.vue'),
        meta: { title: '实名认证' },
      },
      {
        path: 'invest/auth',
        name: 'InvestAuth',
        component: () => import('@/views/invest/auth.vue'),
        meta: { title: '投资人认证' },
      },
      {
        path: 'borrow/auth',
        name: 'BorrowAuth',
        component: () => import('@/views/borrow/auth.vue'),
        meta: { title: '借款人认证' },
      },
      {
        path: 'borrow/apply',
        name: 'BorrowApply',
        component: () => import('@/views/borrow/apply.vue'),
        meta: { title: '借款申请' },
      },
      {
        path: 'borrow/my',
        name: 'MyBorrow',
        component: () => import('@/views/borrow/my.vue'),
        meta: { title: '我的借款' },
      },
      {
        path: 'borrow/repay',
        name: 'BorrowRepay',
        component: () => import('@/views/borrow/repay.vue'),
        meta: { title: '还款计划' },
      },
      {
        path: 'message',
        name: 'Message',
        component: () => import('@/views/message/index.vue'),
        meta: { title: '消息中心' },
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/chat/index.vue'),
        meta: { title: '智能客服' },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: LOGIN_PATH },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/**
 * 全局守卫：**唯一入口是 `/login`**。
 *
 * 通行证 `hasEntry()` 是按标签页隔离的会话标记（见 `@/utils/entryGuard`）：
 * 只有在本标签页真正登录过才发放，因此即使浏览器里还留着 7 天有效的 `user_token` cookie，
 * 直接敲 `/home`、`/invest/list` 等深链（或用 IP 根路径打开）也会回到登录页。
 */
router.beforeEach((to, from, next) => {
  const token = getToken()

  // 1) 登录页：本标签页登录过才能直接进应用，否则停在登录页等用户登录
  if (to.path === LOGIN_PATH) {
    if (token && hasEntry()) {
      next({ path: '/' })
    } else {
      next()
    }
    return
  }

  // 2) 银行回跳落地页：放行；同浏览器已有登录态时补发通行证，避免付款后被抛回登录页
  if (to.path === BANK_RETURN_PATH) {
    if (token) {
      grantEntry()
    }
    next()
    return
  }

  // 3) 注册页：只允许从登录页点「立即注册」进来（含已登录态下的标签页内跳转）
  if (to.path === REGISTER_PATH) {
    if (hasEntry() || from.path === LOGIN_PATH) {
      next()
    } else {
      next({ path: LOGIN_PATH })
    }
    return
  }

  // 4) 其余全部页面（/home、业务页、未知路径）：必须"本标签页从登录页登录过"
  if (token && hasEntry()) {
    next()
  } else {
    next({ path: LOGIN_PATH })
  }
})

export default router