## 五、Redis 的用武之地（两用一弃）

Redis 在三机制里只干两件事、弃过一件事：定时任务防重跑靠分布式锁，投标入口快速拦截靠剩余额度缓存；早年那把按标的加的 Redis 锁因设计缺陷已整体移除。

| 用途 | 场景 | 定位 | 文件 |
| --- | --- | --- | --- |
| 分布式锁 | 每日对账（03:30）与超时释放（每 5 分钟）定时任务，多实例部署同一时刻只跑一个 | **防重跑**：定时任务重复执行会重复动钱 | `utils/RedisDistributedLock.java`、`jobhandler/FinanceJobHandler.java` |
| 剩余额度缓存 | 投标入口快速拦截（key：`lend:leftAmount:<lendId>`），满标后置 0 | 仅友好提示；真闸门在回调的 `atomicInvest` | `service/impl/LendItemServiceImpl.java` |
| （已移除）按标的加锁 | 原 `buildInvestForm` 里的 `lend:leftAmount:<id>:lock` | 误伤同标的不同用户、unlock 不校验持有者，已整体移除 | — |

**分布式锁三要点：** 加锁必带过期时间（防实例宕机后锁永久滞留）；释放必校验持有者 token（Lua 脚本比对再删，GET 与 DEL 非原子、分开写有竞态）；不可重入、不自动续期（任务时长须小于过期时间）。Redis 不可达时抛异常不静默放行——多实例重复执行会重复动钱。
