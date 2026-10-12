/**
 * 入口闸门：本项目**只能从 `/login` 进入**。
 *
 * 背景（为什么要单独做这个）：
 * 登录态 `user_token` 是 **7 天有效、按 host 存的 cookie**。只要浏览器里还留着它，
 * 之前的路由守卫就会认为"已登录"，于是 `http://<host>/home`、`/invest/list`
 * 这类深链可以被直接打开 —— 观感就是"不用登录也能进系统"。
 *
 * 解决方式：加一张**按标签页隔离**的会话通行证（sessionStorage）：
 *   - 只有在本标签页**真正走完登录**（或在银行回跳落地页确认已有登录态）才发放；
 *   - 深链进入时即使 cookie 还在，没有通行证也一律回到 `/login`；
 *   - 刷新不丢（sessionStorage 随标签页存活），**开新标签页需重新登录**；
 *   - 退出登录 / 登录态失效（401）时吊销。
 *
 * ⚠️ 这是**前端体验层的门**，不是安全边界：真正的权限校验在后端 JWT 拦截器。
 * 这里的作用是保证"必须先看到登录页"，不承担防篡改职责。
 */
const ENTRY_KEY = 'ww_entry_granted'

/** 发放通行证：登录成功后调用 */
export function grantEntry(): void {
  try {
    sessionStorage.setItem(ENTRY_KEY, '1')
  } catch {
    /* 隐私模式下 sessionStorage 可能不可用：失败时退化为"每次都需登录"，不影响功能 */
  }
}

/** 是否持有本标签页的通行证 */
export function hasEntry(): boolean {
  try {
    return sessionStorage.getItem(ENTRY_KEY) === '1'
  } catch {
    return false
  }
}

/** 吊销通行证：退出登录、登录态失效时调用 */
export function revokeEntry(): void {
  try {
    sessionStorage.removeItem(ENTRY_KEY)
  } catch {
    /* ignore */
  }
}
