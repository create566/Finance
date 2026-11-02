<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyBorrowList, type MyBorrowItem } from '@/api/lendReturn'
import { getBorrowInfoList, type BorrowInfoItem } from '@/api/borrowInfo'
import { returnMethodText } from '@/utils/enumText'
import AppCard from '@/components/common/AppCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import AppPage from '@/components/common/AppPage.vue'
import MetricRow, { type MetricItem } from '@/components/common/MetricRow.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const router = useRouter()

const records = ref<MyBorrowItem[]>([])
const loading = ref(false)
const applyList = ref<BorrowInfoItem[]>([])
const applyLoading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const data = await getMyBorrowList()
    records.value = Array.isArray(data) ? data : []
  } catch {
  } finally {
    loading.value = false
  }
}

async function fetchApplyList() {
  applyLoading.value = true
  try {
    const data = await getBorrowInfoList()
    applyList.value = Array.isArray(data) ? data : []
  } catch {
  } finally {
    applyLoading.value = false
  }
}

/**
 * 我的借款按标的聚合。
 * 借款是以"标的"为单位的一笔债务，还款计划是它的分期明细 ——
 * 所以这里只列标的，点【还款】跳到还款计划页看这一笔的每一期。
 */
interface LoanGroup {
  lendId: number
  totalPeriods: number
  paidPeriods: number
  remainTotal: number
  nextReturnDate: string
  status: 'repaying' | 'settled' | 'overdue'
}

const loanGroups = computed<LoanGroup[]>(() => {
  const map = new Map<number, MyBorrowItem[]>()
  for (const r of records.value) {
    const list = map.get(r.lendId) || []
    list.push(r)
    map.set(r.lendId, list)
  }
  const groups: LoanGroup[] = []
  for (const [lendId, list] of map) {
    const sorted = [...list].sort((a, b) => a.currentPeriod - b.currentPeriod)
    const unpaid = sorted.filter((r) => r.status !== 1)
    const overdue = unpaid.some((r) => r.status === 2)
    groups.push({
      lendId,
      totalPeriods: sorted.length,
      paidPeriods: sorted.length - unpaid.length,
      remainTotal: unpaid.reduce((sum, r) => sum + (r.total || 0), 0),
      nextReturnDate: unpaid[0]?.returnDate || '-',
      status: overdue ? 'overdue' : unpaid.length === 0 ? 'settled' : 'repaying',
    })
  }
  return groups.sort((a, b) => b.lendId - a.lendId)
})

const metrics = computed<MetricItem[]>(() => {
  const paid = loanGroups.value.reduce((s, g) => s + g.paidPeriods, 0)
  const total = loanGroups.value.reduce((s, g) => s + g.totalPeriods, 0)
  return [
    { label: '借款标的', text: String(loanGroups.value.length), unit: ' 笔' },
    {
      label: '剩余应还',
      value: loanGroups.value.reduce((s, g) => s + g.remainTotal, 0),
      tone: 'danger',
    },
    { label: '还款进度', text: `${paid} / ${total}`, unit: ' 期' },
    {
      label: '逾期标的',
      text: String(loanGroups.value.filter((g) => g.status === 'overdue').length),
      unit: ' 笔',
    },
  ]
})

/** 【还款】跳转到还款计划页，并带上标的下标让页面只显示这一笔 */
function goRepayPlan(lendId: number) {
  router.push({ path: '/borrow/repay', query: { lendId: String(lendId) } })
}

onMounted(() => {
  fetchList()
  fetchApplyList()
})
</script>

<template>
  <AppPage title="我的借款" subtitle="按标的聚合的借款债务与还款进度">
    <MetricRow :items="metrics" class="my-borrow__metrics" />

    <AppCard title="借款标的" subtitle="每笔借款对应一个募资标的" body-flush class="my-borrow__block">
      <el-table :data="loanGroups" v-loading="loading" stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="lendId" label="标的编号" width="100" align="center" />
        <el-table-column label="还款进度" min-width="150" align="center">
          <template #default="{ row }">
            {{ row.paidPeriods }} / {{ row.totalPeriods }} 期
          </template>
        </el-table-column>
        <el-table-column label="剩余应还" min-width="140" align="right">
          <template #default="{ row }">
            <span class="money"><MoneyText :value="row.remainTotal" /></span>
          </template>
        </el-table-column>
        <el-table-column prop="nextReturnDate" label="下期还款日" min-width="130" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" kind="loanGroup" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'settled'"
              type="primary"
              link
              @click="goRepayPlan(row.lendId)"
            >还款</el-button>
            <el-button v-else type="info" link @click="goRepayPlan(row.lendId)">查看计划</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <AppEmpty description="暂无借款标的" />
        </template>
      </el-table>
    </AppCard>

    <AppCard title="借款申请记录" subtitle="申请审核状态与要素" body-flush>
      <el-table :data="applyList" v-loading="applyLoading" stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="id" label="申请编号" min-width="90" />
        <el-table-column prop="amount" label="申请金额" min-width="120" align="right">
          <template #default="{ row }"><MoneyText :value="row.amount" /></template>
        </el-table-column>
        <el-table-column prop="period" label="期限(月)" width="90" align="center" />
        <el-table-column prop="returnMethod" label="还款方式" min-width="140">
          <template #default="{ row }">{{ returnMethodText(row.returnMethod) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" kind="borrowApply" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="申请时间" min-width="170" />

        <template #empty>
          <AppEmpty description="暂无借款申请记录" />
        </template>
      </el-table>
    </AppCard>
  </AppPage>
</template>

<style scoped>
.my-borrow__metrics {
  margin-bottom: var(--space-4);
}
.my-borrow__block {
  margin-bottom: var(--space-4);
}
</style>
