# ww_finance（D:\桌面\实训\demo）长期项目笔记

## 银行库 = 远程阿里云 RDS，且已改为「启动不阻断」（2026-10-04 修复）
- `bankJdbcTemplate` 打的是**阿里云 RDS** `rm-uf6ry261ghx3995253o.mysql.rds.aliyuncs.com`（106.15.168.220），
  库 `ww_bank`，只用一张表 `user_account`（`user_code / amount / freeze_amount / is_deleted`）。
  **本地那个 `bank` 库是别的示例项目（emp/dept/salarygrade），跟本项目无关，别当它是银行库。**
- `BankDataSourceConfig` 已加 `setInitializationFailTimeout(-1)` → **启动时不预建连接**，
  银行库抖动不再让整个 Spring 容器 refresh 失败（改前是"一次抖动 = 服务起不来"）。
  失败代价由首次业务调用承担，且 `BankAccountSyncServiceImpl` 已兜底降级到平台 `user_account`。
- 同时加了 `connectionTimeout=8000` / `validationTimeout=3000`，可用 `bank-datasource.connect-timeout-ms`
  或环境变量 `BANK_DB_CONNECT_TIMEOUT` 覆盖。
- **判别"走的是银行库还是平台兜底"**：看提现超额文案里的"当前可用 ¥Y"。
  例：user 42 银行侧 `-650.60`、平台侧 `4349.40`，数值来源一目了然。
  模拟银行库不可达自测：`--bank-datasource.url=jdbc:mysql://10.255.255.1:3306/ww_bank?serverTimezone=GMT%2B8`。
- ⚠️ `api/src/main/resources/application.yml` 的 `bank-datasource` 里**硬编码了真实 RDS 账号密码**，
  文件自带注释也承认这点。若仓库推到过远端，应在阿里云侧轮换并改环境变量注入。

## 工程结构
- 多模块 Maven：`finance-ai`（Spring AI 智能客服，**8081**）、`api`（网关侧接口，8990）、`finance-gateway`、
  `finance-common` / `finance-base` / `finance-message` / `finance-oss`。
- 前端：`ww/ww_finance_admin`（管理端 5173）、`ww/ww_finance_user`（用户端 5174），Vue3 + Vite + TS + Element Plus。
- 中间件走 Docker：`ww_redis`（登录态）、`ww_chroma`（向量库）；MySQL `ww_finance`。
- 构建环境三个必踩坑（Bash PATH / JDK24 与 Lombok 冲突 / mvn 脚本起不来）见 skill `finance-demo-build`。

## 铁律
- **不要占用 8081**：用户通常在 IDEA 里跑 finance-ai。自测一律用 `--server.port=8082` 并**测完必须杀掉**。
- 沙箱会注入 `SERVER_PORT` 环境变量，不显式指定端口会被顶掉 —— 永远加 `--server.port=<port>`。
- 读 MySQL 一律加 `--default-character-set=utf8mb4`，否则中文显示成 `?`，会把正常数据误判为乱码。
  判定真乱码用 `HEX()`，出现 `3F` 才是真乱码。
- curl 本机端口加 `--noproxy "*"`，否则走代理返回 502。

## AI 配置：谁真正生效（改动前先看这张表）
`prompt_config` / `ai_model_config` / `ai_chat_model_binding` 种子数据里**大部分条目运行时并不读取**：

| 配置 | 运行时 | 读取方 |
| --- | --- | --- |
| `ai_chat_model_binding.scene = intent / chat / rag` | ✅ 最高优先级 | `ReActService`：该场景绑定的**模型 + 提示词 + 知识库**都生效 |
| `prompt_config.scene = intent / chat / rag`（`is_default=1` 那条） | ✅ 仅当上面未绑定/未启用时 | `PromptService.getConfigByScene` → `PromptSelector` |
| `prompt_config.prompt_key = UNSAFE_KEYWORDS` | ✅ | `ContentGuard`（命中原样回合规话术，不调模型） |
| `prompt_config.prompt_key = HANDOFF_KEYWORDS` | ✅ | `ContentGuard`（命中回转人工提示，不调模型） |
| `prompt_config.scene = risk / audit / summary` | ❌ | 无代码读取（软删除留存） |
| `ai_model_config`：`model_type='chat'` + `test_status=1` + `status=1` | ✅ | 唯一可被「对话模型选择」绑定的模型池 |
| `ai_chat_model_binding.scene = chitchat / summary / safety / answer` | ❌ | 已归档（`answer` 已拆为 `chat` + `rag`） |

- 三个运行场景必须与提示词场景**同名同数**：`intent` 意图路由 / `chat` 闲聊应答 / `rag` 知识库问答。
  一个场景 = 一条绑定 = 模型 + 提示词 + 知识库。
- **两层语义**：绑定是覆盖层，Prompt 设置是默认值层。运行时先看绑定场景的 `prompt_key`
  （`PromptService.getConfigByKey` 还原），取不到才回退 `PromptSelector` 挑出的场景默认值。
  排查"改了提示词没反应"看 `GET /ai/admin/chat/model/effective`。
- 场景指令"谁生效（默认值）"的唯一真源是 `PromptSelector.pickEffectiveByScene()`
  （排序 `update_time DESC, id ASC`，同场景 `is_default=1` 覆盖）；管理端「场景默认」标记与它同源。
- 模型门禁（`ChatModelBindingServiceImpl.resolveNames`）：绑定模型必须 chat 类型 + 测试通过 + 已启用。
  `test_status` 由 `POST /ai/admin/model/config/test/{id}` 落库；改 apiUrl/apiKey/modelName/modelType 即失效。
- `content_type='keywords'` 的行**不参与**场景指令选择；`clearDefaultByScene` / `selectActiveByScene` 均已排除。
- 关键词切分规则在 `KeywordUtils.split`（逗号/中文逗号/顿号/分号/换行），归一化去掉所有标点与空白。
  **管理端页面的计数必须用同一套规则**。
  ⚠️ 此类属于 **finance-ai**（`com.kzip.app.util.KeywordUtils`），**不在 api 模块**，
  只管 AI 客服的 `ContentGuard` 关键词拦截，与信贷业务无关。
- 两张关键词表已被运行时使用 → 禁止删除/停用（`assertRuntimeKeywordNotDisabled`），要关闭拦截只能清空内容。
- 管理端改 Prompt 走 `PromptService.ChangedEvent`（`@TransactionalEventListener(AFTER_COMMIT)`）立即刷新缓存，
  5 分钟定时兜底。改了"没反应"先查日志 `Prompt 配置变更，立即刷新缓存：更新指令 XXX`。
- 对话三个入口都已接前置拦截：`/ai/chat`、`/ai/chat-stream`（SSE，**前台主链路**）、`/ai/chat-sync`。
  SSE 的拦截标记走响应头 `X-Guard`，不拼进 token 流。
- 改 `scene` 字面量要同时改三处：Java 常量、`sql/fix_ai_config.sql`、前端 `sceneText` 映射 —— 必漏其一。

## 约定
- `binding.model_name` 会被 `ReActService` 当真实模型名发给大模型接口，改「模型配置」的 modelName 必须
  同步刷全部绑定（`syncModelName`），否则线上静默请求旧模型名。
- apiKey 接口只返回 `****` + 末 4 位；保存时若入参是掩码则沿用原值，防止回传覆盖真密钥。
- 前端会把列表的 `createTime/updateTime` 原样回传 → 三个 `updateWithCheck` 均置空，交给 `MyMetaObjectHandler` 写 now()。
- 用户端登录态直接存 Redis（`login:token:user:<id>`），可取出当 `token` 头做端到端联调；管理端 `/ai/admin/**`
  只需 `token` 非空。

## 资金链路（余额闸门 / 流水状态机）—— 改动前必读
- **余额是"银行库"的余额**：`BankAccountSyncServiceImpl` 从银行库 `ww_bank` 查 `balance`，平台 `user_account.amount` 只是镜像。
  所有扣钱校验都必须用**银行余额**（`getBankBalance(userId)`），不是 `user_account.amount`。
- **余额闸门统一走 `utils/BalanceGuard.require(actionName, available, required)`**：文案 `{actionName}失败：可用余额不足，本次需 ¥X，当前可用 ¥Y，还差 ¥Z，请先充值。`
  `actionName` 传**空串**则不拼前缀（否则 catch 里会变成"投标失败：投标失败："）。已接入：提现 / 还款 / 投标（含锁内二次校验）。
- **BalanceGuard 抛的是 `com.example.demo.exception.BusinessException`（api 模块自己的），已被 `GlobalExceptionHandler` 正确映射为 400 + 原文案** ——
  与历史上被吞成 500 的 `ResourceNotFoundException` 不是一回事，别混淆。null 余额按 0 处理，金额统一 `setScale(2, HALF_UP)`。
  另有 `requirePositive(amount, actionName)`：≤0 或小数位 >2 即拒。
- **`trans_flow.status`：0处理中 / 1成功 / 2失败**（`TransStatusEnum`）。充值/提现**发起时就落 status=0 的流水**，
  并把平台生成的 `agentBillNo`（`LendNoUtils.getChargeNo()/getWithdrawNo()`）随银行表单提交，回调按它定位并置终态。
- **回调幂等**：`resolveTransNo()` **优先取银行原样回带的 `agentBillNo`**；`existsSettledByTransNo()` **只认终态**，
  处理中不算已入账 —— 若把处理中当已入账，回调会被误判成重复而整单吞掉。
- **前端致命坑**：充值/提现/还款接口是 `responseType: 'text'`（返回银行 HTML 表单），但业务失败是
  **HTTP 200 + JSON `{"code":400,"msg":"余额不足"}`**。`utils/request.ts` 的 `parseErrorEnvelope()` 专门识别这种
  "文本里的错误信封"，缺了它"余额不足"会被静默吞成"提交成功"。
- 站内信类型已扩到 **29**（23~29 为回款/合同签署/撤标/提现失败/充值失败/投资人认证/**标的已下架**）；
  `Message.readFlag` 是 JSON 字段名，**不是 `isRead`**。
- 前端 dev server 代理固定打 **8990**（用户端可用 `VITE_API_TARGET` 覆盖成 8991 做隔离自测）。

## 平台侧通用约定（2026-10 审计新增）
- **分页必须走 `com.example.demo.utils.PageUtils.of(pageNum, pageSize)`**，禁止裸 `new Page<>(...)`。
  上限 `MAX_PAGE_SIZE=100`、`MAX_PAGE_NUM=10000`，`pageSize<1` 归一为 10（**不是 1**），`pageNum<1` 归一为 1。
  全项目 18 处分页已统一。`PageUtils.of` 只有 `(Integer,Integer)` 与 `(long,long)` 两个重载，
  **不可再加 `(Long,Long)`** —— 会与 `of(1, null)` 产生编译歧义。
- **状态文案唯一真源在 `ww_finance_user/src/utils/enumText.ts`**：`LEND_STATUS_MAP`（lend.status，8 态）、
  `LEND_ITEM_STATUS_MAP`（lend_item.status，3 态：0募资中/1已撤标/2满标待放款）、`enumTag()`。
  ⚠️ **`lend.status` 与 `lend_item.status` 是两套编码，绝不可合并**：`lend_item.status=1` 是"已撤标"，
  而 `lend.status=1` 是"募资中"。历史上多页各自维护 statusMap 已漂移（把"已撤标"显示成"持有中"）。
- **提现可用额 = 银行余额 − 该用户 `status=0` 的提现金额之和**（`sumPendingWithdraw`）。
  已冻结/处理中的提现不能再当可用额，否则能超额提现。求和走 `TransFlowMapper.sumAmount`（注解 SQL），
  **必须显式写 `is_deleted = 0`**：`@TableLogic` 不会自动作用于手写 `@Select`。
- **`BankBindController.bindDirect` 强制实名门禁**：只有投资人认证(`InvestorAuthStatusEnum.AUTHED=2`)
  或借款人认证(`BorrowerAuthStatusEnum.AUTHED=2`)通过才可绑卡；姓名/身份证**一律取登录用户自己的实名信息**，
  不再接受请求体传入（旧版可传 `name/idCard` 冒用他人身份）。
- **投标金额必须 ≤2 位小数**：银行回调 `handleInvestNotify` 的正则是 `\d+(\.\d{1,2})?`，
  3 位小数会在银行已冻结资金后 `return false` → 资金悬挂。故 `buildInvestForm` 前置 `scale()>2` 拦截。
- **`LendItemServiceImpl.buildInvestForm` 已移除 Redis 锁**：该方法**不写库**，加锁毫无意义；
  旧锁按"标的"维度 key，会把**不同用户**的并投误判为并发而拒绝，且 `unlock` 不校验持有者。

## 合同挂载与标的退出（下架 / 流标）
- **一个标的的全部合同都落在 `contract.biz_type='LEND' + biz_id=lendId`**，靠 `contract_type`
  （LOAN 借款合同 / INVEST 出借协议 / PAYMENT 放款确认书）+ `party_b_id` 区分当事人。
  `ContractBizTypeEnum.LEND_ITEM` 在合同业务里**没有被使用过**（别按它去查）。
- `ContractStatusEnum`：待签署(0) / 已签署(1) / 已作废(2)。`contractService.revoke()` **只作废待签署**，
  reason 必填，已签合同会抛异常 → 批量作废时必须逐个 try/catch，否则一份已签合同能把整笔下架事务带崩。
- **`LendService.offline(Long lendId, Long adminId)` 必须传操作人，null 直接拒绝**（比 `publish()` 严格：
  publish 对 null adminId 是宽容写入，但下架要处置投资人冻结资金，匿名操作本身就是风险动作）。
  驳回在查库之前，保证零副作用。入库落到 `lend.offline_admin_id` / `lend.offline_time`，
  使 `lend` 的四组留痕凑齐：`check_*`(上架审核) / `payment_*`(放款) / `offline_*`(下架处置)。
  `offline_admin_id` 为 NULL 表示**系统自动处置**（定时任务流标），非空才是人工。
- 标的退出出口状态：**PENDING(0) / FUNDING(1) / COMPLETED(2)**，绝不含 REPAYING(5)/SETTLED(6)。
  ⚠️ 管理端前端的按钮条件要和后端准入**同步**：曾出现后端放开 0/1/2 而 `v-if="row.status === 1"`
  只给 FUNDING 的情况，导致运营点不到退资出口 —— 改后端准入后务必回头核对 UI 按钮条件。
- 消息类型加值必须**同时**改三处：Java `MessageTypeEnum`、前端 `ww_finance_user/src/api/message.ts`
  的 `MESSAGE_TYPE_META`、以及任何按 code 统计的 SQL。现有末位：`MAKE_LOAN_PENDING(30, "满标待签确认书")`。
- **三份合同的签署时机（定论，别再挪）**：
  《借款合同》LOAN = 建标后上架募资前，强校验在 `LendServiceImpl.publish()` 关口一；
  《出借协议》INVEST = 投资人投标时，强校验在 `LendItemServiceImpl` 关口二；
  《放款确认书》PAYMENT = **满标后、放款前**，强校验在 `LendReturnServiceImpl.buildMakeLoanForm()` 关口三。
  ⚠️ 曾误把 PAYMENT 的强校验提前到 `publish()` 并已撤销 —— 它是"确认放款、授权划款"的意思表示，
  标的未募满就要求确认放款法理不通。`publish()`/`createLend()` 只做 `getOrCreate` 幂等预生成。
  资金悬停靠三条兜：① 满标催签站内信(30) ② `offline()` 退资出口（准入含 `COMPLETED`，
  `withdrawAllInvestments` 覆盖 `status IN (0,2)` —— **两处必须成对，只改一处是假修复**）
  ③ 合同模板的超期处置条款（`sql/contract_overdue_clause.sql`）。
- **改合同模板必须走版本化**：INSERT `version+1`(status=1) + 同编号旧版本置 `status=0`，
  运行时按 `status=1 ORDER BY version DESC LIMIT 1`。已签合同靠 `content_snapshot` 冻结，改模板不影响历史合同。
  ⚠️ MySQL 里 `'\n'` 是**真换行**（写成 `'\\n'` 会落库成字面反斜杠-n）；`||` 是**逻辑或**不是拼接，必须用 `CONCAT()`。
- `JwtAuthInterceptor` 强依赖 Redis：token 需 `5grcs ` 前缀 + 与 `login:token:<role>:<userId>` **完全相等**，
  命中后 `expire` 续期 7 天。**Docker Desktop 未启动 → 6379 不可达 → 所有 `/api/**`、`/admin/**` 都进不去**，
  此时只能靠纯 Mockito 单测验证。
