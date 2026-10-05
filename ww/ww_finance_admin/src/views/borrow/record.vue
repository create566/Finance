<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBorrowRecordList, auditBorrowRecord, type BorrowRecordQuery } from '@/api/borrowRecord'
import type { BorrowRecord } from '@/api/borrowRecord'

// ------ 搜索 ------
const searchForm = reactive<BorrowRecordQuery>({
  keyword: '',
  status: undefined,
})

// ------ 列表 ------
const tableData = ref<BorrowRecord[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

async function fetchList() {
  loading.value = true
  try {
    const data = await getBorrowRecordList({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: searchForm.keyword,
      status: searchForm.status,
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  fetchList()
}
function handleReset() {
  searchForm.keyword = ''
  searchForm.status = undefined
  handleSearch()
}
function handlePageChange(p: number) {
  pageNum.value = p
  fetchList()
}
function handleSizeChange(s: number) {
  pageSize.value = s
  pageNum.value = 1
  fetchList()
}

// ------ 审核 ------
function handleApprove(row: any) {
  ElMessageBox.confirm(`确定通过该借款申请吗？`, '审核通过', {
    confirmButtonText: '确认通过',
    cancelButtonText: '取消',
    type: 'success',
  })
    .then(async () => {
      await auditBorrowRecord(row.id, 2)
      ElMessage.success('已通过')
      fetchList()
    })
    .catch(() => {})
}

function handleReject(row: any) {
  ElMessageBox.confirm(`确定拒绝该借款申请吗？`, '审核拒绝', {
    confirmButtonText: '确认拒绝',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      await auditBorrowRecord(row.id, -1)
      ElMessage.success('已拒绝')
      fetchList()
    })
    .catch(() => {})
}

// ------ 详情弹窗 ------
const detailVisible = ref(false)
const detailData = ref<any>(null)

function openDetail(row: any) {
  detailData.value = row
  detailVisible.value = true
}

// ------ 工具 ------
const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '草稿', type: 'info' },
  1: { text: '审核中', type: 'warning' },
  2: { text: '已通过', type: 'success' },
  [-1]: { text: '已拒绝', type: 'danger' },
}

// ------ 生命周期 ------
onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>借款列表</h2>
    </div>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="搜索"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" :value="0" />
            <el-option label="审核中" :value="1" />
            <el-option label="已通过" :value="2" />
            <el-option label="已拒绝" :value="-1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="page-card">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="userId" label="用户ID" min-width="80" />
        <el-table-column label="借款金额" min-width="130" align="center">
          <template #default="{ row }">
            <span class="money">¥{{ row.amount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="期限" width="80" align="center">
          <template #default="{ row }">{{ row.period }} 月</template>
        </el-table-column>
        <el-table-column label="年化利率" width="100" align="center">
          <template #default="{ row }">{{ row.borrowYearRate }}%</template>
        </el-table-column>
        <el-table-column label="还款方式" width="100" align="center" prop="returnMethod" />
        <el-table-column label="资金用途" width="100" align="center" prop="moneyUse" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="(statusMap[row.status]?.type as string) || 'info'">
              {{ statusMap[row.status]?.text || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核留痕" width="160" align="center">
          <template #default="{ row }">
            <div v-if="row.checkTime || row.checkAdminName" class="audit-cell">
              <div>{{ row.checkAdminName || '—' }}</div>
              <div class="audit-time">{{ row.checkTime || '—' }}</div>
            </div>
            <span v-else class="audit-none">未审核</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 1" type="success" link @click="handleApprove(row)">通过</el-button>
            <el-button v-if="row.status === 1" type="danger" link @click="handleReject(row)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="借款详情" width="580px">
      <el-descriptions v-if="detailData" :column="2" border>
        <el-descriptions-item label="ID">{{ detailData.id }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ detailData.userId }}</el-descriptions-item>
        <el-descriptions-item label="借款金额">
          <span class="money">¥{{ detailData.amount }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="借款期限">{{ detailData.period }} 月</el-descriptions-item>
        <el-descriptions-item label="年化利率">{{ detailData.borrowYearRate }}%</el-descriptions-item>
        <el-descriptions-item label="还款方式">{{ detailData.returnMethod }}</el-descriptions-item>
        <el-descriptions-item label="资金用途">{{ detailData.moneyUse }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="(statusMap[detailData.status]?.type as string) || 'info'">
            {{ statusMap[detailData.status]?.text || '未知' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detailData.checkAdminName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">{{ detailData.checkTime || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailData.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailData.updateTime }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  width: 100%;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  font-size: 18px;
  font-weight: 500;
  color: #1f2937;
}
.search-card {
  margin-bottom: 16px;
  border-radius: 6px;
}
.search-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.page-card {
  border-radius: 6px;
}
.money {
  color: #f56c6c;
  font-weight: 600;
}
.audit-cell {
  line-height: 1.5;
}
.audit-time {
  font-size: 12px;
  color: #909399;
}
.audit-none {
  font-size: 12px;
  color: #c0c4cc;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
