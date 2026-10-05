<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  getMyMessages,
  readMessage,
  getUnreadCount,
  MESSAGE_TYPE_META,
  messageTypeText,
  type MessageItem,
} from '@/api/message'

const tableData = ref<MessageItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const unreadCount = ref(0)

const drawerVisible = ref(false)
const current = ref<MessageItem | null>(null)

async function fetchList() {
  loading.value = true
  try {
    const data = await getMyMessages({ pageNum: pageNum.value, pageSize: pageSize.value })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch {
  } finally {
    loading.value = false
  }
}

async function fetchUnread() {
  try {
    unreadCount.value = await getUnreadCount()
  } catch {}
}

/** 打开详情即视为已读 —— 这是站内信的通行交互 */
async function openDetail(row: MessageItem) {
  current.value = row
  drawerVisible.value = true
  if (row.readFlag === 0) {
    try {
      await readMessage(row.id)
      row.readFlag = 1
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
      <el-table :data="tableData" v-loading="loading" stripe border empty-text="暂无消息">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag
              :type="(MESSAGE_TYPE_META[row.messageType]?.tag as string) || 'info'"
              size="small"
              effect="plain"
            >
              {{ messageTypeText(row.messageType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="200">
          <template #default="{ row }">
            <span :class="{ unread: row.readFlag === 0 }" class="title-link" @click="openDetail(row)">
              {{ row.title }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="摘要" min-width="280" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.readFlag === 0 ? 'danger' : 'info'" size="small">
              {{ row.readFlag === 0 ? '未读' : '已读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" min-width="170" />
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">查看</el-button>
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

    <el-drawer v-model="drawerVisible" title="消息详情" size="420px">
      <template v-if="current">
        <div class="detail-head">
          <el-tag
            :type="(MESSAGE_TYPE_META[current.messageType]?.tag as string) || 'info'"
            size="small"
            effect="plain"
          >
            {{ messageTypeText(current.messageType) }}
          </el-tag>
          <span class="detail-time">{{ current.createTime }}</span>
        </div>
        <h3 class="detail-title">{{ current.title }}</h3>
        <!-- 纯文本渲染，防止消息正文里的 HTML 被执行 -->
        <pre class="detail-content">{{ current.content }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.page-container { width: 100%; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 500; color: #1f2937; }
.unread { font-weight: 600; color: #1890ff; }
.title-link { cursor: pointer; }
.title-link:hover { text-decoration: underline; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.detail-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.detail-time { font-size: 12px; color: #9ca3af; }
.detail-title { font-size: 16px; font-weight: 600; color: #1f2937; margin: 0 0 12px; }
.detail-content {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.8;
  color: #374151;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 14px 16px;
  margin: 0;
}
</style>
