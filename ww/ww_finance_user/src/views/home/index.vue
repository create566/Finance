<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getUserInfo } from '@/api/user'
import { getAccount } from '@/api/account'
import { getBorrowerAuthStatus } from '@/api/borrower'
import AppCard from '@/components/common/AppCard.vue'
import GradientBanner from '@/components/common/GradientBanner.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import StatCard from '@/components/common/StatCard.vue'

const router = useRouter()
const userInfo = ref<any>(null)
const account = ref<any>(null)
const authStatus = ref<any>(null)

async function fetchData() {
  try {
    userInfo.value = await getUserInfo()
  } catch {}
  try {
    account.value = await getAccount()
  } catch {}
  try {
    authStatus.value = await getBorrowerAuthStatus()
  } catch {}
}

/**
 * 待办事项：把"现在卡在哪一步、该点哪里"直接摆到首页。
 *
 * 改造前首页是一屏静态复制信息（与「账户总览」「个人中心」重复），
 * 用户看不出自己还缺哪道关口，只能逐个菜单点开试。
 * 仅在数据已加载后判断，避免刷新瞬间闪出假的待办。
 */
const todos = computed<{ text: string; actionText: string; action: () => void }[]>(() => {
  const list: { text: string; actionText: string; action: () => void }[] = []
  if (userInfo.value && userInfo.value.bindStatus !== 1) {
    list.push({
      text: '您还未绑定银行卡，无法充值、提现与投资',
      actionText: '去绑卡',
      action: () => router.push('/profile'),
    })
  }
  if (userInfo.value && userInfo.value.investAuthStatus !== 2) {
    list.push({
      text: '您还未通过投资人认证，无法投标',
      actionText: '去认证',
      action: () => router.push('/auth'),
    })
  }
  if (authStatus.value && authStatus.value.borrowAuthStatus !== 2) {
    list.push({
      text: '您还未通过借款人认证，无法申请借款',
      actionText: '去认证',
      action: () => router.push('/auth'),
    })
  }
  return list
})

const investEntries = [
  { text: '浏览标的', icon: 'TrendCharts', path: '/invest/list' },
  { text: '我的投资', icon: 'DataLine', path: '/invest/my' },
  { text: '回款计划', icon: 'Coin', path: '/invest/return' },
  { text: '绑卡充值', icon: 'CreditCard', path: '/profile' },
]

const borrowEntries = [
  { text: '认证中心', icon: 'Postcard', path: '/auth' },
  { text: '借款申请', icon: 'EditPen', path: '/borrow/apply' },
  { text: '我的借款', icon: 'List', path: '/borrow/my' },
  { text: '还款计划', icon: 'Timer', path: '/borrow/repay' },
]

onMounted(fetchData)
</script>

<template>
  <div class="home">
    <GradientBanner
      :title="`你好，${userInfo?.nickName || '用户'}`"
      desc="资金由存管银行独立管理，平台不持有资金"
    >
      <div class="home__balance">
        <div class="home__balance-value">
          <MoneyText :value="account?.amount ?? 0" size="lg" strong />
        </div>
        <div class="home__balance-label">可用余额</div>
      </div>
    </GradientBanner>

    <div class="home__stats">
      <StatCard icon="Wallet" label="可用余额" :value="account?.amount ?? 0" gradient="brand" />
      <StatCard icon="TrendCharts" label="投资金额" :value="account?.investAmount ?? 0" gradient="accent" />
      <StatCard icon="Money" label="借款金额" :value="account?.lendAmount ?? 0" gradient="warm" />
    </div>

    <AppCard v-if="todos.length" title="待办事项" subtitle="完成以下事项即可解锁全部功能">
      <div class="todos">
        <div v-for="t in todos" :key="t.text" class="todo">
          <el-icon class="todo__icon"><WarningFilled /></el-icon>
          <span class="todo__text">{{ t.text }}</span>
          <el-button size="small" type="primary" plain @click="t.action()">{{ t.actionText }}</el-button>
        </div>
      </div>
    </AppCard>

    <div class="home__quick">
      <AppCard title="投资服务" subtitle="浏览标的、查看投资与回款">
        <div class="quick-grid">
          <div
            v-for="q in investEntries"
            :key="q.path"
            class="quick-item"
            @click="router.push(q.path)"
          >
            <span class="quick-icon"><el-icon><component :is="q.icon" /></el-icon></span>
            <span class="quick-text">{{ q.text }}</span>
          </div>
        </div>
      </AppCard>

      <AppCard title="借款服务" subtitle="认证、申请、查看借款与还款">
        <div class="quick-grid">
          <div
            v-for="q in borrowEntries"
            :key="q.path"
            class="quick-item"
            @click="router.push(q.path)"
          >
            <span class="quick-icon"><el-icon><component :is="q.icon" /></el-icon></span>
            <span class="quick-text">{{ q.text }}</span>
          </div>
        </div>
      </AppCard>
    </div>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.home__balance {
  text-align: right;
  color: #fff;
}
.home__balance-label {
  font-size: var(--font-xs);
  color: rgba(255, 255, 255, 0.85);
  margin-top: 2px;
}
.home__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
}
.home__quick {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}
@media (max-width: 1080px) {
  .home__stats,
  .home__quick {
    grid-template-columns: minmax(0, 1fr);
  }
}

.todos {
  display: flex;
  flex-direction: column;
}
.todo {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--border-light);
}
.todo:first-child {
  padding-top: 0;
}
.todo:last-child {
  padding-bottom: 0;
  border-bottom: none;
}
.todo__icon {
  color: var(--color-warning);
  font-size: 16px;
  flex-shrink: 0;
}
.todo__text {
  flex: 1;
  font-size: var(--font-sm);
  color: var(--text-regular);
  min-width: 0;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-2);
}
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-2);
  border-radius: var(--radius-base);
  cursor: pointer;
  transition: background var(--transition-fast), transform var(--transition-fast);
}
.quick-item:hover {
  background: var(--bg-hover);
  transform: translateY(-2px);
}
.quick-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-base);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20px;
  background: var(--gradient-brand);
  box-shadow: 0 8px 18px rgba(91, 134, 229, 0.28);
}
.quick-text {
  font-size: var(--font-sm);
  color: var(--text-regular);
}
</style>
