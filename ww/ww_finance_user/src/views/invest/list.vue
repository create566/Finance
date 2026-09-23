<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getLendList } from '@/api/lend'

const router = useRouter()
const tableData = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const data = await getLendList({ page: pageNum.value, limit: pageSize.value })
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

function goDetail(id: number) {
  router.push(`/invest/detail/${id}`)
}

const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '待发布', type: 'info' },
  1: { text: '募资中', type: 'primary' },
  2: { text: '已完成', type: 'success' },
  3: { text: '已逾期', type: 'danger' },
  4: { text: '已下架', type: 'info' },
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header"><h2>标的列表</h2></div>
    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe border>
          <el-table-column type="index" label="序号" width="70" align="center" />
          <el-table-column prop="title" label="标的名称" min-width="160" />
          <el-table-column prop="amount" label="借款金额" min-width="120" align="right">
            <template #default="{ row }">¥{{ row.amount?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="lendYearRate" label="年化利率" width="100" align="center">
            <template #default="{ row }">{{ row.lendYearRate }}%</template>
          </el-table-column>
          <el-table-column prop="period" label="期限(月)" width="90" align="center">
            <template #default="{ row }">{{ row.period }}个月</template>
          </el-table-column>
          <el-table-column prop="investAmount" label="已募金额" min-width="120" align="right">
            <template #default="{ row }">¥{{ row.investAmount?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="investNum" label="投资人数" width="90" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.status]?.type as string) || 'info'">
              {{ statusMap[row.status]?.text || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="goDetail(row.id)">投资</el-button>
          </template>
        </el-table-column>
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