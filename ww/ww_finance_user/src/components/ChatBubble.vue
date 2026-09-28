<template>
  <div class="chat-bubble-root">
    <div
      v-show="!visible"
      class="chat-bubble-btn"
      :style="bubbleStyle"
      @mousedown.prevent="startDrag"
      @touchstart.prevent="startDragTouch"
    >
      <span class="bubble-icon">💬</span>
      <span class="bubble-text">智能客服</span>
    </div>

    <Transition name="chat-slide">
      <div v-if="visible" class="chat-popup">
        <div class="popup-header" @mousedown="startPopupDrag">
          <span>🤖 智能客服</span>
          <div class="popup-actions">
            <span class="action-btn" title="清空对话" @click.stop="clearChat">🗑</span>
            <span class="action-btn" title="关闭窗口" @click.stop="visible = false">✕</span>
          </div>
        </div>

        <div class="popup-body" ref="bodyRef">
          <div v-if="messages.length === 0" class="popup-empty">
            <div class="empty-icon">🤖</div>
            <p>您好！我是智能客服助手</p>
            <p class="empty-hint">请输入问题，我会尽快为您解答</p>
            <div class="quick-questions">
              <button
                v-for="q in quickQuestions"
                :key="q"
                class="quick-btn"
                @click="sendQuick(q)"
              >{{ q }}</button>
            </div>
          </div>

          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            :class="['msg-item', msg.role]"
          >
            <div class="msg-avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
            <div class="msg-bubble">
              <div class="msg-text" v-html="renderContent(msg)"></div>
              <div v-if="msg.role === 'assistant' && msg.streaming" class="typing-cursor">|</div>
            </div>
          </div>

          <div v-if="loading && !streamingMsg" class="msg-item assistant">
            <div class="msg-avatar">🤖</div>
            <div class="msg-bubble typing-dots">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>

        <div class="popup-footer">
          <input
            v-model="inputText"
            class="chat-input"
            placeholder="请输入您的问题..."
            :disabled="loading"
            @keydown.enter="handleSend"
          />
          <button class="send-btn" :disabled="!inputText.trim() || loading" @click="handleSend">
            {{ loading ? '...' : '发送' }}
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { askAIStream, abortStream } from '@/api/chat'

interface Message {
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
}

const CHAT_ID_KEY = 'ai_chat_id'

const visible = ref(false)
const bodyRef = ref<HTMLElement>()
const inputText = ref('')
const loading = ref(false)
const messages = ref<Message[]>([])
const streamingMsg = ref<Message | null>(null)

const quickQuestions = [
  '借款利率是多少？',
  '如何提高借款额度？',
  '审核需要多长时间？',
  '提前还款有手续费吗？',
]

function getChatId(): string {
  let id = localStorage.getItem(CHAT_ID_KEY)
  if (!id) {
    id = 'chat-' + Date.now()
    localStorage.setItem(CHAT_ID_KEY, id)
  }
  return id
}

// ========== 悬浮球拖拽 ==========
const bubblePos = reactive({
  x: typeof window !== 'undefined' ? window.innerWidth - 80 : 0,
  y: typeof window !== 'undefined' ? window.innerHeight - 180 : 0,
})

const bubbleStyle = computed(() => ({
  left: bubblePos.x + 'px',
  top: bubblePos.y + 'px',
}))

let dragStart = { x: 0, y: 0, startX: 0, startY: 0 }
let isDragging = false

function clamp(val: number, min: number, max: number) {
  return Math.max(min, Math.min(max, val))
}

function startDrag(e: MouseEvent) {
  isDragging = false
  dragStart = { x: e.clientX, y: e.clientY, startX: bubblePos.x, startY: bubblePos.y }
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', stopDrag)
}

function startDragTouch(e: TouchEvent) {
  const touch = e.touches[0]
  isDragging = false
  dragStart = { x: touch.clientX, y: touch.clientY, startX: bubblePos.x, startY: bubblePos.y }
  document.addEventListener('touchmove', onDragTouch, { passive: false })
  document.addEventListener('touchend', stopDragTouch)
}

function onDrag(e: MouseEvent) {
  const dx = e.clientX - dragStart.x
  const dy = e.clientY - dragStart.y
  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) isDragging = true
  bubblePos.x = clamp(dragStart.startX + dx, 0, window.innerWidth - 70)
  bubblePos.y = clamp(dragStart.startY + dy, 0, window.innerHeight - 70)
}

function onDragTouch(e: TouchEvent) {
  e.preventDefault()
  const touch = e.touches[0]
  const dx = touch.clientX - dragStart.x
  const dy = touch.clientY - dragStart.y
  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) isDragging = true
  bubblePos.x = clamp(dragStart.startX + dx, 0, window.innerWidth - 70)
  bubblePos.y = clamp(dragStart.startY + dy, 0, window.innerHeight - 70)
}

function stopDrag() {
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', stopDrag)
  if (!isDragging) {
    visible.value = true
  }
}

function stopDragTouch() {
  document.removeEventListener('touchmove', onDragTouch)
  document.removeEventListener('touchend', stopDragTouch)
  if (!isDragging) {
    visible.value = true
  }
}

// ========== 弹出窗口拖拽 ==========
let popupDrag = { x: 0, y: 0 }
const popupOffset = reactive({ x: 0, y: 0 })

function startPopupDrag(e: MouseEvent) {
  popupDrag = { x: e.clientX - popupOffset.x, y: e.clientY - popupOffset.y }
  document.addEventListener('mousemove', onPopupDrag)
  document.addEventListener('mouseup', stopPopupDrag)
}

function onPopupDrag(e: MouseEvent) {
  popupOffset.x = e.clientX - popupDrag.x
  popupOffset.y = e.clientY - popupDrag.y
}

function stopPopupDrag() {
  document.removeEventListener('mousemove', onPopupDrag)
  document.removeEventListener('mouseup', stopPopupDrag)
}

// ========== 消息处理 ==========
function scrollToBottom() {
  nextTick(() => {
    if (bodyRef.value) {
      bodyRef.value.scrollTop = bodyRef.value.scrollHeight
    }
  })
}

function sendQuick(q: string) {
  inputText.value = q
  handleSend()
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  scrollToBottom()
  loading.value = true

  const chatId = getChatId()
  let fullAnswer = ''

  streamingMsg.value = { role: 'assistant', content: '', streaming: true }

  askAIStream(
    { prompt: text, chatId },
    {
      onToken(token: string) {
        fullAnswer += token
        if (streamingMsg.value) {
          streamingMsg.value.content = fullAnswer
        }
        scrollToBottom()
      },
      onDone() {
        if (streamingMsg.value) {
          streamingMsg.value.streaming = false
          messages.value.push({ ...streamingMsg.value })
          streamingMsg.value = null
        }
        loading.value = false
        scrollToBottom()
      },
      onError(err: Error) {
        console.error('SSE error:', err)
        if (streamingMsg.value) {
          streamingMsg.value.streaming = false
          streamingMsg.value.content = fullAnswer || '抱歉，AI服务暂时不可用，请稍后再试。'
          messages.value.push({ ...streamingMsg.value })
          streamingMsg.value = null
        }
        loading.value = false
        scrollToBottom()
      },
    },
  )
}

function clearChat() {
  messages.value = []
  streamingMsg.value = null
  localStorage.removeItem(CHAT_ID_KEY)
}

function renderContent(msg: Message): string {
  if (!msg.content) return ''
  return msg.content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br>')
}

// ========== 窗口resize时修正位置 ==========
function onResize() {
  bubblePos.x = clamp(bubblePos.x, 0, window.innerWidth - 70)
  bubblePos.y = clamp(bubblePos.y, 0, window.innerHeight - 70)
}

onMounted(() => {
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  abortStream()
})
</script>

<style scoped>
.chat-bubble-root {
  position: fixed;
  z-index: 9999;
  pointer-events: none;
}
.chat-bubble-root > * {
  pointer-events: auto;
}

/* ===== 悬浮球 ===== */
.chat-bubble-btn {
  position: fixed;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  user-select: none;
  transition: transform 0.2s, box-shadow 0.2s;
  z-index: 9999;
}
.chat-bubble-btn:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.55);
}
.chat-bubble-btn:active {
  transform: scale(0.95);
}
.bubble-icon {
  font-size: 26px;
  line-height: 1;
}
.bubble-text {
  font-size: 9px;
  color: #fff;
  margin-top: 2px;
}

/* ===== 弹出窗口 ===== */
.chat-popup {
  position: fixed;
  bottom: 20px;
  right: 20px;
  width: 380px;
  height: 520px;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 32px);
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 9999;
}

/* 动画 */
.chat-slide-enter-active {
  transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.chat-slide-leave-active {
  transition: all 0.2s ease-in;
}
.chat-slide-enter-from {
  opacity: 0;
  transform: translateY(20px) scale(0.9);
}
.chat-slide-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.9);
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: move;
  flex-shrink: 0;
}
.popup-actions {
  display: flex;
  gap: 6px;
}
.action-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.2s;
}
.action-btn:hover {
  background: rgba(255, 255, 255, 0.25);
}

/* body */
.popup-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 14px 6px;
  background: #f5f7fa;
}
.popup-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #999;
  text-align: center;
}
.empty-icon {
  font-size: 48px;
  margin-bottom: 10px;
}
.empty-hint {
  font-size: 12px;
  color: #bbb;
  margin-bottom: 16px;
}
.quick-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
.quick-btn {
  padding: 6px 14px;
  border: 1px solid #ddd;
  border-radius: 16px;
  background: #fff;
  color: #666;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.quick-btn:hover {
  border-color: #667eea;
  color: #667eea;
  background: #f0f2ff;
}

/* 消息 */
.msg-item {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.msg-item.user {
  flex-direction: row-reverse;
}
.msg-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #e8ecf1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}
.msg-bubble {
  max-width: 75%;
  padding: 10px 13px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.55;
  position: relative;
  word-break: break-word;
}
.msg-item.user .msg-bubble {
  background: #667eea;
  color: #fff;
  border-bottom-right-radius: 4px;
}
.msg-item.assistant .msg-bubble {
  background: #fff;
  color: #333;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.msg-text {
  white-space: pre-wrap;
}

/* 打字光标 */
.typing-cursor {
  display: inline-block;
  color: #667eea;
  font-weight: bold;
  animation: blink 0.7s infinite;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* 等待中点点点 */
.typing-dots {
  display: flex;
  gap: 4px;
  padding: 14px 16px;
}
.typing-dots span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #bbb;
  animation: dot-bounce 1.2s infinite;
}
.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-6px); }
}

/* footer */
.popup-footer {
  display: flex;
  gap: 8px;
  padding: 10px 14px;
  border-top: 1px solid #eee;
  background: #fff;
  flex-shrink: 0;
}
.chat-input {
  flex: 1;
  padding: 9px 13px;
  border: 1px solid #ddd;
  border-radius: 20px;
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s;
}
.chat-input:focus {
  border-color: #667eea;
}
.chat-input:disabled {
  background: #f5f5f5;
}
.send-btn {
  padding: 8px 18px;
  border: none;
  border-radius: 20px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s;
  white-space: nowrap;
}
.send-btn:hover:not(:disabled) {
  opacity: 0.9;
}
.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>