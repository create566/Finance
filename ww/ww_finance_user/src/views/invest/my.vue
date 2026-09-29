<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getMyInvestments } from '@/api/lend'

const tableData = ref<any[]>([])
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const result = await getMyInvestments()
    tableData.value = result?.list || []
  } catch {} finally {
    loading.value = false
  }
}

const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '募资中', type: 'warning' },
  1: { text: '持有中', type: 'primary' },
  2: { text: '满标待放款', type: 'success' },
  3: { text: '已回款', type: 'info' },
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header"><h2>我的投资</h2></div>
    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="lendId" label="标的ID" min-width="80" />
        <el-table-column prop="investAmount" label="投资金额" min-width="120" align="right">
          <template #default="{ row }">¥{{ row.investAmount?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="lendYearRate" label="年化利率" width="100" align="center">
          <template #default="{ row }">{{ row.lendYearRate }}%</template>
        </el-table-column>
        <el-table-column prop="expectAmount" label="预期收益" min-width="120" align="right">
          <template #default="{ row }">¥{{ row.expectAmount?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.status]?.type as string) || 'info'">
              {{ statusMap[row.status]?.text || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="投资时间" min-width="170" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 500; color: #1f2937; }
</style>