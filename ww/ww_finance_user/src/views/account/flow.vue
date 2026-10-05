<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  getTransFlowList,
  TRANS_TYPE_META,
  TRANS_STATUS_META,
  transTypeText,
  type TransFlowItem,
} from '@/api/account'

const tableData = ref<TransFlowItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
/** 类型筛选：不传=全部；1充值 2提现 3投标 4投资回款 5放款 6还款 */
const filterType = ref<number | undefined>(undefined)

async function fetchList() {
  loading.value = true
  try {
    const data = await getTransFlowList({
      page: pageNum.value,
      limit: pageSize.value,
      transType: filterType.value,
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch {
  } finally {
    loading.value = false
  }
}

function handleTypeChange() {
  pageNum.value = 1
  fetchList()
}

function handlePageChange(p: number) {
  pageNum.value = p
  fetchList()
}

/** 收/支展示：流水表里金额一律存正数，收付方向由类型决定 */
function signedAmount(row: TransFlowItem): string {
  const meta = TRANS_TYPE_META[row.transType]
  const sign = meta?.direction === 1 ? '+' : '-'
  return `${sign}¥${(row.transAmount ?? 0).toFixed(2)}`
}

function amountClass(row: TransFlowItem): string {
  return TRANS_TYPE_META[row.transType]?.direction === 1 ? 'income' : 'expense'
}

/** 筛选下拉：只列真实存在的业务类型 */
const typeOptions = computed(() =>
  Object.entries(TRANS_TYPE_META).map(([code, meta]) => ({
    value: Number(code),
    label: meta.text,
  })),
)

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>资金流水</h2>
      <el-select
        v-model="filterType"
        placeholder="全部交易类型"
        clearable
        style="width: 180px"
        @change="handleTypeChange"
      >
        <el-option
          v-for="opt in typeOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </div>

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe border empty-text="暂无资金流水">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="transNo" label="交易号" min-width="180" show-overflow-tooltip />
        <el-table-column label="交易类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag
              :type="(TRANS_TYPE_META[row.transType]?.tag as string) || 'info'"
              size="small"
              effect="plain"
            >
              {{ transTypeText(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="交易金额" min-width="130" align="right">
          <template #default="{ row }">
            <span :class="amountClass(row)">{{ signedAmount(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(TRANS_STATUS_META[row.status]?.tag as string) || 'info'" size="small">
              {{ TRANS_STATUS_META[row.status]?.text || '成功' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="交易时间" min-width="170" />
        <el-table-column prop="memo" label="备注" min-width="180" show-overflow-tooltip />
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          background
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 { font-size: 18px; font-weight: 500; color: #1f2937; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.income { color: #cf1322; font-weight: 600; }
.expense { color: #389e0d; font-weight: 600; }
</style>
