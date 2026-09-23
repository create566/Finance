<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyBorrowList, repay } from '@/api/lendReturn'

const tableData = ref<any[]>([])
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const data = await getMyBorrowList()
    tableData.value = Array.isArray(data) ? data : []
  } catch {} finally {
    loading.value = false
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
  1: { text: '还款中', type: 'primary' },
  2: { text: '已还清', type: 'success' },
  3: { text: '逾期', type: 'danger' },
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header"><h2>我的借款</h2></div>
    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="id" label="借款ID" min-width="80" />
        <el-table-column prop="lendId" label="标的ID" min-width="80" />
        <el-table-column prop="amount" label="借款金额" min-width="120" align="right">
          <template #default="{ row }">¥{{ row.amount?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="lendYearRate" label="年化利率" width="100" align="center">
          <template #default="{ row }">{{ row.lendYearRate }}%</template>
        </el-table-column>
        <el-table-column prop="returnMethod" label="还款方式" min-width="120">
          <template #default="{ row }">
            {{ row.returnMethod === 1 ? '等额本息' : row.returnMethod === 0 ? '等额本金' : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.status]?.type as string) || 'info'">
              {{ statusMap[row.status]?.text || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="申请时间" min-width="170" />
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0 || row.status === 1"
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