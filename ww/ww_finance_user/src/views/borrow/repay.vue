<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyBorrowList, repay } from '@/api/lendReturn'
import { getUserInfo } from '@/api/user'

const tableData = ref<any[]>([])
const loading = ref(false)
const userInfo = ref<any>(null)

async function fetchData() {
  loading.value = true
  try {
    const data = await getMyBorrowList()
    tableData.value = Array.isArray(data) ? data : []
  } catch {} finally {
    loading.value = false
  }
  try {
    userInfo.value = await getUserInfo()
  } catch {}
}

function handleRepay(id: number) {
  ElMessageBox.confirm('确认进行还款操作？将跳转银行完成扣款。', '还款确认', {
    confirmButtonText: '确认还款',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      try {
        const result = await repay(id)
        if (typeof result === 'string' && result.includes('<form')) {
          const div = document.createElement('div')
          div.innerHTML = result
          document.body.appendChild(div)
          const form = div.querySelector('form') as HTMLFormElement
          if (form) {
            form.submit()
            return
          }
        }
        ElMessage.success('还款请求已提交')
        fetchData()
      } catch {
        ElMessage.error('还款操作失败')
      }
    })
    .catch(() => {})
}

const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '待还款', type: 'warning' },
  1: { text: '已还清', type: 'success' },
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container">
    <div class="page-banner">
      <h2>还款计划</h2>
      <p>查看您的借款还款计划，按时还款维护良好信用</p>
    </div>

    <el-alert
      v-if="userInfo && userInfo.userType !== 2"
      title="您当前不是借款人身份，还款数据可能为空"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="期次" width="70" align="center" />
        <el-table-column prop="id" label="还款ID" min-width="80" />
        <el-table-column prop="lendId" label="标的ID" min-width="80" />
        <el-table-column prop="returnDate" label="还款日期" min-width="120" align="center" />
        <el-table-column prop="total" label="还款总额" min-width="120" align="right">
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
            <el-button
              v-if="row.status === 0"
              type="primary"
              link
              @click="handleRepay(row.id)"
            >还款</el-button>
            <span v-else style="color: #9ca3af; font-size: 13px">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-banner {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
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