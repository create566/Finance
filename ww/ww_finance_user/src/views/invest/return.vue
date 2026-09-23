<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyInvestList, getItemReturns } from '@/api/lendReturn'
import { getUserInfo } from '@/api/user'

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

const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '未回款', type: 'info' },
  1: { text: '已回款', type: 'success' },
  2: { text: '逾期', type: 'danger' },
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <div class="page-banner">
      <h2>回款计划</h2>
      <p>查看您作为投资人的各期回款计划与明细</p>
    </div>

    <el-alert
      v-if="userInfo && userInfo.userType !== 1"
      title="您当前不是投资人身份，回款数据可能为空"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="期次" width="70" align="center" />
        <el-table-column prop="id" label="回款ID" min-width="80" />
        <el-table-column prop="lendReturnId" label="还款计划ID" min-width="100" />
        <el-table-column prop="returnDate" label="回款日期" min-width="120" align="center" />
        <el-table-column prop="total" label="回款总额" min-width="120" align="right">
          <template #default="{ row }">¥{{ row.total?.toFixed(2) ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="principal" label="本金" min-width="120" align="right">
          <template #default="{ row }">¥{{ row.principal?.toFixed(2) ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="interest" label="利息" min-width="120" align="right">
          <template #default="{ row }">¥{{ row.interest?.toFixed(2) ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.status]?.type as string) || 'info'">
              {{ statusMap[row.status]?.text || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="showDetail(row.id)">明细</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" title="回款明细" width="700px">
      <el-table :data="detailData" v-loading="detailLoading" stripe border size="small">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="returnDate" label="回款日期" min-width="110" />
        <el-table-column prop="totalAmount" label="回款总额" min-width="100" align="right">
          <template #default="{ row }">¥{{ row.totalAmount?.toFixed(2) ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="principal" label="本金" min-width="100" align="right">
          <template #default="{ row }">¥{{ row.principal?.toFixed(2) ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="interest" label="利息" min-width="100" align="right">
          <template #default="{ row }">¥{{ row.interest?.toFixed(2) ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.status]?.type as string) || 'info'" size="small">
              {{ statusMap[row.status]?.text || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-banner {
  background: linear-gradient(135deg, #36d1dc 0%, #5b86e5 100%);
  padding: 20px 28px;
  border-radius: 8px;
  margin-bottom: 20px;
}
.page-banner h2 {
  font-size: 20px;
  font-weight: 600;
  color: #fff;
  margin: 0 0 4px;
}
.page-banner p {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
  margin: 0;
}
</style>