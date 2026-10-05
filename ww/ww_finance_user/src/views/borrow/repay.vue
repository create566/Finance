<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyBorrowList, repay, type MyBorrowItem } from '@/api/lendReturn'
import { getAccount } from '@/api/account'

const route = useRoute()
const router = useRouter()

const allRecords = ref<MyBorrowItem[]>([])
const loading = ref(false)
/** 可用余额（来自银行，平台不持有余额）—— 还款闸门的判据与后端一致 */
const balance = ref<number | null>(null)

/** 从"我的借款"跳过来时带的标的编号，只看这一笔 */
const lendId = computed(() => {
  const v = route.query.lendId
  return v ? Number(v) : null
})

const tableData = computed(() =>
  lendId.value === null ? allRecords.value : allRecords.value.filter((r) => r.lendId === lendId.value),
)

async function fetchData() {
  loading.value = true
  try {
    const data = await getMyBorrowList()
    allRecords.value = Array.isArray(data) ? data : []
  } catch {
  } finally {
    loading.value = false
  }
  try {
    const account = await getAccount()
    balance.value = account?.amount ?? 0
  } catch {}
}

/** 本期应还：逾期时含罚息 */
function payableOf(row: MyBorrowItem): number {
  const overdueTotal = row.overdueTotal
  return row.overdue === true && overdueTotal != null ? overdueTotal : row.total || 0
}

/** 余额够不够这一期 —— 不够就不让提交，与中国互金"余额不足不得发起扣款"的口径一致 */
function insufficient(row: MyBorrowItem): boolean {
  if (balance.value === null) return false
  return balance.value < payableOf(row)
}

async function handleRepay(row: MyBorrowItem) {
  if (insufficient(row)) {
    ElMessage.warning(
      `可用余额不足：本期需 ¥${payableOf(row).toFixed(2)}，当前可用 ¥${(balance.value ?? 0).toFixed(2)}，请先充值`,
    )
    return
  }
  ElMessageBox.confirm(
    `确认归还第 ${row.currentPeriod} 期，金额 ¥${payableOf(row).toFixed(2)}？将跳转银行完成扣款。`,
    '还款确认',
    { confirmButtonText: '确认还款', cancelButtonText: '取消', type: 'warning' },
  )
    .then(async () => {
      try {
        const result = await repay(row.id)
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
        // 拦截器已经提示过具体原因（余额不足等），这里不重复弹
      }
    })
    .catch(() => {})
}

const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '待还款', type: 'warning' },
  1: { text: '已还清', type: 'success' },
  2: { text: '已逾期', type: 'danger' },
}

onMounted(fetchData)
watch(() => route.query.lendId, fetchData)
</script>

<template>
  <div class="page-container">
    <div class="page-banner">
      <h2>还款计划</h2>
      <p>查看您的借款还款计划，按时还款维护良好信用</p>
    </div>

    <div class="toolbar">
      <div class="balance-box">
        <span class="label">可用余额</span>
        <span class="value">¥{{ (balance ?? 0).toFixed(2) }}</span>
      </div>
      <div class="toolbar-right">
        <el-tag v-if="lendId !== null" type="info" effect="plain" style="margin-right: 8px">
          标的编号 {{ lendId }}
        </el-tag>
        <el-button v-if="lendId !== null" link type="primary" @click="router.push('/borrow/my')">
          查看全部借款
        </el-button>
        <el-button v-if="lendId !== null" link type="primary" @click="router.push('/account/overview')">
          去充值
        </el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe border empty-text="暂无还款计划">
        <el-table-column prop="currentPeriod" label="期次" width="75" align="center" />
        <el-table-column prop="lendId" label="标的编号" width="100" align="center" />
        <el-table-column prop="returnDate" label="还款日期" min-width="120" align="center" />
        <el-table-column label="应还总额" min-width="120" align="right">
          <template #default="{ row }">¥{{ payableOf(row).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="本金" min-width="110" align="right">
          <template #default="{ row }">¥{{ row.principal?.toFixed(2) ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column label="利息" min-width="110" align="right">
          <template #default="{ row }">¥{{ row.interest?.toFixed(2) ?? '0.00' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.status]?.type as string) || 'info'" size="small">
              {{ statusMap[row.status]?.text || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="row.status !== 1">
              <el-tooltip
                v-if="insufficient(row)"
                content="可用余额不足，请先充值"
                placement="top"
              >
                <el-button type="primary" link disabled>还款</el-button>
              </el-tooltip>
              <el-button v-else type="primary" link @click="handleRepay(row)">还款</el-button>
            </template>
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
.page-banner h2 { font-size: 20px; font-weight: 600; color: #fff; margin: 0 0 4px; }
.page-banner p { font-size: 13px; color: rgba(255, 255, 255, 0.85); margin: 0; }
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}
.balance-box .label { font-size: 13px; color: #6b7280; margin-right: 8px; }
.balance-box .value { font-size: 18px; font-weight: 600; color: #1f2937; }
.toolbar-right { display: flex; align-items: center; }
</style>
