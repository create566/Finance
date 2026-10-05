<template>
  <div class="chat-wrap">
    <aside class="session-sidebar">
      <div class="sidebar-header">
        <el-button size="small" type="primary" plain style="width: 100%" @click="handleNewChat">+ 新建对话</el-button>
      </div>
      <div class="session-list">
        <div
          v-for="s in sessions"
          :key="s.id"
          :class="['session-item', { active: s.id === sessionId }]"
          @click="handleSelectSession(s.id)"
        >
          <div class="session-item-main">
            <div class="session-title">{{ s.title || '未命名会话' }}</div>
            <div class="session-meta">{{ formatTime(s.updateTime || s.createTime) }} · {{ s.messageCount || 0 }}条</div>
          </div>
          <button
            class="session-delete"
            title="删除该会话"
            @click.stop="handleDeleteSession(s.id)"
          >×</button>
        </div>
        <div v-if="!sessions.length" class="session-empty">暂无历史会话</div>
      </div>
    </aside>
    <div class="chat-container">
    <div class="chat-header">
      <div class="header-left">
        <h2>智能客服</h2>
        <span class="header-desc">AI助手为您解答金融问题</span>
      </div>
      <!-- 只有一个知识库时选择器没有意义（选不选检索范围都一样），自动隐藏；
           管理端建了多个知识库后此处自动出现，选中即限定检索范围 -->
      <div v-if="knowledgeBases.length > 1" class="header-center">
        <el-select
          v-model="selectedKbId"
          :placeholder="'选择知识库（当前' + knowledgeBases.length + '个）'"
          size="small"
          clearable
          style="width: 180px"
          @change="onKbChange"
        >
          <el-option
            v-for="kb in knowledgeBases"
            :key="kb.id"
            :label="kb.name"
            :value="kb.id"
          />
        </el-select>
      </div>
      <div class="header-right">
        <el-button v-if="sessionId" type="danger" plain size="small" @click="handleCloseSession">结束会话</el-button>
      </div>
    </div>

    <div class="chat-body" ref="chatBodyRef">
      <div v-if="messages.length === 0" class="chat-empty">
        <div class="empty-icon">🤖</div>
        <p>您好！我是旺旺信贷智能客服助手</p>
        <p class="empty-hint">请输入您的问题，我会尽力为您解答</p>
        <div class="quick-questions">
          <el-button
            v-for="q in quickQuestions"
            :key="q"
            size="small"
            round
            @click="handleQuickQuestion(q)"
          >{{ q }}</el-button>
        </div>
      </div>

      <div v-for="(msg, idx) in messages" :key="idx" :class="['message-item', msg.role]">
        <div class="message-avatar">
          <span v-if="msg.role === 'user'">👤</span>
          <span v-else>🤖</span>
        </div>
        <div class="message-content">
          <div class="message-text" v-html="formatContent(msg.content)"></div>
          <div class="message-time">{{ msg.createTime || '' }}</div>
        </div>
      </div>

      <div v-if="loading && !(messages[messages.length - 1]?.role === 'assistant' && messages[messages.length - 1]?.content)" class="message-item assistant">
        <div class="message-avatar"><span>🤖</span></div>
        <div class="message-content">
          <div class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-footer">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="2"
        placeholder="请输入您的问题..."
        :disabled="loading"
        @keydown.enter.exact.prevent="handleSend"
      />
      <el-button type="primary" :disabled="!inputText.trim() || loading" @click="handleSend">
        发送
      </el-button>
    </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  askAIStream, getKnowledgeBases, closeSession,
  getAiSessions, getChatHistory, deleteAiSession,
  type ChatMessage, type KnowledgeBase, type ChatSessionItem,
} from '@/api/chat'

/**
 * 当前会话 id 的持久化 key。
 * 之前 sessionId / messages 只存在于内存，浏览器一刷新对话区就全空（"一刷新就没了"）。
 * 这里把 sessionId 落到 localStorage，刷新后自动重新拉取该会话的消息，恢复上下文。
 */
const SESSION_KEY = 'ai_current_session_id'

const chatBodyRef = ref<HTMLElement>()
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const loading = ref(false)
const sessionId = ref<number | null>(null)

// sessionId 变化时同步持久化；清空/删除会话后置 null 会移除该记录
watch(sessionId, (v) => {
  if (v) {
    localStorage.setItem(SESSION_KEY, String(v))
  } else {
    localStorage.removeItem(SESSION_KEY)
  }
})
const selectedKbId = ref<number | null>(null)
const knowledgeBases = ref<KnowledgeBase[]>([])
const sessions = ref<ChatSessionItem[]>([])

const quickQuestions = [
  '借款利率是多少？',
  '如何提高借款额度？',
  '审核需要多长时间？',
  '提前还款有手续费吗？',
]

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  const userMsg: ChatMessage = {
    role: 'user',
    content: text,
    createTime: new Date().toLocaleString('zh-CN'),
  }
  messages.value.push(userMsg)
  inputText.value = ''
  scrollToBottom()

  loading.value = true

  // 改用 SSE 流式：告别"转圈半分钟一口气出全文"，
  // token 边到边显示；userId 由后端从登录 token 解出，前端不再自报
  const streamingMsg: ChatMessage = {
    role: 'assistant',
    content: '',
    createTime: '',
  }
  messages.value.push(streamingMsg)

  askAIStream(
    {
      prompt: text,
      sessionId: sessionId.value || undefined,
      knowledgeBaseId: selectedKbId.value || undefined,
    },
    {
      onToken(token: string) {
        streamingMsg.content += token
        scrollToBottom()
      },
      onDone(sessionId2) {
        if (sessionId2) {
          sessionId.value = sessionId2
        }
        streamingMsg.createTime = new Date().toLocaleString('zh-CN')
        loading.value = false
        scrollToBottom()
        loadSessions()
      },
      onError(err: Error) {
        console.error('[智能客服] 发送失败', err)
        if (!streamingMsg.content) {
          streamingMsg.content = '抱歉，AI服务暂时不可用，请稍后再试。'
        } else {
          streamingMsg.content += '\n\n（回答因网络中断而不完整）'
        }
        streamingMsg.createTime = new Date().toLocaleString('zh-CN')
        loading.value = false
        scrollToBottom()
      },
    },
  )
}

function handleQuickQuestion(q: string) {
  inputText.value = q
  handleSend()
}

async function handleCloseSession() {
  try {
    if (sessionId.value) {
      await ElMessageBox.confirm('确定要结束当前会话吗？', '提示', { type: 'warning' })
      await closeSession(sessionId.value)
      ElMessage.success('会话已结束')
    }
    handleClearChat()
  } catch {
    // 取消
  }
}

function handleClearChat() {
  messages.value = []
  sessionId.value = null
}

async function handleNewChat() {
  // 新建对话：清空当前界面并重置 sessionId，下一条消息会创建新会话，
  // 对应后端新的 chatId("session-新id")，从而加载一份全新的上下文记忆，
  // 不会继承上一段对话。上一段对话的记忆已落库(chat_message/chat_memory)，不受影响，
  // 并保留在左侧"历史会话"列表中，可随时点回继续。
  if (messages.value.length > 0) {
    try {
      await ElMessageBox.confirm('确定要新建对话吗？当前对话将被清空（可在左侧历史会话中重新打开）。', '新建对话', { type: 'warning' })
    } catch {
      return
    }
  }
  handleClearChat()
  loadSessions()
}

// 拉取历史会话列表（左侧侧边栏）；userId 由后端从登录 token 解出
async function loadSessions() {
  try {
    const res = await getAiSessions()
    sessions.value = res?.list ?? []
  } catch {
    // 列表加载失败不阻断主流程
  }
}

// 点回某个历史会话：拉取其消息展示，并切到该 sessionId，
// 后端后续提问会以 chatId="session-{id}" 从 chat_memory 恢复上下文记忆
async function handleSelectSession(id: number) {
  if (loading.value) return
  if (sessionId.value === id) return
  try {
    const res = await getChatHistory(id)
    sessionId.value = id
    messages.value = (res?.messages ?? []).map((m: any) => ({
      role: m.role,
      content: m.content,
      createTime: m.createTime,
    }))
    scrollToBottom()
  } catch {
    ElMessage.error('加载历史会话失败')
  }
}

// 删除历史会话（含其消息），若删除的是当前会话则清空界面
async function handleDeleteSession(id: number) {
  // 先确认：取消时 ElMessageBox 会 reject，这里静默返回
  try {
    await ElMessageBox.confirm('确定删除该历史会话吗？删除后无法恢复。', '提示', { type: 'warning' })
  } catch {
    return
  }
  // 再删除：失败必须显式提示，不能像之前那样把错误吞掉导致"点了没反应"
  try {
    await deleteAiSession(id)
    if (sessionId.value === id) {
      handleClearChat()
    }
    sessions.value = sessions.value.filter(s => s.id !== id)
    ElMessage.success('会话已删除')
    await loadSessions()
  } catch (err: any) {
    console.error('[智能客服] 删除会话失败', err)
    ElMessage.error('删除失败：' + (err?.response?.data?.msg || err?.message || '未知错误'))
  }
}

function formatTime(t?: string): string {
  if (!t) return ''
  // 取 MM-DD HH:mm 部分
  return t.length >= 16 ? t.slice(5, 16) : t
}

function onKbChange() {
  // 切换知识库时清空当前对话，避免上下文混乱
  if (messages.value.length > 0) {
    handleClearChat()
  }
}

function formatContent(content: string): string {
  if (!content) return ''
  return content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br>')
}

function scrollToBottom() {
  nextTick(() => {
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
    }
  })
}

onMounted(async () => {
  try {
    knowledgeBases.value = await getKnowledgeBases()
  } catch {
    // 知识库加载失败不影响正常聊天
  }
  await loadSessions()

  // 刷新后恢复上次会话：仅当该会话仍然存在时才恢复，
  // 若已被删除（后端或管理端删掉）则清掉本地记录，下次发送自动新建会话
  const saved = localStorage.getItem(SESSION_KEY)
  if (saved) {
    const id = Number(saved)
    if (id && sessions.value.some(s => s.id === id)) {
      await handleSelectSession(id)
    } else {
      localStorage.removeItem(SESSION_KEY)
    }
  }
})
</script>

<style scoped>
.chat-wrap {
  display: flex;
  gap: 12px;
  height: calc(100vh - 120px);
}

.session-sidebar {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.sidebar-header {
  padding: 10px;
  border-bottom: 1px solid #f0f0f0;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.session-item-main {
  flex: 1;
  min-width: 0;
}

/* 删除按钮常驻可见（原来要 hover 才出现，用户根本发现不了），
   默认淡灰、悬停变红，既醒目又不喧宾夺主 */
.session-delete {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  line-height: 18px;
  text-align: center;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #b6bcc8;
  font-size: 16px;
  cursor: pointer;
  opacity: 0.75;
  transition: all 0.15s;
}

.session-item:hover .session-delete {
  opacity: 1;
  color: #f56c6c;
}

.session-delete:hover {
  background: #fce4e4;
  color: #e24b4a;
  opacity: 1;
}

.session-item:hover {
  background: #f5f7fa;
}

.session-item.active {
  background: #eef2ff;
}

.session-title {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item.active .session-title {
  color: #667eea;
  font-weight: 600;
}

.session-meta {
  font-size: 11px;
  color: #bbb;
  margin-top: 4px;
}

.session-empty {
  padding: 24px 12px;
  text-align: center;
  font-size: 12px;
  color: #ccc;
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #eee;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}

.header-left h2 {
  margin: 0 0 4px 0;
  font-size: 18px;
}

.header-desc {
  font-size: 12px;
  opacity: 0.8;
}

.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.header-right {
  display: flex;
  gap: 8px;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  background: #f5f7fa;
}

.chat-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.empty-hint {
  font-size: 13px;
  color: #bbb;
  margin-bottom: 24px;
}

.quick-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.message-item {
  display: flex;
  margin-bottom: 16px;
  gap: 10px;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.message-item.user .message-avatar {
  background: #667eea;
}

.message-content {
  max-width: 70%;
}

.message-text {
  padding: 10px 14px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.message-item.user .message-text {
  background: #667eea;
  color: #fff;
}

.message-time {
  font-size: 11px;
  color: #bbb;
  margin-top: 4px;
  text-align: center;
}

.chat-footer {
  padding: 12px 24px;
  background: #fff;
  border-top: 1px solid #eee;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.chat-footer :deep(.el-textarea__inner) {
  resize: none;
}

.typing-indicator {
  display: flex;
  gap: 6px;
  padding: 10px 14px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #bbb;
  animation: typing 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-6px);
    opacity: 1;
  }
}
</style>