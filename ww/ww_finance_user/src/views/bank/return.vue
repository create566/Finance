<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'

/**
 * 银行处理完成后的回跳落地页（对应后端 HfbConst 的 6 个 *_RETURN_URL）。
 *
 * ── 为什么必须有这样一个**免登录**的独立页 ─────────────────────────────
 * 银行走完流程后会按 `returnUrl` 做一次**整页跳转**（`BANK_FRONT_BASE_URL` + 本路径）。
 * 而登录态 `user_token` 是**按 host 存**的 cookie：
 *   - 用户用 `http://127.0.0.1:5174` 访问，配置里写的却是 `http://localhost:5174`；
 *   - 或用户用 IP / 域名访问，配置没跟着改。
 * 回跳时 host 一变，cookie 就带不过来。此前 returnUrl 直接指到 `/home`，
 * 而 `/home` 在路由守卫的白名单之外 ⇒ 用户明明付了钱，却被丢到**登录页**，
 * 观感就是"付完款账号还退登了"。把这个落地页加进白名单，回跳就不再依赖登录态。
 *
 * ── 两种落地形态 ─────────────────────────────────────────────────────
 * 1. 新窗口（正常形态）：前端用 `openBankForm` 把收银台开在新窗口，原标签页留在应用内，
 *    登录态与页面状态都不受影响。本页检测到 `window.opener` 就倒计时自动关闭，
 *    用户回到原页面即可看到最新余额 / 进度。
 * 2. 同标签页（兜底）：没有 opener 时不要自作主张关窗口，给一个"返回首页"按钮。
 */
const countdown = ref(3)
const isPopup = ref(false)
let timer: number | undefined

function closeSelf() {
  window.close()
}

onMounted(() => {
  // `window.opener` 存在 ⇒ 本页是由应用脚本 open 出来的支付窗口，可以安全自关
  isPopup.value = !!window.opener && !window.opener.closed

  if (!isPopup.value) return

  timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      if (timer !== undefined) window.clearInterval(timer)
      timer = undefined
      closeSelf()
    }
  }, 1000)
})

onBeforeUnmount(() => {
  if (timer !== undefined) window.clearInterval(timer)
})
</script>

<template>
  <div class="bank-return">
    <el-card shadow="always" class="bank-return-card">
      <div class="icon-wrap">
        <span class="icon-ring">
          <el-icon :size="42"><CircleCheckFilled /></el-icon>
        </span>
      </div>
      <h2>操作已提交</h2>
      <p class="sub">
        银行已受理本次操作，最终结果以银行回调为准，稍后可在
        <strong>资金流水</strong> / <strong>我的投资</strong> 中查看。
      </p>

      <template v-if="isPopup">
        <p class="hint">
          本窗口将在 <span class="num">{{ countdown }}</span> 秒后自动关闭，
          请返回<strong>原来的页面</strong>查看结果。
        </p>
        <el-button type="primary" @click="closeSelf">立即关闭本窗口</el-button>
      </template>
      <template v-else>
        <p class="hint">请返回平台继续操作。</p>
        <el-button type="primary" @click="closeSelf">返回平台</el-button>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.bank-return {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
  padding: var(--space-6);
}
.bank-return-card {
  width: 440px;
  max-width: 100%;
  border-radius: var(--radius-lg);
  text-align: center;
  padding: var(--space-5) var(--space-4);
}
.icon-wrap {
  margin-bottom: var(--space-4);
  display: flex;
  justify-content: center;
}
.icon-ring {
  width: 76px;
  height: 76px;
  border-radius: var(--radius-full);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-success);
  background: var(--color-success-bg);
  border: 1px solid var(--color-success-border);
}
.bank-return-card h2 {
  margin: 0 0 var(--space-2);
  font-size: var(--font-xl);
  font-weight: 600;
  color: var(--text-primary);
}
.sub {
  margin: 0 0 var(--space-4);
  font-size: var(--font-base);
  line-height: var(--lh-base);
  color: var(--text-regular);
}
.hint {
  margin: 0 0 var(--space-4);
  font-size: var(--font-sm);
  color: var(--text-placeholder);
}
.hint .num {
  color: var(--color-primary);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
</style>
