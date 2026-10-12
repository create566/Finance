<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'

/**
 * 滑块拼图人机验证（纯前端实现）
 * 用法：<SliderCaptcha v-model:visible="captchaVisible" @success="onCaptchaSuccess" />
 */

const visible = defineModel<boolean>('visible', { default: false })
const emit = defineEmits<{ (e: 'success'): void }>()

const W = 310 // 画布宽
const H = 150 // 画布高
const P = 44 // 拼图边长
const TOLERANCE = 6 // 允许误差（px）

const bgCanvas = ref<HTMLCanvasElement>()
const pieceCanvas = ref<HTMLCanvasElement>()

let targetX = 0 // 缺口横坐标
const pieceY = ref(0) // 缺口纵坐标
const dragX = ref(0) // 滑块/拼图当前位移
const dragging = ref(false)
const failed = ref(false)
const succeeded = ref(false)

let startX = 0
let offscreen: HTMLCanvasElement | null = null

function roundSquarePath(ctx: CanvasRenderingContext2D, x: number, y: number, s: number, r: number) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + s, y, x + s, y + s, r)
  ctx.arcTo(x + s, y + s, x, y + s, r)
  ctx.arcTo(x, y + s, x, y, r)
  ctx.arcTo(x, y, x + s, y, r)
  ctx.closePath()
}

function drawBackground(ctx: CanvasRenderingContext2D) {
  const hue = Math.floor(Math.random() * 360)
  const grad = ctx.createLinearGradient(0, 0, W, H)
  grad.addColorStop(0, `hsl(${hue}, 55%, 72%)`)
  grad.addColorStop(1, `hsl(${(hue + 60) % 360}, 60%, 55%)`)
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, W, H)

  // 随机圆点纹理，防止纯色被直接肉眼比对
  for (let i = 0; i < 14; i++) {
    ctx.beginPath()
    ctx.fillStyle = `rgba(255,255,255,${0.08 + Math.random() * 0.12})`
    ctx.arc(Math.random() * W, Math.random() * H, 4 + Math.random() * 20, 0, Math.PI * 2)
    ctx.fill()
  }
  for (let i = 0; i < 6; i++) {
    ctx.beginPath()
    ctx.strokeStyle = `rgba(255,255,255,${0.1 + Math.random() * 0.15})`
    ctx.lineWidth = 1 + Math.random() * 2
    const x1 = Math.random() * W
    const y1 = Math.random() * H
    ctx.moveTo(x1, y1)
    ctx.lineTo(x1 + (Math.random() - 0.5) * 120, y1 + (Math.random() - 0.5) * 80)
    ctx.stroke()
  }
}

function render() {
  const bg = bgCanvas.value
  const piece = pieceCanvas.value
  if (!bg || !piece) return
  const bctx = bg.getContext('2d')
  const pctx = piece.getContext('2d')
  if (!bctx || !pctx) return

  targetX = P + 24 + Math.random() * (W - 2 * P - 48)
  pieceY.value = 16 + Math.random() * (H - P - 32)
  dragX.value = 0
  failed.value = false
  succeeded.value = false

  // 1. 离屏画布生成背景图
  offscreen = document.createElement('canvas')
  offscreen.width = W
  offscreen.height = H
  const octx = offscreen.getContext('2d')
  if (!octx) return
  drawBackground(octx)

  // 2. 主画布：背景 + 抠出缺口
  bctx.globalCompositeOperation = 'source-over'
  bctx.clearRect(0, 0, W, H)
  bctx.drawImage(offscreen, 0, 0)
  bctx.globalCompositeOperation = 'destination-out'
  roundSquarePath(bctx, targetX, pieceY.value, P, 10)
  bctx.fill()

  // 缺口描边
  bctx.globalCompositeOperation = 'source-over'
  roundSquarePath(bctx, targetX, pieceY.value, P, 10)
  bctx.strokeStyle = 'rgba(255,255,255,0.85)'
  bctx.lineWidth = 2
  bctx.stroke()

  // 3. 拼图块：裁剪出与缺口相同的形状
  pctx.clearRect(0, 0, P, P)
  pctx.save()
  roundSquarePath(pctx, 0, 0, P, 10)
  pctx.clip()
  pctx.drawImage(offscreen, -targetX, -pieceY.value)
  pctx.restore()
  roundSquarePath(pctx, 0, 0, P, 10)
  pctx.strokeStyle = 'rgba(255,255,255,0.95)'
  pctx.lineWidth = 2
  pctx.stroke()
}

function onPointerDown(e: PointerEvent) {
  if (succeeded.value) return
  dragging.value = true
  failed.value = false
  startX = e.clientX - dragX.value
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value) return
  dragX.value = Math.max(0, Math.min(W - P, e.clientX - startX))
}

function onPointerUp() {
  if (!dragging.value) return
  dragging.value = false
  if (Math.abs(dragX.value - targetX) <= TOLERANCE) {
    succeeded.value = true
    emit('success')
    setTimeout(() => {
      visible.value = false
      window.setTimeout(render, 400)
    }, 350)
  } else {
    failed.value = true
    setTimeout(() => {
      dragX.value = 0
      setTimeout(() => (failed.value = false), 300)
    }, 350)
  }
}

watch(visible, (v) => {
  if (v) nextTick(render)
})

defineExpose({ refresh: render })
</script>

<template>
  <el-dialog
    v-model="visible"
    title="安全验证"
    width="360px"
    align-center
    :close-on-click-modal="false"
    class="captcha-dialog"
  >
    <div class="captcha-body">
      <div class="canvas-wrap" :style="{ width: W + 'px', height: H + 'px' }">
        <canvas ref="bgCanvas" :width="W" :height="H" class="bg-canvas" />
        <canvas
          ref="pieceCanvas"
          :width="P"
          :height="P"
          class="piece-canvas"
          :style="{
            top: pieceY + 'px',
            left: dragX + 'px',
            transition: dragging ? 'none' : 'left 0.3s ease',
          }"
        />
        <div v-if="succeeded" class="result-tip success">验证通过</div>
        <div v-else-if="failed" class="result-tip fail">未对齐，请重试</div>
      </div>

      <div class="slider-row">
        <div class="slider-track" :style="{ width: W + 'px' }">
          <div
            class="slider-fill"
            :class="{ fail: failed, success: succeeded }"
            :style="{ width: dragX + P + 'px' }"
          />
          <span class="slider-text" :class="{ hidden: dragging || dragX > 0 || succeeded }">
            按住滑块，拖动拼图对齐缺口
          </span>
          <div
            class="slider-handle"
            :class="{ fail: failed, success: succeeded }"
            :style="{
              left: dragX + 'px',
              transition: dragging ? 'none' : 'left 0.3s ease',
            }"
            @pointerdown="onPointerDown"
            @pointermove="onPointerMove"
            @pointerup="onPointerUp"
            @pointercancel="onPointerUp"
          >
            <span v-if="succeeded">✓</span>
            <span v-else-if="failed">✕</span>
            <span v-else>»»</span>
          </div>
        </div>
        <el-button :icon="Refresh" circle title="换一张" @click="render" />
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.captcha-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.canvas-wrap {
  position: relative;
  background: #dfe7f2;
  border-radius: 8px;
  overflow: hidden;
  user-select: none;
}
.bg-canvas {
  display: block;
}
.piece-canvas {
  position: absolute;
  cursor: grab;
}
.result-tip {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 12px;
  line-height: 22px;
  color: #fff;
}
.result-tip.success {
  background: rgba(103, 194, 58, 0.85);
}
.result-tip.fail {
  background: rgba(245, 108, 108, 0.85);
}
.slider-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.slider-track {
  position: relative;
  height: 40px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  overflow: hidden;
}
.slider-fill {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  background: rgba(91, 134, 229, 0.15);
}
.slider-fill.fail {
  background: rgba(245, 108, 108, 0.18);
}
.slider-fill.success {
  background: rgba(103, 194, 58, 0.18);
}
.slider-text {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  line-height: 38px;
  text-align: center;
  font-size: 13px;
  color: #94a3b8;
  pointer-events: none;
  transition: opacity 0.2s;
}
.slider-text.hidden {
  opacity: 0;
}
.slider-handle {
  position: absolute;
  top: 0;
  width: 44px;
  height: 40px;
  border-radius: 20px;
  background: linear-gradient(135deg, #36d1dc, #5b86e5);
  color: #fff;
  font-size: 14px;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  touch-action: none;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.2);
}
.slider-handle:active {
  cursor: grabbing;
}
.slider-handle.fail {
  background: #f56c6c;
}
.slider-handle.success {
  background: #67c23a;
}
</style>

<style>
.captcha-dialog .el-dialog__body {
  padding-top: 8px;
}
</style>
