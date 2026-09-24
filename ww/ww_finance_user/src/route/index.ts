import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import Layout from '@/layout/index.vue'

const whiteList = ['/login', '/register']

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