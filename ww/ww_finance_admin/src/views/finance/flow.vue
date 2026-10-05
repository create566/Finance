<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  getTransFlowPage,
  TRANS_TYPE_META,
  TRANS_STATUS_META,
  type TransFlowRow,
} from '@/api/transFlow'

const tableData = ref<TransFlowRow[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

const query = ref<{ userName: string; transType: number | null; userId: number | null }>({
  userName: '',
  transType: null,
  userId: null,
})

async function fetchList() {
  loading.value = true
  try {
    const data = await getTransFlowPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      userName: query.value.userName,
      transType: query.value.transType,
      userId: query.value.userId,
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch {
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  fetchList()
}

function handleReset() {
  query.value = { userName: '', transType: null, userId: null }
  handleSearch()
}

function handlePageChange(p: number) {
  pageNum.value = p
  fetchList()
}

function typeText(row: TransFlowRow): string {
  return row.transTypeName || TRANS_TYPE_META[row.transType]?.text || '未知'
}

/** 本页收入/支出合计。全量合计需要后端聚合接口，这里只对当前页做口径说明，避免误读 */
const pageIncome = computed(() =>
  tableData.value
    .filter((r) => TRANS_TYPE_META[r.transType]?.direction === 1)
    .reduce((s, r) => s + (r.transAmount || 0), 0),
)
const pageExpense = computed(() =>
  tableData.value
    .filter((r) => TRANS_TYPE_META[r.transType]?.direction === -1)
    .reduce((s, r) => s + (r.transAmount || 0), 0),
)

const typeOptions = Object.entries(TRANS_TYPE_META).map(([code, meta]) => ({
  value: Number(code),
  label: meta.text,
}))

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header"><h2>资金流水监控</h2></div>

    <el-card shadow="never" style="margin-bottom: 16px">
      <div class="filter-bar">
        <el-input
          v-model="query.userName"
          placeholder="用户名 / 手机号"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
        <el-input-number
          v-model="query.userId"
          placeholder="用户ID"
          :controls="false"
          style="width: 140px"
        />
        <el-select v-model="query.transType" placeholder="全部交易类型" clearable style="width: 170px">
          <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="summary">
          <span>本页收入 <b class="income">¥{{ pageIncome.toFixed(2) }}</b></span>
          <span>本页支出 <b class="expense">¥{{ pageExpense.toFixed(2) }}</b></span>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe border empty-text="暂无流水记录">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="transNo" label="交易号" min-width="190" show-overflow-tooltip />
        <el-table-column prop="userId" label="用户ID" width="85" align="center" />
        <el-table-column prop="userName" label="用户" min-width="120" show-overflow-tooltip />
        <el-table-column label="交易类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag
              :type="(TRANS_TYPE_META[row.transType]?.tag as string) || 'info'"
              size="small"
              effect="plain"
            >
              {{ typeText(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" min-width="130" align="right">
          <template #default="{ row }">
            <span :class="TRANS_TYPE_META[row.transType]?.direction === 1 ? 'income' : 'expense'">
              {{ TRANS_TYPE_META[row.transType]?.direction === 1 ? '+' : '-' }}¥{{
                (row.transAmount ?? 0).toFixed(2)
              }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95" align="center">
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
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 500; color: #1f2937; }
.filter-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.summary { margin-left: auto; display: flex; gap: 20px; font-size: 13px; color: #6b7280; }
.income { color: #cf1322; }
.expense { color: #389e0d; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
