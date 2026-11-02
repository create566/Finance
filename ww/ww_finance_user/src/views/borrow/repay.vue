<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyBorrowList, repay, type MyBorrowItem } from '@/api/lendReturn'
import { getAccount } from '@/api/account'
import { openBankForm } from '@/utils/bankForm'
import AppCard from '@/components/common/AppCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import GradientBanner from '@/components/common/GradientBanner.vue'
import MetricRow, { type MetricItem } from '@/components/common/MetricRow.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const route = useRoute()
const router = useRouter()

const allRecords = ref<MyBorrowItem[]>([])
const loading = ref(false)
/** 可用余额（来自银行，平台不持有余额）—— 还款闸门的判据与后端一致 */
const balance = ref<number | null>(null)

/** 从"我的借款"跳过来时带的标的编号，只看这一笔 */
const lendId = computed(() => {
  const v = route.query.lendId
  return v ? Number(v) : null
})

const tableData = computed(() =>
  lendId.value === null ? allRecords.value : allRecords.value.filter((r) => r.lendId === lendId.value),
)

async function fetchData() {
  loading.value = true
  try {
    const data = await getMyBorrowList()
    allRecords.value = Array.isArray(data) ? data : []
  } catch {
  } finally {
    loading.value = false
  }
  try {
    const account = await getAccount()
    balance.value = account?.amount ?? 0
  } catch {}
}

/** 本期应还：逾期时含罚息 */
function payableOf(row: MyBorrowItem): number {
  const overdueTotal = row.overdueTotal
  return row.overdue === true && overdueTotal != null ? overdueTotal : row.total || 0
}

/** 余额够不够这一期 —— 不够就不让提交，与中国互金"余额不足不得发起扣款"的口径一致 */
function insufficient(row: MyBorrowItem): boolean {
  if (balance.value === null) return false
  return balance.value < payableOf(row)
}

const metrics = computed<MetricItem[]>(() => {
  const unpaid = tableData.value.filter((r) => r.status !== 1)
  return [
    {
      label: '待还总额',
      value: unpaid.reduce((s, r) => s + payableOf(r), 0),
      tone: 'danger',
    },
    { label: '待还期数', text: String(unpaid.length), unit: ' 期' },
    {
      label: '已还清期数',
      text: String(tableData.value.filter((r) => r.status === 1).length),
      unit: ' 期',
    },
    {
      label: '逾期期数',
      text: String(tableData.value.filter((r) => r.status === 2).length),
      unit: ' 期',
    },
  ]
})

async function handleRepay(row: MyBorrowItem) {
  if (insufficient(row)) {
    ElMessage.warning(
      `可用余额不足：本期需 ¥${payableOf(row).toFixed(2)}，当前可用 ¥${(balance.value ?? 0).toFixed(2)}，请先充值`,
    )
    return
  }
  ElMessageBox.confirm(
    `确认归还第 ${row.currentPeriod} 期，金额 ¥${payableOf(row).toFixed(2)}？将在新窗口打开银行收银台完成扣款。`,
    '还款确认',
    { confirmButtonText: '确认还款', cancelButtonText: '取消', type: 'warning' },
  )
    .then(async () => {
      try {
        const result = await repay(row.id)
        // 银行扣款表单开新窗口提交：本页留在还款计划、登录态不丢，
        // 用户付完关掉新窗口后自动刷新本页（而不是整页跳走后掉到登录页）。
        const openResult = openBankForm(result, { onClosed: fetchData })
        if (openResult === 'opened' || openResult === 'blocked') {
          return
        }
        ElMessage.success('还款请求已提交')
        fetchData()
      } catch {
        // 拦截器已经提示过具体原因（余额不足等），这里不重复弹
      }
    })
    .catch(() => {})
}

onMounted(fetchData)
watch(() => route.query.lendId, fetchData)
</script>

<template>
  <div class="rp">
    <GradientBanner title="还款计划" desc="查看您的借款还款计划，按时还款维护良好信用">
      <div class="rp__balance">
        <div class="rp__balance-value"><MoneyText :value="balance ?? 0" size="lg" strong /></div>
        <div class="rp__balance-label">可用余额（存管银行）</div>
      </div>
    </GradientBanner>

    <div class="rp__toolbar">
      <div class="rp__toolbar-left">
        <template v-if="lendId !== null">
          <el-tag type="info" effect="plain" size="small">标的编号 {{ lendId }}</el-tag>
          <span class="rp__toolbar-hint">仅显示该标的的还款计划</span>
        </template>
        <span v-else class="rp__toolbar-hint">显示全部借款的还款计划</span>
      </div>
      <div class="rp__toolbar-right">
        <el-button v-if="lendId !== null" link type="primary" @click="router.push('/borrow/my')">
          查看全部借款
        </el-button>
        <el-button link type="primary" @click="router.push('/account/overview')">去充值</el-button>
      </div>
    </div>

    <MetricRow :items="metrics" class="rp__metrics" />

    <AppCard body-flush>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="currentPeriod" label="期次" width="75" align="center" />
        <el-table-column prop="lendId" label="标的编号" width="100" align="center" />
        <el-table-column prop="returnDate" label="还款日期" min-width="120" align="center" />
        <el-table-column label="应还总额" min-width="120" align="right">
          <template #default="{ row }">
            <span class="money"><MoneyText :value="payableOf(row)" /></span>
          </template>
        </el-table-column>
        <el-table-column label="本金" min-width="110" align="right">
          <template #default="{ row }"><MoneyText :value="row.principal" /></template>
        </el-table-column>
        <el-table-column label="利息" min-width="110" align="right">
          <template #default="{ row }"><MoneyText :value="row.interest" /></template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" kind="repay" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="row.status !== 1">
              <el-tooltip
                v-if="insufficient(row)"
                content="可用余额不足，请先充值"
                placement="top"
              >
                <el-button type="primary" link disabled>还款</el-button>
              </el-tooltip>
              <el-button v-else type="primary" link @click="handleRepay(row)">还款</el-button>
            </template>
            <span v-else class="rp__done">—</span>
          </template>
        </el-table-column>

        <template #empty>
          <AppEmpty description="暂无还款计划" />
        </template>
      </el-table>
    </AppCard>
  </div>
</template>

<style scoped>
.rp {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.rp__balance {
  text-align: right;
  color: #fff;
}
.rp__balance-label {
  font-size: var(--font-xs);
  color: rgba(255, 255, 255, 0.85);
  margin-top: 2px;
}
.rp__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
}
.rp__toolbar-left {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}
.rp__toolbar-hint {
  font-size: var(--font-xs);
  color: var(--text-placeholder);
}
.rp__toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}
.rp__done {
  color: var(--text-disabled);
  font-size: var(--font-sm);
}
</style>
