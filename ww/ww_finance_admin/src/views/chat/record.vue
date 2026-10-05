<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getChatSessionList, getChatSessionDetail, closeChatSession, deleteChatSession, type ChatSessionQuery } from '@/api/chatRecord'
import type { ChatSession, ChatMessage } from '@/api/chatRecord'

// ------ 搜索 ------
const searchForm = reactive<ChatSessionQuery>({
  keyword: '',
  status: undefined,
})

// ------ 列表 ------
const tableData = ref<ChatSession[]>([])
const loading = ref(false)
// 分页状态：后端 pageSessions 默认只返回 10 条，不传分页参数时后续会话永远看不到，
// 删除后第 11 条顶上会被误认为"没删掉"，因此必须显式分页
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function fetchList() {
  loading.value = true
  try {
    const res = await getChatSessionList({
      keyword: searchForm.keyword,
      status: searchForm.status,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    tableData.value = res.list || []
    total.value = res.total || 0
    // 删除/筛选后当前页可能已越界，回退到最后一页重新拉取
    const maxPage = Math.max(1, Math.ceil(total.value / pageSize.value))
    if (pageNum.value > maxPage) {
      pageNum.value = maxPage
      await fetchList()
    }
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
  pageNum.value = 1
  fetchList()
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

// ------ 详情弹窗（对话记录） ------
const detailVisible = ref(false)
const detailData = ref<ChatSession | null>(null)
const detailLoading = ref(false)

async function openDetail(row: ChatSession) {
  // 列表行不含消息体，必须单取详情，否则气泡区永远是空的
  detailData.value = { ...row, messages: [] }
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getChatSessionDetail(row.id)
    detailData.value = { ...res.session, messages: res.messages || [] }
  } catch {
    // 会话可能已被删除，error 已由拦截器提示
    detailData.value = { ...row, messages: [] }
  } finally {
    detailLoading.value = false
  }
}

// ------ 结束会话 ------
function handleClose(row: ChatSession) {
  ElMessageBox.confirm(`确定结束「${row.userName}」的咨询会话吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await closeChatSession(row.id)
      ElMessage.success('会话已结束')
      fetchList()
    })
    .catch(() => {})
}

// ------ 删除 ------
function handleDelete(row: ChatSession) {
  ElMessageBox.confirm(`确定删除「${row.title}」的咨询记录吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await deleteChatSession(row.id)
      ElMessage.success('删除成功')
      fetchList()
    })
    .catch(() => {})
}

// ------ 工具 ------
const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '进行中', type: 'warning' },
  1: { text: '已结束', type: 'info' },
}

function msgRoleTag(msg: ChatMessage) {
  return msg.role === 'user' ? '用户提问' : 'AI 回答'
}
function msgRoleType(msg: ChatMessage) {
  return msg.role === 'user' ? '' : 'success'
}

/** 序号跨页连续，而非每页都从 1 开始 */
function indexMethod(i: number) {
  return (pageNum.value - 1) * pageSize.value + i + 1
}

/** 消息时间兼容两种序列化：ISO 字符串，或 Jackson 未关 timestamps 时的 [y,M,d,H,m,s] 数组 */
function fmtTime(v?: string | number[]) {
  if (!v) return ''
  if (Array.isArray(v)) {
    const [y, mo, d, h = 0, mi = 0, s = 0] = v
    const p = (n: number) => String(n).padStart(2, '0')
    return `${y}-${p(mo)}-${p(d)} ${p(h)}:${p(mi)}:${p(s)}`
  }
  return String(v).replace('T', ' ').slice(0, 19)
}

// ------ 生命周期 ------
onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2>咨询记录</h2>
    </div>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="用户名/手机号/问题"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 100px">
            <el-option label="进行中" :value="0" />
            <el-option label="已结束" :value="1" />
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
        <el-table-column type="index" :index="indexMethod" label="序号" width="70" align="center" />
        <el-table-column label="咨询用户" min-width="140">
          <template #default="{ row }">
            <div class="user-cell">
              <span class="user-name">{{ row.userName }}</span>
              <span class="user-phone">{{ row.userPhone }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="咨询问题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="messageCount" label="消息数" width="80" align="center" />
        <el-table-column label="使用模型" width="100" align="center">
          <template #default="{ row }">
            <el-tag effect="plain" size="small">{{ row.modelName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="kbName" label="命中知识库" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type as string">
              {{ statusMap[row.status]?.text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="开始时间" min-width="170" />
        <el-table-column prop="updateTime" label="最后更新" min-width="170" />
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">查看对话</el-button>
            <el-button v-if="row.status === 0" type="warning" link @click="handleClose(row)">结束</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :current-page="pageNum"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </el-card>

    <!-- 对话详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="对话详情"
      width="680px"
      :close-on-click-modal="true"
    >
      <template v-if="detailData">
        <!-- 会话信息 -->
        <div class="session-info">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="用户">{{ detailData.userName }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ detailData.userPhone }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusMap[detailData.status]?.type as string" size="small">
                {{ statusMap[detailData.status]?.text }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="模型">
              <el-tag effect="plain" size="small">{{ detailData.modelName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="知识库">{{ detailData.kbName }}</el-descriptions-item>
            <el-descriptions-item label="消息数">{{ detailData.messageCount }} 条</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 对话气泡 -->
        <div class="chat-messages" v-loading="detailLoading">
          <el-empty v-if="!detailLoading && !detailData.messages?.length" description="暂无对话消息" />
          <div
            v-for="(msg, idx) in detailData.messages"
            :key="idx"
            class="chat-msg"
            :class="msg.role === 'user' ? 'msg-user' : 'msg-ai'"
          >
            <div class="msg-meta">
              <el-tag :type="msgRoleType(msg) as string" size="small" effect="dark">
                {{ msgRoleTag(msg) }}
              </el-tag>
              <span class="msg-time">{{ fmtTime(msg.createTime || msg.timestamp) }}</span>
            </div>
            <div class="msg-bubble">
              <pre>{{ msg.content }}</pre>
            </div>
          </div>
        </div>
      </template>
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
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.user-cell {
  display: flex;
  flex-direction: column;
}
.user-name {
  font-weight: 500;
  color: #303133;
}
.user-phone {
  font-size: 12px;
  color: #909399;
}
/* 对话详情样式 */
.session-info {
  margin-bottom: 16px;
}
.chat-messages {
  max-height: 480px;
  overflow-y: auto;
  padding: 8px 0;
}
.chat-msg {
  margin-bottom: 16px;
}
.msg-user {
  padding-left: 0;
}
.msg-ai {
  padding-right: 0;
}
.msg-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.msg-time {
  font-size: 12px;
  color: #c0c4cc;
}
.msg-bubble {
  display: inline-block;
  max-width: 92%;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.7;
}
.msg-user .msg-bubble {
  background: #ecf5ff;
  color: #303133;
  border-top-left-radius: 2px;
}
.msg-ai .msg-bubble {
  background: #f0f9eb;
  color: #303133;
  border-top-right-radius: 2px;
}
.msg-bubble pre {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  margin: 0;
}
</style>