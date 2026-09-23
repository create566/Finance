<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getTransFlowList } from '@/api/account'

const tableData = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const data = await getTransFlowList({ page: pageNum.value, limit: pageSize.value })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch {} finally {
    loading.value = false
  }
}

function handlePageChange(p: number) {
  pageNum.value = p
  fetchList()
}

const transTypeMap: Record<number, string> = {
  1: '充值',
  2: '提现',
  3: '投资',
  4: '回款',
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header"><h2>资金流水</h2></div>
    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="transNo" label="交易号" min-width="180" />
        <el-table-column label="交易类型" width="100" align="center">
          <template #default="{ row }">{{ row.transTypeName || transTypeMap[row.transType] || '未知' }}</template>
        </el-table-column>
        <el-table-column prop="transAmount" label="交易金额" min-width="120" align="right">
          <template #default="{ row }">¥{{ row.transAmount?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="交易时间" min-width="170" />
        <el-table-column prop="memo" label="备注" min-width="120" />
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
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>