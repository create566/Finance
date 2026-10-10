import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import Layout from '@/layout/index.vue'

/**
 * 免登录白名单。
 *
 * ⚠ `/bank/return` 必须在里面：它是**银行回跳的落地页**，而银行回跳是一次全新的整页加载，
 * 登录态 cookie 按 host 存，回跳 host 与实际访问 host 不一致时（127.0.0.1 vs localhost）
 * 根本带不过来。若这个页面还需要登录，用户付完款就会看到"掉登录页"。
 */
const whiteList = ['/login', '/register', '/bank/return']

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
  { path: '/:pathMatch(.*)*', redirect: '/home' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, _from, next) => {
  const token = getToken()
  if (token) {
    if (to.path === '/login' || to.path === '/register') {
      next('/')
    } else {
      next()
    }
  } else {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next('/login')
    }
  }
})

export default router