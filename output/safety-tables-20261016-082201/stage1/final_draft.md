# 资金安全三机制速记表

> 一句话总纲：**入口幂等保不重、并发原子保不超、事后对账保不漏**。三层各自独立兜底，且每层都有落库痕迹可查——「钱不会错」不靠承诺，靠机制。

> 速记口诀：回调重复莫重复，**只认成功**再入账；累加必带**额度条件**，撤资先过 **CAS 闸门**；对账**每天心跳**留一行，异常笔笔有留痕。

> 路径约定：下表文件路径均相对于 `api/src/main/java/com/example/demo/`。

## 一、三机制总览

| 机制 | 一句话 | 防什么 | 核心技术 | 主战场 |
|------|--------|--------|----------|--------|
| 入口幂等 | 重复回调，不重复入账 | 银行重试推送导致重复记账 | 业务单号 + 流水状态机，幂等判据只认成功态 | 6 类银行回调入口 |
| 并发原子 | 条件更新，从机制上防超募 | 并发投标超募、并发撤资重复退款 | 数据库行锁 + 条件 UPDATE（CAS） | 标的已募集金额 |
| 事后对账 | 逐笔核对，异常留痕 | 漏记、金额不符、银行侧账实异常 | 定时任务 + 双向流水比对 + 心跳行 | 每日凌晨对账 |

## 二、机制一：入口幂等（重复回调，不重复入账）

### 2.1 幂等判据（最核心的一张表）

| 场景（同 transNo 的流水状态） | 是否算「已入账」 | 原因 |
|------|--------|------|
| 成功 | **是** | 直接确认应答 success，不再入账 |
| 处理中 | 否 | 否则回调被误判为重复通知吞掉，那笔钱永远挂在处理中 |
| 失败 | 否 | 超时兜底会把占位置为失败，迟到的成功回调仍须能结算入账 |

### 2.2 实现拆解

| 环节 | 做法 | 文件 |
|------|------|------|
| 统一出口 | 6 类回调（绑卡/充值/提现/投标/放款/还款）统一走 `respond()`：先落一条 BANK_TRADE 对账流水，再调 Service，应答 success/fail；异常不吞、原样抛出让银行重试 | `controller/api/BankNotifyController.java` |
| 幂等判据 | `existsSucceededByTransNo`：同单号且 status=成功才算已入账 | `service/impl/TransFlowServiceImpl.java` |
| 回调流程 | 验签 → 银行状态判断 → 金额正则校验 → bindCode 反查用户 → 幂等 → 入账 | `TransFlowServiceImpl`、`service/impl/LendItemServiceImpl.java` |
| 单号口径 | 充值/提现用平台发起时生成的 agentBillNo（发起即落处理中流水占位）；投标/放款/还款用银行流水号 | `TransFlowServiceImpl#resolveTransNo` |
| 在途状态机 | `savePending` 占位（FAILED 可重激活为处理中）；`settlePending` 只从 处理中/失败 结算到终态，不含成功（防迟到的失败回调把成功翻回失败） | `TransFlowServiceImpl` |
| 超时兜底 | 每 5 分钟扫描，超时的「处理中」扣款类流水（提现/投标/还款）置失败释放占额 | `jobhandler/FinanceJobHandler.java` |

## 三、机制二：并发原子（条件更新，从机制上防超募）

### 3.1 核心 SQL（背这一条就够）

```sql
-- 投标累加：行锁 + 剩余额度双条件，rows=0 即拒（已满或不足）
UPDATE lend SET invest_amount = invest_amount + #{amount}, invest_num = invest_num + 1
WHERE id = #{lendId} AND is_deleted = 0 AND status = 1 AND amount - invest_amount >= #{amount}
```

### 3.2 原子操作全家福

| 操作 | SQL 要点 | 防什么 | 文件 |
|------|----------|--------|------|
| 投标累加 | `invest_amount = invest_amount + ?`，条件 `status=1 AND amount-invest_amount>=?` | 超募 | `mapper/LendItemMapper.java` |
| 撤标回减 | `invest_amount = invest_amount - ?`，条件 `invest_amount >= ?` | 回减成负、并发互相覆盖 | 同上 |
| 退资闸门 | `UPDATE lend_item SET status=1 WHERE id=? AND status IN (0,2)` | 连点撤标/并发批量退资导致**同一笔退款两次** | 同上 |
| 镜像扣钱 | `UPDATE user_account SET amount=amount-? WHERE user_id=? AND amount>=?` | 余额扣成负数 | `mapper/UserAccountMapper.java` |

### 3.3 三层防线的定位（别搞混）

| 层 | 是什么 | 定位 |
|------|--------|------|
| 下单前校验 | `BalanceGuard.require` 余额判定 + 可用额 = 银行余额 − 在途（提现+投标+还款）+ 落「处理中」流水占位 | **防**：友好提示，非互斥 |
| 回调原子更新 | `atomicInvest` 条件 UPDATE，由数据库串行化 | **真闸门**：从机制上防超募 |
| Redis 剩余额度缓存 | `buildInvestForm` 入口快速拦截 | 仅提示；代码注释明示真闸门在回调 |

> 易错点：手写 SQL 里 `is_deleted = 0` **必须显式写**——`@TableLogic` 只对 MyBatis-Plus 自动生成的 SQL 生效，漏写会让已逻辑删除的标的继续被累加募集额。

## 四、机制三：事后对账（逐笔核对，异常留痕）

### 4.1 流程拆解

| 步骤 | 做法 | 留痕去向 |
|------|------|----------|
| 定时触发 | 每天 03:30（cron）+ Redis 分布式锁，多实例只跑一个 | `jobhandler/FinanceJobHandler.java#dailyReconciliation` |
| 拉流水 | 银行当日流水 vs 本地 `trans_flow`，以 transNo 为 key | `service/impl/ReconciliationServiceImpl.java` |
| 逐笔比对 | 金额一致=匹配；本地无=缺失（status=1）；金额不符=不一致（status=2，双方金额都记）；银行流水缺 transNo=单独计数不写库 | `reconciliation_record` 表 |
| 本地多出 | 银行查无的本地流水同样记缺失 | 同上 |
| 账实兜底 | 扫银行侧负余额/负冻结账户，逐条落差异（`RECON-NEGATIVE-` 前缀） | 同上 |
| 心跳行 | `RECON-HEARTBEAT-<日期>` 一天一行，重复运行就地更新 | 区分「没差异」与「压根没跑过」 |
| 对账日志 | 每次对账落一条 RECON 到 `logs/bank-trade/bank-trade.log`（保留 90 天） | `utils/BankTradeLog.java` |

### 4.2 为什么事后对账不可省

银行扣款类接口（冻结/提现/划转）**不校验余额**，前置闸门与回调原子闸门都挡不住极端并发与回调重放——库里曾出现 3 个负余额账户即为实证。前置闸门是「防」，事后对账是「查」：防不住的，逐笔核对逐一发现、笔笔留痕到人。

## 五、文件速记表（背这张就够）

| 文件 | 一句话 |
|------|--------|
| `controller/api/BankNotifyController.java` | 6 类回调统一出口，每笔回调整理成对账流水 |
| `service/impl/TransFlowServiceImpl.java` | 幂等判据（只认成功）+ 在途流水状态机 + 超时释放 |
| `service/impl/LendItemServiceImpl.java` | 投标回调处理 + 下单前全套前置校验 |
| `mapper/LendItemMapper.java` | 防超募、撤标回减、退资 CAS 三条原子 SQL |
| `mapper/UserAccountMapper.java` | 镜像账本原子加减/冻结/解冻 |
| `utils/BalanceGuard.java` | 投标/还款/提现前置余额闸门（只判定、不变更） |
| `service/impl/ReconciliationServiceImpl.java` | 对账主逻辑：逐笔比对 + 心跳行 + 账实兜底 |
| `jobhandler/FinanceJobHandler.java` | 每日对账（03:30）与超时释放（每 5 分钟）定时任务 |
| `utils/BankTradeLog.java` | 银行往来流水日志：出站 REQ / 回调 CALLBACK / 对账 RECON |
