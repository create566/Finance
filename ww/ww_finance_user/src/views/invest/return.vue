<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyInvestList, getItemReturns } from '@/api/lendReturn'
import { getUserInfo } from '@/api/user'
import AppCard from '@/components/common/AppCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import GradientBanner from '@/components/common/GradientBanner.vue'
import MetricRow, { type MetricItem } from '@/components/common/MetricRow.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const tableData = ref<any[]>([])
const loading = ref(false)
const detailVisible = ref(false)
const detailData = ref<any[]>([])
const detailLoading = ref(false)
const userInfo = ref<any>(null)

async function fetchData() {
  loading.value = true
  try {
    const data = await getMyInvestList()
    tableData.value = Array.isArray(data) ? data : []
  } catch {} finally {
    loading.value = false
  }
  try {
    userInfo.value = await getUserInfo()
  } catch {}
}

async function showDetail(lendReturnId: number) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const data = await getItemReturns(lendReturnId)
    detailData.value = Array.isArray(data) ? data : []
  } catch {
    ElMessage.error('获取回款明细失败')
  } finally {
    detailLoading.value = false
  }
}

function sumOf(field: string, onlyUnpaid = false): number {
  return tableData.value
    .filter((row) => (onlyUnpaid ? row?.status !== 1 : true))
    .reduce((acc, row) => acc + Number(row?.[field] || 0), 0)
}

const paidCount = computed(() => tableData.value.filter((row) => row?.status === 1).length)

const metrics = computed<MetricItem[]>(() => [
  { label: '计划回款总额', value: sumOf('total'), tone: 'primary' },
  { label: '本金合计', value: sumOf('principal') },
  { label: '利息合计', value: sumOf('interest') },
  { label: '待回款总额', value: sumOf('total', true), tone: 'danger' },
])

onMounted(fetchData)
</script>

<template>
  <div class="rp">
    <GradientBanner
      title="回款计划"
      desc="查看您作为投资人的各期回款计划与明细"
    >
      <div class="rp__summary">
        <div class="rp__summary-value">{{ paidCount }} / {{ tableData.length }}</div>
        <div class="rp__summary-label">已回款期数</div>
      </div>
    </GradientBanner>

    <div v-if="userInfo && userInfo.userType !== 1" class="rp__notice">
      <el-icon class="rp__notice-icon"><InfoFilled /></el-icon>
      <span>您当前不是投资人身份，回款数据可能为空。</span>
    </div>

    <MetricRow :items="metrics" />

    <AppCard body-flush>
      <el-table :data="tableData" v-loading="loading" stripe>
        <!-- 后端 listMyItemReturns 按 currentPeriod 倒序返回，行号 ≠ 期次，
             用 type="index" 会把第 12 期显示成「1」。 -->
        <el-table-column prop="currentPeriod" label="期次" width="70" align="center" />
        <el-table-column prop="id" label="回款ID" min-width="80" />
        <el-table-column prop="lendReturnId" label="还款计划ID" min-width="100" />
        <el-table-column prop="returnDate" label="回款日期" min-width="120" align="center" />
        <el-table-column prop="total" label="回款总额" min-width="120" align="right">
          <template #default="{ row }"><MoneyText :value="row.total" /></template>
        </el-table-column>
        <el-table-column prop="principal" label="本金" min-width="120" align="right">
          <template #default="{ row }"><MoneyText :value="row.principal" /></template>
        </el-table-column>
        <el-table-column prop="interest" label="利息" min-width="120" align="right">
          <template #default="{ row }"><MoneyText :value="row.interest" colored /></template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" kind="investReturn" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <!-- 必须传 lendReturnId（lend_return.id），不能传 id：
                 本表格的数据源是 myInvestList，返回的是 lend_item_return 记录，
                 其 id 是明细ID，去查 lend_return 表必然查不到 → 后端返回「还款计划不存在」。 -->
            <el-button type="primary" link @click="showDetail(row.lendReturnId)">明细</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <AppEmpty description="暂无回款计划" />
        </template>
      </el-table>
    </AppCard>

    <el-dialog v-model="detailVisible" title="回款明细" width="700px">
      <el-table :data="detailData" v-loading="detailLoading" stripe size="small">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="returnDate" label="回款日期" min-width="110" />
        <!-- 字段名是 total（LendItemReturn.total），没有 totalAmount。
             写成 totalAmount 会让这一列恒显示 ¥0.00。 -->
        <el-table-column prop="total" label="回款总额" min-width="100" align="right">
          <template #default="{ row }"><MoneyText :value="row.total" /></template>
        </el-table-column>
        <el-table-column prop="principal" label="本金" min-width="100" align="right">
          <template #default="{ row }"><MoneyText :value="row.principal" /></template>
        </el-table-column>
        <el-table-column prop="interest" label="利息" min-width="100" align="right">
          <template #default="{ row }"><MoneyText :value="row.interest" colored /></template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.status" kind="investReturn" />
          </template>
        </el-table-column>

        <template #empty>
          <AppEmpty description="暂无明细" size="sm" />
        </template>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.rp {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.rp__summary {
  text-align: right;
  color: #fff;
}
.rp__summary-value {
  font-size: var(--font-xl);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.rp__summary-label {
  font-size: var(--font-xs);
  color: rgba(255, 255, 255, 0.85);
  margin-top: 2px;
}
.rp__notice {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-base);
  background: var(--color-info-bg);
  border: 1px solid var(--color-info-border);
  font-size: var(--font-sm);
  color: var(--text-regular);
}
.rp__notice-icon {
  color: var(--color-info);
}
</style>
