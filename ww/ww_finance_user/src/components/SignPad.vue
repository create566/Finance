<script setup lang="ts">
/**
 * SignPad —— 手写签名板
 *
 * 关键设计：
 * 1. 用**笔画历史 + 重绘**而非直接改位图：这样窗口缩放 / 高 DPI 屏重排后签名不会丢，也能实现「撤销」。
 * 2. 用 pointer 事件统一覆盖鼠标 / 触屏 / 触控笔，避免维护三套监听。
 * 3. 内部按 devicePixelRatio 放大画布，避免高清屏上笔迹发虚。
 * 4. 导出时按 maxExportWidth 等比缩小（默认 500px），PNG 体积可控，与后端签名图落盘一致。
 */
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 逻辑宽度（画布坐标，非像素） */
    width?: number
    /** 逻辑高度 */
    height?: number
    /** 线条宽度（逻辑坐标） */
    lineWidth?: number
    /** 导出图最大宽度，越小体积越小 */
    maxExportWidth?: number
  }>(),
  { width: 600, height: 220, lineWidth: 2.6, maxExportWidth: 500 },
)

const emit = defineEmits<{ (e: 'change', isEmpty: boolean): void }>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
/** 笔画历史：每笔是一串逻辑坐标点 */
const strokes = ref<Array<Array<{ x: number; y: number }>>>([])
const drawing = ref(false)
let ctx: CanvasRenderingContext2D | null = null
let currentStroke: Array<{ x: number; y: number }> | null = null

function setupCanvas() {
  const canvas = canvasRef.value
  if (!canvas) return
  const dpr = window.devicePixelRatio || 1
  canvas.width = Math.round(props.width * dpr)
  canvas.height = Math.round(props.height * dpr)
  ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.scale(dpr, dpr)
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  ctx.lineWidth = props.lineWidth
  ctx.strokeStyle = '#1f2937'
  redraw()
}

/** 白底 + 重放全部笔画。导出的 PNG 是白底黑字，贴进合同 PDF 也能正常显示 */
function redraw() {
  if (!ctx) return
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, props.width, props.height)
  ctx.strokeStyle = '#1f2937'
  ctx.lineWidth = props.lineWidth
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
  for (const stroke of strokes.value) {
    if (stroke.length < 2) {
      // 单击也留一个点，避免用户点一下没反应
      const p = stroke[0]
      if (p) {
        ctx.beginPath()
        ctx.arc(p.x, p.y, props.lineWidth / 2, 0, Math.PI * 2)
        ctx.fillStyle = '#1f2937'
        ctx.fill()
      }
      continue
    }
    ctx.beginPath()
    ctx.moveTo(stroke[0].x, stroke[0].y)
    for (let i = 1; i < stroke.length; i++) ctx.lineTo(stroke[i].x, stroke[i].y)
    ctx.stroke()
  }
}

/** 客户端坐标 → 画布逻辑坐标 */
function toLogical(e: PointerEvent): { x: number; y: number } {
  const canvas = canvasRef.value!
  const rect = canvas.getBoundingClientRect()
  const sx = props.width / rect.width
  const sy = props.height / rect.height
  return { x: (e.clientX - rect.left) * sx, y: (e.clientY - rect.top) * sy }
}

function onPointerDown(e: PointerEvent) {
  e.preventDefault()
  const canvas = canvasRef.value
  if (!canvas || !ctx) return
  canvas.setPointerCapture?.(e.pointerId)
  drawing.value = true
  currentStroke = [toLogical(e)]
  strokes.value.push(currentStroke)
  redraw()
}

function onPointerMove(e: PointerEvent) {
  if (!drawing.value || !currentStroke || !ctx) return
  e.preventDefault()
  const p = toLogical(e)
  const last = currentStroke[currentStroke.length - 1]
  // 抽稀：两点距离过近不记录，减小历史体积
  if (last && Math.hypot(p.x - last.x, p.y - last.y) < 1.2) return
  currentStroke.push(p)
  // 增量绘制，避免每次整幅重绘造成卡顿
  ctx.beginPath()
  ctx.moveTo(last!.x, last!.y)
  ctx.lineTo(p.x, p.y)
  ctx.stroke()
}

function onPointerUp(e: PointerEvent) {
  if (!drawing.value) return
  drawing.value = false
  canvasRef.value?.releasePointerCapture?.(e.pointerId)
  currentStroke = null
  emit('change', isEmpty())
}

function clear() {
  strokes.value = []
  currentStroke = null
  drawing.value = false
  redraw()
  emit('change', true)
}

function undo() {
  strokes.value.pop()
  redraw()
  emit('change', isEmpty())
}

function isEmpty(): boolean {
  return strokes.value.length === 0
}

/** 导出 PNG dataURL；空白时返回空串，由调用方拦截提示 */
function exportBase64(): string {
  const canvas = canvasRef.value
  if (!canvas || isEmpty()) return ''

  // 等比缩放到 maxExportWidth，降低 base64 体积
  const ratio = Math.min(1, props.maxExportWidth / props.width)
  const outW = Math.max(1, Math.round(props.width * ratio))
  const outH = Math.max(1, Math.round(props.height * ratio))

  const out = document.createElement('canvas')
  out.width = outW
  out.height = outH
  const octx = out.getContext('2d')
  if (!octx) return ''
  octx.fillStyle = '#ffffff'
  octx.fillRect(0, 0, outW, outH)
  octx.drawImage(canvas, 0, 0, canvas.width, canvas.height, 0, 0, outW, outH)
  return out.toDataURL('image/png')
}

let resizeTimer: number | undefined
function handleResize() {
  window.clearTimeout(resizeTimer)
  // 重排后按笔画历史重绘，签名不丢
  resizeTimer = window.setTimeout(() => redraw(), 120)
}

onMounted(async () => {
  await nextTick()
  setupCanvas()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  window.clearTimeout(resizeTimer)
})

defineExpose({ clear, undo, isEmpty, exportBase64 })
</script>

<template>
  <div class="sign-pad">
    <canvas
      ref="canvasRef"
      class="sign-canvas"
      :style="{ aspectRatio: `${width} / ${height}` }"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
    />
    <div v-if="isEmpty()" class="sign-tip">请在此区域手写签名</div>
    <div class="sign-actions">
      <el-button size="small" @click="undo" :disabled="isEmpty()">撤销</el-button>
      <el-button size="small" @click="clear" :disabled="isEmpty()">清空</el-button>
    </div>
  </div>
</template>

<style scoped>
.sign-pad {
  position: relative;
  width: 100%;
}
.sign-canvas {
  display: block;
  width: 100%;
  background: #fff;
  border: 1px dashed #c0c4cc;
  border-radius: 6px;
  cursor: crosshair;
  /* 阻止触屏拖动时页面滚动，否则手机上签名会变成滚屏 */
  touch-action: none;
}
.sign-tip {
  position: absolute;
  top: 44%;
  left: 0;
  right: 0;
  text-align: center;
  color: #c0c4cc;
  font-size: 16px;
  letter-spacing: 6px;
  pointer-events: none;
  user-select: none;
}
.sign-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
