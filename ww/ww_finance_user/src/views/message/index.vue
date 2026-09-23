<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyMessages, readMessage, getUnreadCount } from '@/api/message'

const tableData = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const unreadCount = ref(0)

async function fetchList() {
  loading.value = true
  try {
    const data = await getMyMessages({ page: pageNum.value, limit: pageSize.value })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch {} finally {
    loading.value = false
  }
}

async function fetchUnread() {
  try {
    unreadCount.value = await getUnreadCount()
  } catch {}
}

async function handleRead(row: any) {
  if (row.isRead === 0) {
    try {
      await readMessage(row.id)
      row.isRead = 1
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch {}
  }
}

function handlePageChange(p: number) {
  pageNum.value = p
  fetchList()
}

onMounted(() => {
  fetchList()
  fetchUnread()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>消息中心</h2>
      <el-badge v-if="unreadCount > 0" :value="unreadCount" :max="99">
        <span style="font-size: 13px; color: #9ca3af">未读消息</span>
      </el-badge>
    </div>
    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="200">
          <template #default="{ row }">
            <span :class="{ 'unread': row.isRead === 0 }" style="cursor: pointer" @click="handleRead(row)">
              {{ row.title }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isRead === 0 ? 'danger' : 'info'" size="small">
              {{ row.isRead === 0 ? '未读' : '已读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" min-width="170" />
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
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 500; color: #1f2937; }
.unread { font-weight: 600; color: #1890ff; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>