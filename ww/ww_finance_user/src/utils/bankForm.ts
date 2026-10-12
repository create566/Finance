import { ElMessage } from 'element-plus'

/**
 * 「银行收银台」表单的统一投放口。
 *
 * 后端这几个接口（绑卡 / 充值 / 提现 / 投标 / 还款）成功时返回的是**一整页 HTML**，
 * 里面是一个会**自动提交到银行**的 form。它必须放到**新窗口**里提交，
 * 绝不能拿当前标签页整页跳转 —— 这正是「点返回平台掉登录页」的根因：
 *
 *   1. 当前标签页一跳走，整个 SPA 就被卸载了。用户在银行页面操作的整段时间里，
 *      应用是"消失"的（没有余额、没有进度、也没有返回入口）。
 *   2. 银行处理完会按 `returnUrl` 回跳（`BANK_FRONT_BASE_URL` + 结果页），
 *      那是**一次全新的整页加载**。而登录态 `user_token` 是**按 host 存的 cookie**：
 *      只要回跳 host 与用户实际访问的 host 不一致（`127.0.0.1` vs `localhost`
 *      这种最常踩），cookie 就带不过去 ⇒ 直接被路由守卫丢到登录页，
 *      用户看到的就是"钱都付了反而掉登录了"。
 *
 * 放到新窗口后：原标签页始终留在应用内、登录态不丢；用户在新窗口完成操作 →
 * 关掉它 → 原页面自动刷新余额/进度。
 *
 * ⚠ 关于"弹出被拦截"：`window.open` 依赖浏览器的**瞬时用户激活**（约 5 秒内有效）。
 * 通常点击 → 请求 → 弹窗这条链路够快，不会被拦；但如果调用发生在**自动续投**这类
 * **没有用户手势**的场景（例如签完协议自动回跳到本页后继续下单），就大概率会被拦。
 * 因此这里**故意不回退成"当前标签页跳转"**（那正是要修掉的体验问题），
 * 而是返回 `'blocked'` 让调用方**不要谎报成功**，提示用户手动再点一次即可。
 *
 * 被拦时留下的「处理中」占位流水，后端有「超时自动释放占额」定时任务兜底
 * （`FinanceJobHandler#releaseStalePendingFlows`），不会把可用余额永久占死。
 */
export type BankFormOpenResult =
  /** 已在新窗口打开，调用方不要再提示"成功"、也不要跳页 */
  | 'opened'
  /** 是银行表单，但新窗口被浏览器拦截（已提示用户放行后重试） */
  | 'blocked'
  /** 根本不是银行表单：业务校验失败（拦截器已弹过原因），或本就无需跳转 */
  | 'not-form'

export interface OpenBankFormOptions {
  /** 打开成功后的提示文案 */
  tip?: string
  /** 新窗口被用户关闭后的回调，用于刷新原页面的余额 / 进度 */
  onClosed?: () => void
}

/**
 * 识别响应体里是否藏着「银行表单」。
 * 后端业务失败时返回的是 `{"code":400,...}` JSON（HTTP 200），由 axios 拦截器负责提示，
 * 这里只做廉价的 `<form` 探测，避免误报文成"支付成功"。
 */
export function isBankForm(html: unknown): html is string {
  return typeof html === 'string' && html.includes('<form')
}

/**
 * 在新窗口里打开银行收银台。
 *
 * @param html 后端返回的整页 HTML（内含自动提交到银行的 form）
 */
export function openBankForm(html: unknown, options: OpenBankFormOptions = {}): BankFormOpenResult {
  if (!isBankForm(html)) {
    return 'not-form'
  }

  const win = window.open('', '_blank')
  if (!win) {
    ElMessage.warning('浏览器拦截了新窗口，请允许本站弹出窗口后重试')
    return 'blocked'
  }

  win.document.write(html)
  win.document.close()

  ElMessage.success(options.tip ?? '已在新窗口打开银行收银台，请在新窗口完成操作')

  if (options.onClosed) {
    // 跨域（新旧窗口 host 不一致）时也能读到的只有 `closed`，
    // 它正是判断"用户是否关掉支付窗口"最合适的探针。
    // 加个 30 分钟上限，避免用户一直不关窗口时定时器常驻。
    const deadline = Date.now() + 30 * 60 * 1000
    const timer = window.setInterval(() => {
      if (win.closed || Date.now() > deadline) {
        window.clearInterval(timer)
        if (win.closed) options.onClosed?.()
      }
    }, 800)
  }

  return 'opened'
}
