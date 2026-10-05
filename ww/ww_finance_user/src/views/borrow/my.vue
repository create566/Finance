<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyBorrowList, type MyBorrowItem } from '@/api/lendReturn'
import { getBorrowInfoList, type BorrowInfoItem } from '@/api/borrowInfo'

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

/** 【还款】跳转到还款计划页，并带上标的下标让页面只显示这一笔 */
function goRepayPlan(lendId: number) {
  router.push({ path: '/borrow/repay', query: { lendId: String(lendId) } })
}

const loanStatusMap: Record<LoanGroup['status'], { text: string; type: string }> = {
  repaying: { text: '还款中', type: 'warning' },
  settled: { text: '已结清', type: 'success' },
  overdue: { text: '已逾期', type: 'danger' },
}

/** el-table 插槽里的 row 是 any，直接索引 Record 联合类型会报 TS7053，统一从这里取 */
function loanStatusMeta(status: LoanGroup['status']) {
  return loanStatusMap[status] || { text: '未知', type: 'info' }
}

const applyStatusMap: Record<number, { text: string; type: string }> = {
  0: { text: '草稿', type: 'info' },
  1: { text: '审核中', type: 'warning' },
  2: { text: '审核通过', type: 'success' },
  [-1]: { text: '审核拒绝', type: 'danger' },
}

const returnMethodMap: Record<number, string> = {
  1: '等额本息',
  2: '等额本金',
  3: '每月还息一次还本',
  4: '一次还本付息',
}

onMounted(() => {
  fetchList()
  fetchApplyList()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header"><h2>我的借款</h2></div>

    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header><span style="font-weight: 600">借款标的</span></template>
      <el-table :data="loanGroups" v-loading="loading" stripe border empty-text="暂无借款标的">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="lendId" label="标的编号" width="100" align="center" />
        <el-table-column label="还款进度" min-width="150" align="center">
          <template #default="{ row }">
            {{ row.paidPeriods }} / {{ row.totalPeriods }} 期
          </template>
        </el-table-column>
        <el-table-column label="剩余应还" min-width="140" align="right">
          <template #default="{ row }">¥{{ row.remainTotal.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="nextReturnDate" label="下期还款日" min-width="130" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="loanStatusMeta(row.status).type" size="small">
              {{ loanStatusMeta(row.status).text }}
            </el-tag>
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
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header><span style="font-weight: 600">借款申请记录</span></template>
      <el-table :data="applyList" v-loading="applyLoading" stripe border empty-text="暂无借款申请记录">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="id" label="申请编号" min-width="90" />
        <el-table-column prop="amount" label="申请金额" min-width="120" align="right">
          <template #default="{ row }">¥{{ row.amount?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="period" label="期限(月)" width="90" align="center" />
        <el-table-column prop="returnMethod" label="还款方式" min-width="120">
          <template #default="{ row }">{{ returnMethodMap[row.returnMethod] || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(applyStatusMap[row.status]?.type as string) || 'info'" size="small">
              {{ applyStatusMap[row.status]?.text || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="申请时间" min-width="170" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 500; color: #1f2937; }
</style>
