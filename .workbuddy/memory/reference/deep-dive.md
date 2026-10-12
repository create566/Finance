# 深度实证（从 MEMORY.md 拆出，按需 Read）

## 平台收费：只赚服务费（2026-10-08 字节码实证）
- 银行库只有 5 表（user_account/user_bind/user_invest/user_return/user_item_return），**无平台账户表**；服务费只留痕不划转：
  `user_return.vote_fee_amt`、`user_invest.vote_fee_amt`、`user_item_return.fee_amt`。
- ① **放款** `UserInvestServiceImpl.makeLoan`：解冻各投资人 `voteAmt` → `mchFee=new BigDecimal(map.get("mchFee"))`
  → `transferAccounts(borrower, ΣvoteAmt−mchFee)` ⇒ **借款人实收=募资额−服务费**（砍头息）；`user_invest.status==1` 幂等返回。
- ② **还款** `LendReturnServiceImpl.returnCommit`：逐条 `transferAccounts(toBindCode,transitAmt)` 并 Σ →
  **校验 `voteFeeAmt+ΣtransitAmt==totalAmt`**（不等抛 `HfbException(RETURN_AMMOUNT_MORE_ERROR)`）→
  `transferAccounts(fromBindCode,"-"+totalAmt)` 扣借款人 ⇒ **投资人拿全额本息，voteFeeAmt 即平台服务费**。
- **平台侧服务费一分未收**：`lend_return.fee`/`lend_item_return.fee` 在 `handleMakeLoanNotify` 硬编码 `ZERO`；
  投标 `voteFeeAmt="0"`；`lend.service_rate` **全项目零引用**（死字段）；**平台从不传 `mchFee`** → 银行 makeLoan 必 NPE，
  且发生在解冻投资人资金**之后**。
- ⚠️ **逾期还款必失败**：逾期传 `totalAmt=本息+罚息`，而 voteFeeAmt=0、Σtransit=本息 → 等式不成立；要让罚息归平台必须把 `fee` 落成罚息。
- ⚠️ **银行仅 4 类回调（绑卡/充提/投标/还款），无"放款回调"** → `/api/core/lend/notifyMakeLoan` 不会被该 jar 触发。
- 实证：user_invest 71 笔 Σvote 1509.3 万、user_return 62 笔、user_item_return 125 笔的 fee 字段全 0；
  平台 `lend_return` 48 行 fee 全 0；4 个已放款标的 `payment_admin_id` 全 NULL。

## 2026-10-08 数据核查（现存问题，逐条）
- 银行 `user_account` 有 **3 个负余额**（-100.00 / -650.60 / -1,308,223.87）。根因：银行 `transferAccounts` 是无条件加减、
  **扣款不校验余额**；平台 `BalanceGuard` 只在提交表单前读一次余额，挡不住并发/重复回调。
- 6 条 `trans_flow.status=0` 悬挂（2026-09-30，用户 33：充值 5×500 + 提现 1000；提现那条永久占用提现可用额）。
- 平台 `user_bind` 仅 10 条，多个投资用户（3–9/11/14/43）无绑卡记录 → 回调用 bindCode 反查用户会失败。
- `reconciliation_record` 0 行（对账定时未跑过或被跳过）。
- 3 号标的已下架(4)但仍留 5000 元 `lend_item.status=0` 未退资。
- 4 个还款中标(2/5/7/9)的投资记录仍是 `status=0`，未按满标置 2。
- 银行 `user_invest.benefit_bind_code='0'` 脏数据（2026-10-09 详查）：**原 11 条，已修 3 条，余 8 条**。
  - 根因：放款 `makeLoan` 的划转受益人取自 DB 行（**不是请求参数** `bindCode`），且循环内每轮覆盖 ⇒ 取「最后一行」；
    查 `user_account` 落空 → `transferAccounts` 第 87 行 NPE → 银行返 200+`<html>出错了</html>` → 平台吞成「银行无应答」。
  - 已修（银行 `user_invest`）：id 113(项目106)→`28ea52ac90a24c7e84f9ce7f85c9aacb`；id 101/102(项目10/7)→`3d4043d7ed6946abb25fc6358b2d49c3`。
    回滚凭据 `.workbuddy/tmp/bank_fix_benefit_bind_code_20261009_110157.sql`。
  - 余 8 条属 `LEND376245874344333312 / LEND376246197372850176 / LEND376246905031626752 / LEND20260908120000123 / LEND20260911150748896`，
    这些 agent_project_code 在平台 `lend` / `lend_item.lend_item_no` **完全不存在**（创建于 09-08~09-18，疑似同库他方数据）⇒ 无法映射，**未动**。
  - ⚠️ 平台 106 有 10 笔投标(60000) 但银行侧只有 1 笔(5000) ⇒ 修好后能放款成功，**只会划转 5000**（其余 9 笔是 09-30 直接种进平台、从未走银行 vote）。

## 智能客服会话：MongoDB
- 链路：`finance-ai` → MongoDB `mongodb://localhost:27017/ww_finance_ai`（`MONGODB_URI`），集合
  `chat_session`（@Id Long，手写 AtomicLong 自增）/ `chat_message`（@Id ObjectId，靠 `sessionId` 关联）。
- ⚠️ 遗留死链路：`api` `UserChatController`(`/api/core/chat/**`) + MySQL 同表是迁移前旧实现，前端 0 引用。
  **改客服功能改 Mongo 那条。** LLM 上下文记忆仍在 **MySQL `chat_memory`**（conversationId=`session-<id>`）。
- **删除级联已实现**，收敛在 `MongoSessionHistoryService.deleteSession(id)`：删 `chat_message` + `chat_session` +
  清 `chat_memory` 两条 key。用户端 `DELETE /ai/sessions/{id}`（先 `ownsSession` fail-closed）与管理端
  `DELETE /ai/admin/chat/session/{id}`（**无归属校验**）同调该方法；管理端无角色分级。
- 查库：`mongosh.exe "mongodb://127.0.0.1:27017/ww_finance_ai" --quiet --eval '...'`。

## AI 配置谁生效
- ✅ `ai_chat_model_binding.scene=intent/chat/rag`（模型+提示词+知识库）；`prompt_config.scene=intent/chat/rag`(is_default=1)
  （未被绑定覆盖时）；`prompt_config.prompt_key=UNSAFE_KEYWORDS/HANDOFF_KEYWORDS`；`ai_model_config` chat+test_status=1+status=1。
  ❌ `prompt_config.scene=risk/audit/summary`；绑定 scene=chitchat/summary/safety/answer（已归档）。
- 绑定=覆盖层、Prompt=默认层；查生效 `GET /ai/admin/chat/model/effective`，真源 `PromptSelector.pickEffectiveByScene()`。
- 关键词切分 `KeywordUtils.split`（finance-ai `com.kzip.app.util`，**不在 api**）；`content_type='keywords'` 不参与场景选择；
  两张关键词表在用 → 禁删/停用。
- 改 Prompt 走 `ChangedEvent`(AFTER_COMMIT) 刷缓存；三入口 `/ai/chat`、`/ai/chat-stream`(SSE 主链路)、`/ai/chat-sync`
  均前置拦截，SSE 走响应头 `X-Guard`；改 scene 字面量同改 Java 常量 + `sql/fix_ai_config.sql` + 前端 sceneText。
  `binding.model_name` 当真实模型名发出；apiKey 掩码回传，入参掩码则沿用。

## 测试与联调
- 单测 18 个 `*Test.java`（12 个纯 Mockito 离线可跑，99 用例）；白名单外：TokenFlowTest/CrudTest/DbConnectTest/
  BankBindTest/DemoApplicationTests。**多类逗号分隔**；`-Dtest=A+B+C` 静默跑 0 个却 BUILD SUCCESS，跑完核对 `Tests run:`。
- Postman `docs/postman/`（2026-10-09 扩到 **105 请求/12 文件夹**，覆盖全部 103 接口），**唯一真源 `generate_collection.py`**（不发请求）。
  集合级 Pre-request 自动登录取 `adminToken/userToken/investorToken`（JWT exp 提前 60s 刷新 + 401 自愈重放）⇒ 导入即跑免管 token。
- JMeter `docs/jmeter/ww_finance_benchmark.jmx`：SetupThreadGroup 三方登录取 token → `props.put` → 主场景 `__P(adminToken)`；
  只压只读接口（写接口有脏数据风险）。CLI `jmeter -n -t ... -l result.jtl -e -o report`。⚠️ **本机未装 JMeter，从未实际执行过**。
- 联调范式 `_token.py`（自签 JWT，秘钥 `vsofo-5grcs-secret`，jjwt 0.9.1 先 base64 解一次）、`_e2e_market.py`、
  `_e2e_owner_isolation.py`；惯例打 8991。
- **执行器** `.workbuddy/tmp/run_api_tests.py`：`RUN_WRITES` 控是否跑写操作、`--html-only` 只重出报告；
  `AUTO_BIND`(响应取值回填下游请求) / `AUTO_BIND_WHERE`(按条件挑行，如 repayId 取 status=0) / `AUTO_BIND_MAX_BY_NAME`(save 后回查 max id)。
  `logout` 必须放最后跑。报告落 `docs/test-reports/`；截图 `.workbuddy/tmp/shot_report.js`（playwright + 显式 chromium-1228；
  **agent-browser 在沙箱内挂起不可用**；截图脚本按板块滚动截）。
- **实测结论**：只读轮 **58/58 = 100%**；全量轮（含写操作）**105 用例 / 90 通过 / 15 前置未满足 = 85.7%**。
  15 条失败全是环境前置缺口（无待发布标 / 无待审核借款 / 无认证中记录 / 缺身份证图 / 未签《出借协议》·《资金存管协议》 /
  测试账号银行余额=0 / 当次 9090 未起），**非代码缺陷**。
- ⚠️ **写用例踩坑（已实测踩过，务必遵守）**：
  - `dict/listByCode` 与 `dict/category` 的参数名都是 **`dictCode`**（传 `code` → 静默建 `dict_code=NULL` 脏分类）；
    `dict/option` body `{"parentId":"{{dictId}}","value":1}`（Integer）；`dict/update` 传 `{"id":"{{dictId}}","parentId":1,...}`
    避开唯一键 `uk_parent_id_value(parent_id,value)` 自撞。`dict/category` 返回体里取新 id 回填 `dictId`。
  - `contract/template/{id}/status` 的 `status` 是 **query 参数**（`.../status?status=1`），不是 body。
  - `integralGrade/save|update` 字段是 `integralStart/integralEnd/borrowAmount`（不是 minAmount/maxAmount）；
    **save 只返 `data=true`**，必须回查取 max id 再改/删，否则误删业务等级（曾误删 id=1）或字典 ROOT。
  - `contract/prepare` 必须用**借款人本人名下**的标（fixture `prepLendId=5`）；`chat/message/list` 需本人会话（用户 30 fixture 会话 id=55）；
    `lendReturn/repay/{id}` 的 `repayId` 取 `myBorrowList` 里 status=0 的行。
  - 新增接口后必须重跑 `generate_collection.py` 并**回填真实 id**（尤其 borrow/record·borrower·investor 的 page），否则拿默认 1 打。
  - **写操作前先 `mysqldump -uroot -p123456 ww_finance`**；收尾清理测试产物（测试字典/积分等级/模板版本/充值占位流水），
    核对配置表回到基线。⚠️ **银行库（阿里云 RDS）是唯一不可回滚面**。
- 🔴 **接口层真缺陷（待修，非前置问题）**：① `contract/prepare` 非签署方调用 → 撞 `uk_biz` → **500 + msg 泄露 SQL 异常**（应 403）；
  ② `dict/category` 传错键名静默建脏分类（无校验）；③ `dict/update` 撞唯一键返通用 500；④ `template/{id}/status` catch 只捕
  `ResourceNotFoundException`；⑤ 一批写接口把 DB 约束冲突冒泡成 `500 系统内部错误`。
- 使用说明 `docs/postman/接口测试使用说明.md`（含两轮结果 + 缺陷清单）。

## Chroma 向量库（Docker）
- 容器 `ww_chroma` = `ghcr.io/chroma-core/chroma:1.0.0`，:8000，volume `chroma_data` → `/data`
  （`chroma.sqlite3` + 每集合一个 HNSW 目录）。⚠️ 必须用 **/api/v2**，v1 已 Unimplemented。
- ⚠️ **数据不在默认租户**：tenant=`SpringAiTenant` / database=`SpringAiDatabase`（`CommonConfiguration.java:53,57` 硬编码）。
  查默认租户只得 `[]`，会误判"没数据"。
- 实际：集合 `pdf_rag_collection`（dim 1024, cosine），`/count`=**18**，全部属 `docId=22`（用户使用手册）。
  MySQL `knowledge_doc` 12–21 的 chunk_count 是历史遗留 → **RAG 目前只能命中这一份文档**。
- `query_texts` 不可用（`embedding_function=null`，要 `query_embeddings`）；`where`/`include` 正常。⚠️ 禁用 delete/update 接口。
- 容器内无 python3/sqlite3（只有 `chroma` CLI）→ 直查 sqlite 要 `docker cp ww_chroma:/data/chroma.sqlite3` 出来读。
- **项目根 `_chroma.sh`** 是只读查询入口：`cols` / `count` / `ls [n]` / `doc <docId> [n]` / `meta` / `sql "..."` / `du` / `logs [n]`。
- 磁盘：`/data` 43M 中 41M 是 `dd844d28-…/data_level0.bin`（纯 HNSW 预分配空壳）；`49007907-…` 是 sqlite 无引用的孤儿索引目录。

## 合同与标的退出（完整版 · 2026-10-09 整改后）
- **5 类合同**（`ContractTypeEnum`）：LOAN 借款合同 / INVEST 出借协议 / PAYMENT 放款确认书 /
  **SERVICE 居间服务协议** / **DEPOSIT 资金存管协议**。后两类 2026-10-09 新增（原为"完全不存在"）。
- `contract` 表靠 `contract_type` + `biz_type`(`LEND`/`BORROW_INFO`/`LEND_ITEM`/`USER`) + `party_b_id` 区分；
  状态 0待签/1已签/2已作废；`revoke()` 只作废待签署且 reason 必填 → **批量作废要逐个 try/catch**。
- **模板版本化**：INSERT version+1(status=1)+旧版置 0，运行时 `status=1 ORDER BY version DESC LIMIT 1`。
  当前启用：`LOAN v3` / `INVEST v2` / `PAYMENT v1` / `SERVICE v1` / `DEPOSIT v1`。SQL：`sql/contract_loan_party_fix.sql`、`sql/contract_service_deposit.sql`。
  ✅ **主体口径（答辩必用）**：`TPL_LOAN` v3 正文 = **甲方（借款人）/ 乙方（全体出借人）**，与 `party_a_id=借款人` 一致；
  ⚠️ `party_b_id` 仍 NULL 是"每人一份"的**有意建模**（双边合同是《出借协议》），**别再当缺陷报**。
  ⚠️ MySQL `'\n'` 是真换行；`||` 是逻辑或，拼接用 `CONCAT()`。
  ⚠️ **模板正文里存的是真实换行（CHAR(10)）**，不是字面 `\n`；前端 `pre-wrap` 渲染。
- **签署时机**：LOAN=上架前 `publish()` + 建标 `createLend()` 预生成；INVEST=投标时（`buildInvestForm` 硬前置）；
  PAYMENT=放款前 `buildMakeLoanForm()`；**SERVICE=发标(`publish`) + 投标(`buildInvestForm`)**；**DEPOSIT=绑卡(`BankBindController`)**。
- **用户级一次性协议**（SERVICE/DEPOSIT）：`biz_type=USER`、`biz_id=party_a_id=userId`、`party_b_id=NULL`、`amount=0`。
  ⚠️ amount **不能传 NULL**（MySQL 唯一键对 NULL 不去重 → 重复生成）。
  服务：`getOrCreateUserAgreement(type,userId)` / `assertUserAgreementSigned(type,userId)`；
  **模板缺失时降级放行**（`loadEnabledTemplate` 返 null → 直接 return），部署顺序不敏感。
  乙方只落名称不落 id：SERVICE→`旺旺金融平台`，DEPOSIT→`模拟存管银行`（都不是 user 表用户）。
- **可见性 ≠ 签署权**：`pageMy`/`canRead` 在"甲或乙==我"之外，另加"我投资过的标的的 LOAN/PAYMENT"分支
  （`myInvestedLendIds` 查 `lend_item.invest_user_id`）⇒ 投资人能**只读**看到《借款合同》。
  `detail` 返 `signable`（= 甲或乙），前端据此隐藏签署入口并标"非签署方（只读）"。`sign()` 仍严格按甲乙判权。
- ✅ **`syncMyContracts` 补建范围**：借款人标的除 FAILED/OFFLINE 外**全补**（原只 PENDING/FUNDING ⇒ 存量满标标的补不出）；
  《放款确认书》改为 `status >= COMPLETED`；**新增用户级协议补建**。前端进 `/contract/my` 时静默同步一次（幂等）。
- ✅ **导出**：`GET /api/core/contract/auth/export/{contractId}` → `exportFile` 返 `{fileName, content}`（内部 `canRead` 鉴权）。
- ⛔ **仍未改**：① 罚息归平台（`LendReturnServiceImpl:817-823`）；② 满标不生成合同/不签章（有意：建标预生成，
  避免生成逻辑进资金事务）；③ 正文未渲染还款计划表（v3 只补逾期罚息规则：日罚息=年化÷365×1.5，上限本金 100%）。
- `offline(lendId,adminId)` 必须传操作人（null 拒），落 `offline_admin_id`/`offline_time`（NULL=系统处置）；
  人工与 `offlineBySystem()` 共用 `offlineInternal`，**不要放宽接受 null**；消息 MAKE_LOAN_TIMEOUT(31)/LEND_OFFLINE(29)。
- 退资出口仅 PENDING(0)/FUNDING(1)/COMPLETED(2)，不含 REPAYING(5)/SETTLED(6)；`offline()` 准入与
  `withdrawAllInvestments` 的 `status IN (0,2)` **必须成对**，管理端 `v-if` 同步。
- **宽限期起算 `complete_time` 不是 `update_time`**（后者 `on update` 重置）；满标时 `LendItemServiceImpl:319` 写；
  `MAKE_LOAN_SIGN_DEADLINE_DAYS=3` 须与合同模板一致；`refundUnsignedCompletedLends` 只处理 `status=2`(COMPLETED)，
  三边界：complete_time 非空 / PAYMENT 未签 / payment_admin_id 空。
- ⚠️ `LendStatusEnum` 8 态数字：0待发布/1募资中/2已满标待放款/3已逾期/**4已下架**/5还款中/6已完结/7已流标。
- 平台约定补充：前端回传 createTime/updateTime → 三个 `updateWithCheck` 置空交 `MyMetaObjectHandler`。
  `Message.readFlag` 是 JSON 字段名。`BankBindController.bindDirect` 姓名/身份证取登录用户自身，不接受请求体。
- **测试联动**：`BankBindTest` 是 `@SpringBootTest`（真 bean），绑卡加了 DEPOSIT 闸门后，
  用例里必须先 `getOrCreateUserAgreement(DEPOSIT,userId)` + `sign(id,userId,1x1png,"127.0.0.1","junit")`，
  否则 `GET /api/user/bind` 返的是"请先签署《资金存管协议》"HTML 而非绑卡表单（断言 `<form` 会红）。

## 日志 / Nacos / Gateway / OSS（完整版）
- 5 模块各有 `logback-spring.xml` → `logs/<模块>/<模块>.log`（SizeAndTimeBased，单文件 50MB/留 30 天/总量 3GB）；
  ⚠️ 根**必须绝对路径** `D:/桌面/实训/demo/logs` —— `mvn spring-boot:run` 的 working dir 是**模块目录**，写 `${user.dir}` 会让日志目录随启动方式漂移。
- Tomcat 另有 `logs/access/<svc>-access.<yyyy-MM-dd>.log`（api/ai/message/oss 的 `server.tomcat.accesslog`，`directory` 也须绝对路径，否则相对 base dir）；
  **gateway 走 WebFlux/Netty 无此机制**；前端无文件日志。银行 jar 无源码 → 用 `run_bank.sh` 传 `--logging.file.name`，其 mybatis 是 `StdOutImpl`，**SQL 只进 stdout**。Chroma 走 `docker compose logs chroma`。
- **Nacos 未跑且被刻意旁路**：api `nacos.discovery.enabled=false` + `service-sms.ribbon.listOfServers=localhost:8120`；message discovery/config 全 false；ai 无依赖。
  ⚠️ **gateway/oss 缺 Nacos 是启动直接失败**（`NacosServiceRegistry.register` 抛 NacosException → `Failed to start bean 'webServerStartStop'`）；yml 账号 `nacos/shangqiuqwjk832`。
- **手工起 Nacos v2.2.3**（实际路径 `D:\桌面\Nacos\nacos-dist\nacos`，8848）：`.\startup.cmd -m standalone` —— PowerShell **不能省 `.\`**；
  ⚠️ 脚本第 27 行默认 `MODE="cluster"`，**不带 `-m` 会走外置 MySQL 分支 → `db.num is null` → 启动失败**（非 JDK 问题）；走 `JAVA_HOME`（JDK 17 已实证兼容 2.2.3）；根目录 `start-standalone.bat` 写死 JDK 24，**别用**。
  🔴 **2026-10-10 实证的第二症状（cluster 模式起来了，但拒收注册）**：若 Nacos 以 cluster 模式成功启动（`nacos.log` 会写 `Nacos started successfully in cluster mode. use external storage`），
  单节点会误判注册请求应转发给「其他节点」→ 回重定向 → 客户端跟随后又被判非法 → 客户端拿到
  **`ErrCode:400 {"message":"receive invalid redirect request from peer 127.0.0.1"}`**，Spring 侧表现为
  `NacosServiceRegistry.register` 失败 → `Failed to start bean 'webServerStartStop'` → `Application run failed`（**进程直接退出，端口不监听**）。
  判据：`nacos.log` 搜 `started successfully in` —— 必须是 `stand alone mode. use embedded storage`；`logs/naming-server.log` 同刻会记 `ERROR receive invalid redirect request from peer`。
  ⚠️ 该模式下**所有注册方（api/gateway/message/oss）一起挂**，且报错文案与「Nacos 没起（Connection refused）」完全不同，别混为一谈。
  🔴 **2026-10-10 实证的第三症状（改为 standalone 后仍挂：403）**：`ErrCode:403 ... user not found!`（Spring 侧同为
  `NacosServiceRegistry.register` 失败 → 进程退出）。根因是 **`conf/application.properties` 第 138 行 `nacos.core.auth.enabled=true`**
  （**Nacos 2.2.x 起默认开启**，非人为改动），而各服务 yml **刻意不配 username/password**（注释里写明假设本机 auth 关闭）——
  匿名注册被鉴权拒绝。⚠️ **更正**：此前本文件误记为 `auth.enabled=false`，实测为 `true`。
  **修法（一行，已做，备份 `conf/application.properties.bak_20261010_095322`）**：改 `nacos.core.auth.enabled=false` 后重启 Nacos。
  另一处同批修复：`bin/startup.cmd:27` 默认 `MODE="cluster"` → `standalone`（备份 `bin/startup.cmd.bak_20261010_095322`），免得下次双击又回 400。
  **判据（实证）**：`GET /nacos/v1/console/health/readiness`→`OK`；**匿名** `POST /nacos/v1/ns/instance?serviceName=x&ip=1.2.3.4&port=1`→`ok`（若返 403 即鉴权未关）；
  `logs/api.log` 出现 `nacos registry, DEFAULT_GROUP service-core <ip>:8990 register finished` + `Started DemoApplication in 51.46 seconds`。
  服务侧只需重启，**jar 不用重打**（yml 本就无账号密码）。
- ⚠️ **硬杀 Nacos 的副作用**：`taskkill /F` 后 `data/derby-data/db.lck` 残留 ⇒ 下次启动报
  `load derby-schema.sql error`（栈底 `java.sql.SQLTimeoutException: Login timeout exceeded` / `org.apache.derby...StandardException: Login timeout exceeded`）
  —— 这是**内嵌 Derby 被旧进程锁住**，不是 schema 坏了。**删 `data/derby-data/db.lck` 重起即可**（本项目 Nacos 纯注册中心、无配置条目，Derby 数据可弃）。
- ⚠️ **本沙箱手动起 Nacos 必须显式 `--server.port=8848`**：环境会给子进程注入 `SERVER_PORT`，Nacos 作为 Spring Boot 应用会读它并绑到随机高端口
  （实测绑到 63658，且该端口另有鉴权代理响应 `{"error":{"code":"AUTH_REQUIRED"}}`，极具误导性）。加 `-Dserver.port=8848 --server.port=8848` 可解。
- ⚠️ **本沙箱 Bash 工具的后台进程在每次调用结束即被回收**：`nohup … &` 起的 Nacos/服务都不能跨调用存活 ⇒ 要验证「Nacos + manage.sh start」必须在**同一次**调用内串起来做完
  （实测：WAIT_PORT=75，api 51s 起完，api+gateway+message+oss 合计约 3 分钟）。这也是「端口刚开又关」的假象来源，**别误判成服务崩溃**。
- **OSS 两套同路径实现，生效的是 api 那份**：api `OssFileController` `/api/oss/file`（仅图片/需登录/归属校验已修/存 `uploads/`，前缀 `/api/knowledge/files`）；
  `finance-oss FileController` 同路径（**不校验登录、删除无归属校验=IDOR**、白名单含 pdf）**是死代码**。
  user baseURL=`/api` → 实际打 `/api/oss/file/upload` → 8990 命中 api。⚠️ **走网关会先命中 `/*/oss/**`→finance-oss**，先修它再用网关。
- Gateway(8088) 可选、当前未用（`/*/core/**`→service-core、`/*/sms/**`→service-sms、`/*/oss/**`→service-oss、`/ai/**`→8081，带 CorsConfig 5173/5174）。

## 安全缺口（2026-10-08 完整版）
- 隔离三层：JwtAuthInterceptor（token 与 Redis 相等）+ LoginUserContext(ThreadLocal) + Controller 用登录态 userId 下推 SQL。
  ✅ OSS 越权已修（`images/u<id>/…` + `deleteOwned` 三段匹配，失败 403），**新上传必须走三参 upload**。
- ⚠️ **管理端无权限分级**：`sys_admin` 无角色字段，`TokenUtil:91` 硬编码 token_role=admin，`@PreAuthorize`/`@Secured` 全项目零命中
  → 任何 admin 能审核/下架/放款/作废合同/删他人会话。
- ⚠️ `GET /api/core/user/checkMobile/{mobile}` 在 exclude 白名单 → **未授权手机号枚举**；`GET /api/core/lend/{id}` 返回完整 Lend（含借款人 userId）。
- 生产缺口：无 profile 隔离；凭据 `${ENV:明文默认值}` 硬兜底；无 Dockerfile/actuator/监控/备份；无方法级鉴权/脱敏/乐观锁（`@Version`、`FOR UPDATE` 零命中）/集成测试。
  运行时硬依赖：Docker(redis+chroma)、`ww_bank-1.0.0.jar`(手动)、MongoDB(27017)、`finance-message`(8120)。

## docs/ 与两个数据目录
- docs/ 有效：`postman/`、`shots/`、`业务概述.docx`（交付正文；3 处偏差：消息「28 类」实为 31、定时「共三项」实为 4 项、
  称下架资金处置待完善但已实现）。留追溯：`MODULE_OPTIMIZATION_REPORT.md`、`合同签署与标的推荐_方案设计.md`（端口 8110 错）、
  ER 图四件套（18 实体缺 contract 三表，html 混入源码已于 10-08 剥离，备份 `%TEMP%\er-diagram.original-contaminated.html`）。
  docs/ 无独立流程图。
- `pdf-storage/` = finance-ai `LocalPdfFileRepository:46` 写的 PDF 归档（相对路径 → 启动工作目录）；
  ⚠️ 索引 `chat-pdf.properties`（项目根）已损坏（465B 全 NUL）→ `/ai/pdf/file/{chatId}` 全 404。
- `output/` = tencent-docx 流水线工作目录（`pipeline-state.yaml` 为权威状态机）。
- `logs/40fceb80-*.json` 是文档流水线产物，不是应用日志。

## 【2026-10-09 14:20 全闭环验收实证】标的一次完整循环 + 平账核查
**标的**：lend **101** `LEND20261009120023716`（全闭环验收标-张三，10000.00 / 12 期 / 8%）
**角色**：借款人 33（`15138019132`）/ 投资人 22（`17431209467`）/ 管理员 admin。**实例=隔离 8991**（8990 跑旧类，SERVICE prepare 仍报「标的不存在」）。

### 流程与合同（全部走通）
| 关口 | 合同 | 结果 |
|---|---|---|
| 建标（admin `create` 直置 FUNDING，**绕过 publish()**） | — | LOAN/PAYMENT 预生成 |
| 投标前置 | INVEST #20 | ✅ status=1 |
| 投标前置 | SERVICE #9 (USER,22) | ✅ status=1 |
| 放款前置 | PAYMENT #19 | ✅ status=1（**放款闸门在此**）|
| 上架前置 | LOAN #18 | status=0 —— **未被校验**，因走的是 admin create 捷径 |
- ⚠️ **LOAN 未签却能放款**「不是漏洞」：`LendServiceImpl.java:279` 只在 `publish()` 里校验 LOAN；`AdminLendController.create` 直接置 `status=FUNDING` 绕过了 publish。走 `publish()` 的正常路径会强制 LOAN 签署。**答辩口径按此说。**
- 放款：`PUT /admin/core/loan/project/101/makeLoan` → 银行 `resultCode=0000`「放款成功」。

### 还款计划（12 期，等额本息）
- 月供恒 **869.88**（末期 869.94 补差）；Σprincipal=**10000.00**、Σinterest=**369.00**、Σtotal=**10438.62**。
- `lend_item_return`（投资人侧 12 期）与 `lend_return`（借款人侧）**逐期 principal/interest/total 完全一致**（单投资人 → 100% 占比直切）。
- 第 1 期已还：借款人 -869.88、投资人 +869.88，双双 status=1。

### 平账核查（本轮增量 vs 存量漂移）—— 关键结论
银行库在 RDS，**镜像 `ww_finance.user_account` 是纯 ±增量账本**（`UserAccountServiceImpl.applyAtomicUpdate`，非定期从银行重拉）：
| 账户 | 本轮前(11:33备份) | 本轮后 | Δ实际 | 理论Δ | 判定 |
|---|---|---|---|---|---|
| 投资 22 | 12131.57 | 43001.45 | **+30869.88** | 4×充值40000−投标10000+回款869.88=**30869.88** | ✅ 精确 |
| 借款 33 | 14431.31 | 23561.43 | **+9130.12** | 放款10000−还款869.88=**9130.12** | ✅ 精确 |
- **两侧增量 100% 对得上** ⇒ 本轮全链路（充值/投标/放款/还款）**资金完全平账**。
- 银行侧同向移动：投资人 `9690e647…` = **43001.45**（=平台，差 0）；借款人 `3d4043d7…` = **15571.43**（平台高 **+7990.00**）。
- 🔴 **7990.00 是「本轮之前就存在的存量漂移」**，本轮未引入、未放大（两侧同加 9130.12）。根因=镜像开账基准与银行历史不一致（账户行开于 09-28，非清零重建）。**结论：不影响资金正确性判定；若要彻底消偏需以银行为准重设镜像基准。**
- `trans_flow` 8 条**全 status=1，无悬挂**；INVEST 占位流水已被银行回调正常结清（12 号 status=1）。
- 银行侧凭据：`user_invest` #119（`agent_project_code=101`, vote_amt=10000, status=1）；`user_return` #99（869.88）→ `user_item_return` #171（transit 869.88 = base 803.21 + benifit 66.67）——**与平台逐字段对齐**。
- 🔵 **日志路径注意**：makeLoan 走 `RequestHelper` → **只进 `logs/api/api.log`**（`加密前：`/`同步放款请求银行`/`旺旺银行应答结果`），**不进 `logs/bank-trade/bank-trade.log`**；还款两段式才进 bank-trade。**对账取证要同时看两处。**
- `reconciliation_record` 仍 0 行（对账心跳由 03:30 定时任务写，一天一行，**别当差异**）。

## 构建与打包（完整版 · 2026-10-09 实证）
- 父 pom 是自建 `ww_finance`（**不用** `spring-boot-starter-parent`）⇒ `spring-boot-maven-plugin` **必须显式写**：
  `<executions><execution><goals><goal>repackage</goal></goals></execution></executions>`。
  ⚠️ 只配 `<classifier>exec</classifier>` **无效** —— classifier 只决定产物名字，executions 才决定「要不要打」；
  症状是 `mvn package` BUILD SUCCESS 却只出 thin jar。**判据：日志里有没有 `spring-boot:...:repackage` 行。**
  2026-10-09 已补全 gateway/message/oss/ai（api 本来就有）。
- **上传产物 = 5 个 `-exec.jar` + `ww_bank-1.0.0.jar`**（common/base 不打不传）。
  **两套 JDK 硬要求**：api/gateway/message/oss/bank 用 **JDK8**，ai 用 **JDK17**。
  方案 `docs/服务器部署与测试方案.md`；脚本 `deploy/`，`start-all.sh` 顺序 bank→api→gateway→message→oss→ai→前端。
- ⚠️ **`lombok` 是编译期依赖，不该进 fat jar**（白占 1.94MB/包）⇒ plugin 加
  `<excludes><exclude>org.projectlombok:lombok</exclude></excludes>`（api 早有，message/oss 2026-10-09 补）。
- ⚠️ **瘦身（2026-10-09）**：message 62.0→**52.0MB**、oss 65.2→**55.1MB**（各 -10MB/-29 libs）。
  做法：对 `finance-base` 加 `<exclusions>` 排掉 **swagger UI 三件套**（`springfox-swagger-ui` /
  `swagger-bootstrap-ui` / `knife4j-spring-boot-starter`）+ **`spring-cloud-starter-alibaba-sentinel`**。
  ⛔ **`springfox-swagger2` 不能排**（见下）。

## 模块依赖的真实关系（易踩）
- ⚠️ **`finance-message` / `finance-oss` 依赖 `finance-base` 不是为了用它的类（源码一个都没 import）**，
  而是为了它的 3 个配置类被 `@ComponentScan("com.wwfinance")` 捡到：
  `WebMvcConfig`（Long→String 防精度丢失 + 时间格式化）、
  `RedisConfig`（`RedisTemplate<String,Object>`，**message 的 ApiSmsController 注入它，去掉即启动失败**）、
  `OpenFeignConfig`。
- ⚠️ **`Swagger2Config`（base）是裸 `@Configuration @EnableSwagger2`，无 `@ConditionalOnClass`**
  ⇒ 被扫到就必须有 springfox 的 `Docket`，**排掉 `springfox-swagger2` 会启动失败**。
  要彻底去 swagger 需先加 `@ConditionalOnClass(Docket.class)`（未做）。
- ⚠️ **`finance-common` 引了 `spring-boot-starter-security`**，而 message/oss 启动类写了
  `exclude = SecurityAutoConfiguration.class` —— 但代码层仍引用其类，**导致 security 无法剥离**（2.09MB/包，弃疗）。
- ⚠️ message 的 `nacos-discovery` **靠 `finance-base` 传递**（自己只引 `nacos-config`）
  ⇒ 动 finance-base 前必查，否则注册**静默失效**。oss 已显式引。

## 2026-10-10 从 MEMORY.md 迁出的次高频细节

### 银行收银台 / 回跳地址（Phase C 修复实证）
- ✅ **银行收银台一律新窗口**：`ww_finance_user/src/utils/bankForm.ts#openBankForm` 三态 `opened|blocked|not-form`；被拦住时**不回退当前标签页、不谎报成功**；
  `invest/detail.vue` / `borrow/repay.vue` / `account/overview.vue` **三处共用**。
- ✅ **银行 6 个 returnUrl 全指免登录结果页 `/bank/return`**（`HfbConst.RETURN_PAGE_PATH`）。旧值 `/home` 有两个致命点：不在免登录白名单 + 回跳是整页加载，
  而 `user_token` 按 host 存 cookie ⇒ **host 一变就掉登录页**。⇒ 已加 `RETURN_PAGE_PATH` 到白名单。
- ✅ **回跳地址按请求 Origin 动态推导**：`HfbConst.returnUrl()` → `resolveReturnUrl(currentRequestOrigin())`（取 `Origin` → 退 `Referer` → 配置兜底，再拼 `/bank/return`）。
  6 个出站调用点已改用。原因：一套部署有多条访问路径（`localhost:5174` / 公网 `http://120.26.132.5`），写死 `BANK_FRONT_BASE_URL` 治不了"跨 host 掉登录页"；
  `changeOrigin` 只改 Host **不改 Origin**，所以按 Origin 解析可行。
- ⚠️ 运行时验证：本地直连 → `http://127.0.0.1:5174/bank/return`；带公网 Origin（`http://120.26.132.5:81`）→ `http://120.26.132.5:81/bank/return`。
- ⚠️ **`BORROW_RETURN_URL` 等 5 个静态常量**只剩 `HfbConstTest` 引用 ⇒ 属"双套回跳地址"重复（**中风险，勿盲删**）。

### 旧实例缓存 `NoClassDefFoundError`（放款 500 根因）
- 🔴 症状：生成表单全绿、**唯独放款 500**，栈里 `NoClassDefFoundError: com/example/demo/Bank/RequestHelper`（或 `Map.of` 相关）。
- 根因：**运行中的旧 api 实例**在某个 class 之后才编译出来；实例**首次调用时类加载失败被 JVM 缓存**，此后该动作**持续 500**。**与代码无关，重启实例即恢复。**
- `RequestHelper` 只被**服务端出站同步调用**用到（放款 `makeLoanSync`）⇒ 症状高度特化。判据：日志里能看到实例启动时间早于 class 编译时间。
- ⚠️ 两处日志都要看：`实训jar包/logs/*.log`（manage.sh 重定向 stdout）+ `demo/logs/<svc>/<svc>.log`（logback 文件）。
  JDK8 栈帧 `Thread.java:745` / JDK17 `:840` 可用于区分实例身份。

### 在途流水占位兜底
- `trans_flow` 幂等 `existsSucceededByTransNo()` **只认 SUCCESS**；在途 `status=0` 占位由 `expireStalePendingFlows`（单据侧）
  + `FinanceJobHandler#releaseStalePendingFlows`（定时 5 分钟一轮，阈值 `job.pending-flow.timeout-minutes` 默认 10 分钟）兜底释放
  ⇒ **用户跳收银台不付款不再永久占死可用额**。

### 构建/验证三条新铁律（2026-10-10 实测，务必遵守）

1. **`-pl api -am` 的 reactor 不含 `finance-base`**。实测 reactor 顺序 = `ww_finance → finance-common → api`，
   因为 api/pom.xml 于 2026-10-08 把 `finance-base` 依赖「显式化」删掉了（改为逐条声明它带进来的库）。
   ⇒ **改 `finance-base`（如 `WebMvcConfig`）必须单独再跑 `-pl finance-base compile`**，
   否则你以为验证通过了、其实那个模块根本没编译。（SKILL.md 里"连带 finance-common/finance-base"的旧说法已改正。）
2. **编译/打包用 JDK8，跑单测必须用 JDK17**。测试源码（`FinanceJobHandlerTest:135`、`AvailableBalanceUnifiedTest:144,146`）
   用了 `List.of(...)` / `Map.of(...)`（Java 9+ API）。用 JDK8 跑 `mvn test` 会在 `testCompile` 阶段报
   「找不到符号: 方法 of(...)，位置: 接口 java.util.List/Map」——**这是既有问题，不是你的改动引起的**。
   JDK17 下父 POM 的 `-source 8 -target 8` 只出 obsolete 警告（解析走 JDK17 的 rt，所以 `List.of` 能过）。
3. **别只 `grep` maven 输出判断成败**。输出含中文/二进制时 `grep` 只回一行
   `Binary file (standard input) matches`，看着像"没有错误"。要么加 `-a`，要么最稳地汇总
   `api/target/surefire-reports/*.txt` 里的 `Tests run/Failures/Errors`（还能量出总用例数）。
   ⚠️ 报告目录会残留**上一轮**的 txt ⇒ 按 mtime 过滤（`os.path.getmtime` 取 10 分钟内的）才是本轮。
   **白名单当前口径：22 类 / 147 用例**（2026-10-09 记的 144 已过时）。

### ⚠️ `.workbuddy/` 下的文件可能被外部进程回收（2026-10-10 实证）
- `.workbuddy/tmp/` 里当天新写的 `.py` 脚本 **会在几十分钟内消失**；
  同一天 `MEMORY.md` 也被外部改写回了另一个更短的版本（mtime 与我的写入时间不一致、体积从 12.6KB 变 9.1KB）。
- ⇒ **脚本放技能自带的 `scripts/`**（`~/.workbuddy/skills/finance-demo-build/scripts/`），
  **知识放 `reference/deep-dive.md` / 当日 daily log / SKILL.md**（这三处实测能存活），
  MEMORY.md 只当"高频索引"，并且**同一条重要结论尽量不要在 MEMORY.md 里孤本存放**。

### 平台约定补充（2026-10-10）
- 分页统一 `PageUtils.of(pageNum,pageSize)`；`MAX_PAGE_SIZE=100` / `MAX_PAGE_NUM=10000`；
  ⚠️ **不可给 `PageUtils.of` 加 `(Long,Long)` 重载**（会与现有签名冲突）。
- 状态文案真源 `ww_finance_user/src/utils/enumText.ts`（LEND **8 态** / LEND_ITEM **3 态**，**绝不可合并**）。
  新增站内信类型须**三处同改**：Java 枚举（已到 31 类）+ 前端 `MESSAGE_TYPE_META` + 统计 SQL。
- 登录态：Redis key `login:token:<role>:<id>`，比对需 `5grcs ` 前缀 + 值完全相等；
  ⚠️ **Docker 未起 → 6379 不可达 → `/api`、`/admin` 全进不去**。
- 权限分级实现：`sys_admin.role` + JWT `token_admin_role` + 自定义 `@RequireAdminRole`；
  ⚠️ **不能用 `@PreAuthorize`**（未引入 spring-security 方法级拦截，全站 500）。P5-23~27 已修（`sql/fix_schema_drift.sql` 幂等）。
- ⚠️ **P5-68：`db_init.sql` 已无法重建线上库**（schema 漂移累积），**别按脚本重建**。
- 日志根**必须绝对路径** `D:/桌面/实训/demo/logs`；银行往来独立 `logs/bank-trade/bank-trade.log`
  （`REQ|`/`CALLBACK|`/`RECON|` 前缀，留 90 天）——**对账唯一凭据**。Gateway 只做路由 + CORS，**不做鉴权**。

## 🔴 "跨机（frp+公网）AI 不能删文件、本机 localhost 正常" —— AI 自有 CORS 白名单（2026-10-12 定位并修复）

**现象**：前端经 frp 暴露到云服务器（`http://120.26.132.5` 用户端 :80 / 管理端 :81），后端全部跑在本机。
别人电脑打开前端后 **AI 知识库删除文件失败**，本机 localhost 一切正常；api/oss 等其他模块也正常。

**链路**：浏览器(`Origin: http://120.26.132.5:81`) → nginx:81 → 网关 8088 → `/ai/**` → `http://localhost:8081`(AI)。

**根因**：`finance-ai/src/main/java/com/kzip/app/config/CorsConfig.java` 是一个
`WebMvcConfigurer`，带**自己独立的一套** CORS 白名单 `ALLOWED_ORIGINS`，原值只有
`localhost/127.0.0.1` 的 5173 / 5174 / 8081，**不含公网 IP**。

三个关键认知（否则会一直往 Nacos、frp、nginx 上找）：

1. **同源请求也会带 `Origin` 头**：浏览器对**非 GET/HEAD** 的请求（DELETE/POST/PUT/PATCH）一律附带
   `Origin`，哪怕目标与页面同源。而 Spring 的 `CorsUtils.isCorsRequest()` **只判断"有没有 Origin 头"**，
   不判断是否真的跨源 ⇒ 这些请求会被 Spring 当成 CORS 请求走白名单校验。
   ⇒ **表现就是"只有写操作挂、读取(GET)正常"**（同源 GET 不带 Origin）。
2. 白名单不命中 ⇒ `DefaultCorsProcessor.rejectRequest()` ⇒ 状态码 **403**、响应体纯文本
   `Invalid CORS request`（**不是** JSON、不是 401）——看到这个字符串就锁定 CORS。
3. **网关救不了**：网关 `finance-gateway/.../config/CorsConfig.java` 用的是 `CorsWebFilter`，
   白名单里**确实**已经加了 `http://120.26.132.5` 与 `:81`；但网关对**非预检**请求只是"加上 CORS 头然后转发"，
   **不阻止**下游服务再校验一次 ⇒ 请求照样会被 AI 拒掉（实测经 8088 + 公网 Origin 仍是 403）。

**为什么只有 AI 挂**：全项目**只有 finance-ai 模块自带 CORS 映射**。实测确认：
- `api` 的 `config/MyWebConfig.java`：`WebMvcConfigurer`，只做资源映射 + 拦截器，**无 CORS**；
- `finance-base` 的 `config/WebMvcConfig.java`：只改 Jackson 转换器，**无 CORS**；
- `finance-oss` 的 `UploadConfig` / `OssWebConfig`、`finance-ai` 的 `WebSecurityConfig`：**无 CORS**；
- AI 的 `ChatController` 顶部注释明确"跨域统一由 CorsConfig 处理"，也没用 `@CrossOrigin`。

**与 Nacos 无关**：AI 本来就不注册 Nacos，网关用静态 URI `http://localhost:8081` 直连它。
"只有 AI 没注册 Nacos"是同一个设计取舍，但**不是**这次故障的原因。

**实测证据**（`curl --noproxy "*"`，直连 8081）：

| 请求 | Origin | 结果 |
| --- | --- | --- |
| `DELETE /ai/admin/knowledge/base/999999` | `http://120.26.132.5:81` | **403 `Invalid CORS request`** |
| `GET /ai/admin/knowledge/base/list` | `http://120.26.132.5:81` | **403 `Invalid CORS request`** |
| `OPTIONS`（预检，`Access-Control-Request-Method: DELETE`） | `http://120.26.132.5:81` | **403** |
| `DELETE …` | `http://localhost:5173` | **401** `{"code":401,"msg":"未认证，请先登录"}` ⇒ CORS 已放行 |
| `DELETE …` | 不带 Origin | 401 ⇒ 放行 |
| 经网关 8088 `DELETE` | `http://120.26.132.5:81` | 403（网关放行后被 AI 拒） |

**修复**（最小、无损）：`ALLOWED_ORIGINS` 追加
`"http://120.26.132.5"`（用户端 :80）与 `"http://120.26.132.5:81"`（管理端 :81），
与网关白名单保持一致。**不要**为了省事写 `*`/`allowedOriginPatterns("*")`——
本类开了 `allowCredentials(true)`，通配符会被 Spring 直接拒绝，且等于放弃加固。

**验证**（2026-10-12）：
1. `-pl finance-ai -am package`（**JDK17**，plexus classworlds 启动器）→ `BUILD SUCCESS`，
   新产物 `finance-ai/target/finance-ai-1.0-exec.jar`；
2. `unzip` 出 `BOOT-INF/classes/com/kzip/app/config/CorsConfig.class`，`grep -a` 常量池确认
   两条公网来源已进包（`strings` 本机没有，用 `grep -a` 代替）；
3. 起**隔离实例 8082**（`--server.port=8082`，`LOG_HOME` 指向临时目录防止抢日志文件锁）：
   公网 Origin 的 DELETE / GET 均返回 **401**（CORS 放行、仅缺登录），预检 `OPTIONS` 返回
   **200 + `Access-Control-Allow-Origin: http://120.26.132.5:81`**；同刻旧 8081 仍 403；
4. 测完按端口找到 PID `Stop-Process`，确认 8082 关闭、8081 未受影响，清理临时日志目录。

**生效方式**：源码已改、jar 已重新打好，但**正在跑的 8081 仍是旧字节码**，需
把 `finance-ai/target/finance-ai-1.0-exec.jar` 覆盖到 `D:\桌面\实训jar包\`（先停 ai 再替，Windows 会锁 jar）
并重启该服务。⚠️ 判断"跑的是不是成品部署包"看 `D:\桌面\实训jar包\run\*.pid` 的 mtime。

**⚠️ 伴生隐患（本次未处理）**：网关与 AI **两层都会**写 `Access-Control-Allow-Origin`
⇒ 响应头里出现**重复的 ACAO**（实测经 8088 + `Origin: http://localhost:5173` 返回两条）。
当前前后端都是**同源**访问，浏览器不做 CORS 校验，所以不炸；**将来若真跨源**（前端与 API 不同 host/port），
浏览器会以 "The 'Access-Control-Allow-Origin' header contains multiple values" 拒绝。
要根治需让两者只保留一处 CORS（推荐：网关去掉 CORS、各服务只放行自己的真实来源；或反之）。


## 合同建模 · 细节（2026-10-12 从 MEMORY.md 下沉）
- 合同 5 类：LOAN(建标+上架前)/INVEST(投标前)/PAYMENT(放款前,即放款闸门)/SERVICE(居间,发标+投标)/DEPOSIT(存管,绑卡)。
- USER 级合同 `biz_id=party_a_id=userId`、`party_b_id=NULL` 为**有意建模**（"每人一份"，勿当缺陷）。
- `ContractBizTypeEnum`：LEND/BORROW_INFO/LEND_ITEM/USER；模板版本化=追加新行（不原地改）。
- `revoke()` 只作废待签且 reason 必填；`offline(lendId,adminId)` 必传操作人；唯一键 `uk_biz` 含 amount；`detail` 返 `signable`（可见≠可签）；宽限期起算 `complete_time`。
- `LendStatusEnum` 0待发布/1募资中/2满标待放款/3逾期/4下架/5还款中/6完结/7流标。

## 站内信 / 公开统计 / 收银台 · 细节（2026-10-12 从 MEMORY.md 下沉）
- 站内信类型三处同步：Java 枚举(已到 31)+前端 `MESSAGE_TYPE_META`+统计 SQL。
- 公开统计接口 `GET /api/core/index/statistics`（`IndexStatisticsController`，JWT+限流白名单已放行）：登录/注册页实时拉真实运营数据；取不到就隐藏，**不许写死假数字**。
- 银行收银台一律 `utils/bankForm.ts#openBankForm`（新窗口，三态；被拦不回退、不谎报）；6 个 returnUrl 全指免登录 `/bank/return` 且按请求 Origin 动态推导（`HfbConst.returnUrl()`）。
- 登录态 token：Redis `login:token:<role>:<id>`，值自带 `5grcs ` 前缀**原样**作 header（user `Authorization`/admin `token`）。
