<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  getMyMessages,
  readMessage,
  getUnreadCount,
  MESSAGE_TYPE_META,
  type MessageItem,
} from '@/api/message'
import { adaptTagMap, MESSAGE_READ_MAP } from '@/utils/enumText'
import AppCard from '@/components/common/AppCard.vue'
import AppEmpty from '@/components/common/AppEmpty.vue'
import AppPage from '@/components/common/AppPage.vue'
import StatusTag from '@/components/common/StatusTag.vue'

const tableData = ref<MessageItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const unreadCount = ref(0)

const drawerVisible = ref(false)
const current = ref<MessageItem | null>(null)

/** 消息类型表由 api/message.ts 拥有，这里只做形状适配 */
const messageTypeMap = adaptTagMap(MESSAGE_TYPE_META)

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
  <AppPage title="消息中心" subtitle="平台通知、审核结果与资金变动提醒">
    <template #actions>
      <el-badge v-if="unreadCount > 0" :value="unreadCount" :max="99" class="msg__badge">
        <span class="msg__badge-text">未读消息</span>
      </el-badge>
      <span v-else class="msg__badge-text muted">暂无未读</span>
    </template>

    <AppCard body-flush>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.messageType" :map="messageTypeMap" />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="200">
          <template #default="{ row }">
            <span :class="{ 'is-unread': row.readFlag === 0 }" class="title-link" @click="openDetail(row)">
              {{ row.title }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="摘要" min-width="280" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <StatusTag :status="row.readFlag" :map="MESSAGE_READ_MAP" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" min-width="170" />
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <AppEmpty description="暂无消息" />
        </template>
      </el-table>

      <div v-if="total > 0" class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          background
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </AppCard>

    <el-drawer v-model="drawerVisible" title="消息详情" size="420px">
      <template v-if="current">
        <div class="detail-head">
          <StatusTag :status="current.messageType" :map="messageTypeMap" />
          <span class="detail-time">{{ current.createTime }}</span>
        </div>
        <h3 class="detail-title">{{ current.title }}</h3>
        <!-- 纯文本渲染，防止消息正文里的 HTML 被执行 -->
        <pre class="detail-content">{{ current.content }}</pre>
      </template>
    </el-drawer>
  </AppPage>
</template>

<style scoped>
.msg__badge-text {
  font-size: var(--font-sm);
  color: var(--text-secondary);
}
.muted {
  color: var(--text-placeholder);
}
.title-link {
  cursor: pointer;
  color: var(--text-regular);
}
.title-link:hover {
  color: var(--color-primary);
  text-decoration: underline;
}
.is-unread {
  font-weight: 600;
  color: var(--color-primary);
}
.pager {
  display: flex;
  justify-content: flex-end;
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--border-light);
}
.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-3);
}
.detail-time {
  font-size: var(--font-xs);
  color: var(--text-placeholder);
}
.detail-title {
  font-size: var(--font-md);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 var(--space-3);
}
.detail-content {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: var(--font-base);
  line-height: var(--lh-base);
  color: var(--text-regular);
  background: var(--bg-sunken);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-base);
  padding: var(--space-4);
  margin: 0;
}
</style>
