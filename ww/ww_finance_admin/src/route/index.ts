import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import Layout from '@/layout/index.vue'

// 白名单（无需登录）
const whiteList = ['/login']

// 从 cookie 读 token
function getToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)admin_token=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

// 路由配置
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    component: Layout,
    redirect: '/point/level',
    children: [
      {
        path: 'point/level',
        name: 'PointLevel',
        component: () => import('@/views/point/level.vue'),
        meta: { title: '积分等级列表' },
      },
      {
        path: 'member/list',
        name: 'MemberList',
        component: () => import('@/views/member/list.vue'),
        meta: { title: '会员列表' },
      },
      {
        path: 'borrow/record',
        name: 'BorrowRecord',
        component: () => import('@/views/borrow/record.vue'),
        meta: { title: '借款列表' },
      },
      {
        path: 'borrow/person',
        name: 'BorrowPerson',
        component: () => import('@/views/borrow/person.vue'),
        meta: { title: '借款人列表' },
      },
      {
        path: 'invest/person',
        name: 'InvestPerson',
        component: () => import('@/views/invest/person.vue'),
        meta: { title: '投资人列表' },
      },
      {
        path: 'loan/project',
        name: 'LoanProject',
        component: () => import('@/views/loan/project.vue'),
        meta: { title: '标的列表' },
      },
      {
        path: 'contract/record',
        name: 'ContractRecord',
        component: () => import('@/views/contract/record.vue'),
        meta: { title: '合同台账' },
      },
      {
        path: 'contract/template',
        name: 'ContractTemplate',
        component: () => import('@/views/contract/template.vue'),
        meta: { title: '合同模板管理' },
      },
      {
        path: 'finance/flow',
        name: 'FinanceFlow',
        component: () => import('@/views/finance/flow.vue'),
        meta: { title: '资金流水' },
      },
      {
        path: 'knowledge/list',
        name: 'KnowledgeList',
        component: () => import('@/views/knowledge/list.vue'),
        meta: { title: '知识库列表' },
      },
      {
        path: 'knowledge/detail/:id',
        name: 'KnowledgeDetail',
        component: () => import('@/views/knowledge/detail.vue'),
        meta: { title: '知识库详情' },
      },
      {
        path: 'model/list',
        name: 'ModelList',
        component: () => import('@/views/model/list.vue'),
        meta: { title: '模型列表' },
      },
      {
        path: 'chat/record',
        name: 'ChatRecord',
        component: () => import('@/views/chat/record.vue'),
        meta: { title: '咨询记录' },
      },
      {
        path: 'chat/prompt',
        name: 'ChatPrompt',
        component: () => import('@/views/chat/prompt.vue'),
        meta: { title: 'Prompt设置' },
      },
      {
        path: 'chat/model',
        name: 'ChatModel',
        component: () => import('@/views/chat/model.vue'),
        meta: { title: '对话模型选择' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/point/level' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局路由守卫：登录鉴权（cookie token）
router.beforeEach((to, _from, next) => {
  const token = getToken()
  if (token) {
    if (to.path === '/login') {
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