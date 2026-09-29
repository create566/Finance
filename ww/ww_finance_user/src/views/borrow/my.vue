<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyBorrowList, repay } from '@/api/lendReturn'
import { getBorrowInfoList, type BorrowInfoItem } from '@/api/borrowInfo'

const tableData = ref<any[]>([])
const loading = ref(false)
const applyList = ref<BorrowInfoItem[]>([])
const applyLoading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const data = await getMyBorrowList()
    tableData.value = Array.isArray(data) ? data : []
  } catch {} finally {
    loading.value = false
  }
}

async function fetchApplyList() {
  applyLoading.value = true
  try {
    const data = await getBorrowInfoList()
    applyList.value = Array.isArray(data) ? data : []
  } catch {} finally {
    applyLoading.value = false
  }
}

function handleRepay(id: number) {
  ElMessageBox.confirm('确认进行还款操作？', '提示', { type: 'warning' })
    .then(async () => {
      await repay(id)
      ElMessage.success('还款成功')
      fetchList()
    })
    .catch(() => {})
}

const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '待还款', type: 'warning' },
  1: { text: '已还清', type: 'success' },
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
      <template #header><span style="font-weight:600">借款申请记录</span></template>
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

    <el-card shadow="never">
      <template #header><span style="font-weight:600">还款计划</span></template>
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="currentPeriod" label="期次" width="80" align="center" />
        <el-table-column prop="lendId" label="标的ID" min-width="80" />
        <el-table-column prop="returnDate" label="还款日期" min-width="120" align="center" />
        <el-table-column prop="total" label="应还总额" min-width="120" align="right">
          <template #default="{ row }">¥{{ row.total?.toFixed(2) ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="principal" label="本金" min-width="110" align="right">
          <template #default="{ row }">¥{{ row.principal?.toFixed(2) ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="interest" label="利息" min-width="110" align="right">
          <template #default="{ row }">¥{{ row.interest?.toFixed(2) ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.status]?.type as string) || 'info'">
              {{ statusMap[row.status]?.text || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              type="primary"
              link
              @click="handleRepay(row.id)"
            >还款</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 500; color: #1f2937; }
</style>