# ww_finance（D:\桌面\实训\demo）项目长期笔记

P2P 网络借贷信息中介平台（实训）。借款人发标 → 投资人投标 → 满标放款 → 分期还款。
平台不碰资金，**存管银行** `ww_bank-1.0.0.jar`（手动启动，9090）持有并划转。

## 工程结构
- 多模块 Maven：`api`(8990) / `finance-ai`(8081) / `finance-message`(8120) / `finance-gateway` /
  `finance-common` / `finance-base` / `finance-oss`。Spring Boot 2.3.4 + MyBatis-Plus。
- 前端 `ww/ww_finance_admin`(5173)、`ww/ww_finance_user`(5174)：Vue3 + Vite + TS + Element Plus。
- 中间件 Docker（只有 `ww_redis` + `ww_chroma`）；MySQL `ww_finance`。
- 构建三坑（Bash PATH / JDK24×Lombok / mvn 脚本起不来）见 skill `finance-demo-build`。

## 铁律（操作前必读）
- **不占 8081**（用户 IDEA 在跑 finance-ai）；自测 `--server.port=8082`，**测完必杀**。
  沙箱注入 `SERVER_PORT`，永远显式加 `--server.port=<port>`。
- 读 MySQL 加 `--default-character-set=utf8mb4`；判真乱码用 `HEX()`（出 `3F` 才是）。
- curl 本机端口加 `--noproxy "*"`。
- `bash` 工具 PATH 缺 `date`/`dirname`，先 `export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows"`。

## 数据源
- `bankJdbcTemplate` → 阿里云 RDS `rm-uf6ry261ghx3995253o...`，库 `ww_bank`，只用 `user_account`。
  **本地 `bank` 库是别的示例项目（emp/dept/salarygrade），无关。**
- `BankDataSourceConfig` 已 `setInitializationFailTimeout(-1)`；`BankAccountSyncServiceImpl` 兜底降级
  平台 `user_account`。超时 `bank-datasource.connect-timeout-ms` / `BANK_DB_CONNECT_TIMEOUT`（默认 8000/3000）。
- 判别走银行库还是兜底：看提现超额文案的"当前可用 ¥Y"。模拟不可达：
  `--bank-datasource.url=jdbc:mysql://10.255.255.1:3306/ww_bank?serverTimezone=GMT%2B8`。
- ⚠️ `application.yml` 硬编码真实 RDS 密码；若推过远端 → 轮换 + 改纯环境变量。
- ⚠️ `security.bank.sign-key` / `agent-id` 是**死配置**（无代码读取）；生效的是环境变量
  `BANK_SIGN_KEY` / `BANK_AGENT_ID`。三方签名密钥必须同为 `9876543210`
  （`SignUtil.SIGN_KEY` = `HfbConst.SIGN_KEY` = `BankSignUtil.SIGN_KEY`），改一处即全线验签失败。

## 资金链路（余额闸门 / 流水状态机）
- **余额是银行库余额**：`getBankBalance(userId)` 才是真余额，平台 `user_account.amount` 只是镜像。
- **闸门统一 `utils/BalanceGuard`**：`require(actionName, available, required)` →
  `{actionName}失败：可用余额不足，本次需 ¥X，当前可用 ¥Y，还差 ¥Z，请先充值。`
  `actionName` 传**空串**不拼前缀（否则"投标失败：投标失败："）。已接：提现 / 还款 / 投标。
  抛 `BusinessException` → 400 + 原文案。null 按 0，`setScale(2, HALF_UP)`。
  `requirePositive(amount, actionName)`：≤0 或 >2 位小数即拒。
- **`trans_flow.status`：0处理中 / 1成功 / 2失败**。充值/提现**发起即落 status=0**，
  `agentBillNo`（`LendNoUtils.getChargeNo()/getWithdrawNo()`）随银行表单提交，回调按它定位终态。
- **回调幂等**：`resolveTransNo()` 优先取银行回带 `agentBillNo`；`existsSettledByTransNo()` **只认终态**
  （处理中不算已入账，否则回调被误判重复而整单吞掉）。
- **提现可用额 = 银行余额 − 该用户 `status=0` 提现之和**（`sumPendingWithdraw`）。求和走
  `TransFlowMapper.sumAmount` 注解 SQL，**必须显式 `is_deleted = 0`**（`@TableLogic` 不作用于手写 `@Select`）。
- **投标金额必须 ≤2 位小数**：银行 `handleInvestNotify` 正则 `\d+(\.\d{1,2})?`，3 位小数会在银行已冻结
  资金后 `return false` → 资金悬挂。`buildInvestForm` 前置 `scale()>2` 拦截。
- `LendItemServiceImpl.buildInvestForm` **已移除 Redis 锁**（不写库；旧锁按标的分 key 误判并投）。
- **银行回调验签已实现，别再当缺口**：6 个处理器第一件事就验签，失败 `return false` ——
  `UserBindServiceImpl:77`、`TransFlowServiceImpl:165`(充值)/`215`(提现)、`LendItemServiceImpl:217`(投标)、
  `LendReturnServiceImpl:192`(放款)/`455`(还款)。验签在 Service 层。⚠️ 曾用大小写敏感 `grep "sign"`
  误判"不验签"；排查能力是否存在必须加 `-i` 或按语义 grep。存疑未实证：还款回调 resultParam 看不到 `sign`。
- **前端致命坑**：充值/提现/还款是 `responseType:'text'`（返回银行 HTML 表单），业务失败却是
  **HTTP 200 + JSON `{"code":400,"msg":...}`**。`utils/request.ts` 的 `parseErrorEnvelope()` 专门识别；
  缺了它"余额不足"被静默吞成"提交成功"。

## 合同挂载与标的退出（下架 / 流标）
- 一个标的全部合同落在 `contract.biz_type='LEND' + biz_id=lendId`，靠 `contract_type`（LOAN 借款合同 /
  INVEST 出借协议 / PAYMENT 放款确认书）+ `party_b_id` 区分。`ContractBizTypeEnum.LEND_ITEM` **从未被使用**。
- `ContractStatusEnum` 待签署(0)/已签署(1)/已作废(2)。`contractService.revoke()` **只作废待签署**且 reason
  必填 → **批量作废必须逐个 try/catch**，否则一份已签能把整笔下架事务带崩。已签靠 `content_snapshot` 冻结。
- **三份合同签署时机（定论，别再挪）**：LOAN = 建标后上架募资前，强校验 `LendServiceImpl.publish()` 关口一；
  INVEST = 投标时，`LendItemServiceImpl` 关口二；PAYMENT = **满标后、放款前**，
  `LendReturnServiceImpl.buildMakeLoanForm()` 关口三。⚠️ 曾误把 PAYMENT 提前到 `publish()` 并已撤销；
  `publish()/createLend()` 只做 `getOrCreate` 幂等预生成。
- **`LendService.offline(lendId, adminId)` 必须传操作人**，null 直接拒（查库前驳回，零副作用）。落
  `lend.offline_admin_id`/`offline_time`；NULL = **系统自动处置**。两入口 `offline()` 人工 /
  `offlineBySystem()` 系统共用 `offlineInternal`，**不要把 offline() 放宽成接受 null**；
  发消息分流：系统 `MAKE_LOAN_TIMEOUT(31)`，人工 `LEND_OFFLINE(29)`。
- 退资出口状态：**PENDING(0)/FUNDING(1)/COMPLETED(2)**，绝不含 REPAYING(5)/SETTLED(6)。⚠️ 管理端按钮
  `v-if` 要与后端准入**同步**（曾出现后端放开 0/1/2 而 UI 只给 FUNDING）；`offline()` 准入含 `COMPLETED`
  与 `withdrawAllInvestments` 覆盖 `status IN (0,2)` —— **两处必须成对，只改一处是假修复**。
- **宽限期起算点是 `lend.complete_time` 不是 `update_time`**（后者 `on update CURRENT_TIMESTAMP`，
  动一下就重置 ⇒ 拖着不签可无限续期）。`MAKE_LOAN_SIGN_DEADLINE_DAYS = 3` **必须与合同模板天数一致**。
  定时 `FinanceJobHandler#refundUnsignedCompletedLends`（每天 01:30）三个防误伤边界：`complete_time`
  非空 / PAYMENT 未签 / `payment_admin_id` 为空。
- **改合同模板必须版本化**：INSERT `version+1`(status=1) + 同编号旧版本置 `status=0`，运行时
  `status=1 ORDER BY version DESC LIMIT 1`。⚠️ MySQL `'\n'` 是**真换行**（`'\\n'` 落库成字面反斜杠-n）；
  `||` 是**逻辑或**不是拼接，必须 `CONCAT()`。
- 定时超期处置三兜底：① 满标催签站内信(30) ② `offline()` 退资出口 ③ 模板超期条款
  （`sql/contract_overdue_clause.sql`）。

## 平台侧通用约定
- **分页必须 `PageUtils.of(pageNum, pageSize)`**，禁裸 `new Page<>()`。`MAX_PAGE_SIZE=100` /
  `MAX_PAGE_NUM=10000`；`pageSize<1` 归一 **10**（不是 1），`pageNum<1` 归一 1。只有 `(Integer,Integer)`
  + `(long,long)` 两个重载，**不可再加 `(Long,Long)`**（编译歧义）。
- **状态文案唯一真源 `ww_finance_user/src/utils/enumText.ts`**：`LEND_STATUS_MAP`（lend.status，8 态）、
  `LEND_ITEM_STATUS_MAP`（lend_item.status，3 态：0募资中/1已撤标/2满标待放款）、`enumTag()`。
  ⚠️ **两套编码绝不可合并**（`lend_item.status=1` 已撤标 ≠ `lend.status=1` 募资中）。
- **`BankBindController.bindDirect` 强制实名门禁**：仅 `InvestorAuthStatusEnum.AUTHED=2` 或
  `BorrowerAuthStatusEnum.AUTHED=2` 才可绑卡；姓名/身份证**一律取登录用户自己的实名信息**，
  不接受请求体传入（旧版可传 `name/idCard` 冒用他人身份）。
- 站内信类型已扩到 **31**（末位 `MAKE_LOAN_TIMEOUT(31)`）；`Message.readFlag` 是 JSON 字段名
  （**不是 `isRead`**）。**加类型要同时改三处**：Java `MessageTypeEnum`、前端
  `ww_finance_user/src/api/message.ts` 的 `MESSAGE_TYPE_META`、按 code 统计的 SQL。
- 前端 dev server 代理固定 8990（用户端 `VITE_API_TARGET` 可覆盖成 8991 隔离自测）。
- 前端回传 `createTime/updateTime` → 三个 `updateWithCheck` 均置空，交给 `MyMetaObjectHandler` 写 now()。
- 登录态存 Redis（`login:token:user:<id>`）。`JwtAuthInterceptor` 强依赖 Redis：token 需 `5grcs ` 前缀
  + 与 `login:token:<role>:<userId>` **完全相等**，命中续期 7 天。**Docker 未启动 → 6379 不可达 →
  所有 `/api/**`、`/admin/**` 都进不去**，只能靠 Mockito 单测。

## AI 配置：谁真正生效（改动前先看）
| 配置 | 生效 | 读取方 |
| --- | --- | --- |
| `ai_chat_model_binding.scene=intent/chat/rag` | ✅ 最高优先级 | `ReActService`：绑定的**模型+提示词+知识库**都生效 |
| `prompt_config.scene=intent/chat/rag`（`is_default=1`） | ✅ 仅当上面未绑定/未启用 | `PromptService.getConfigByScene` → `PromptSelector` |
| `prompt_config.prompt_key=UNSAFE_KEYWORDS`/`HANDOFF_KEYWORDS` | ✅ | `ContentGuard`（命中直接回话术，不调模型） |
| `prompt_config.scene=risk/audit/summary` | ❌ | 无代码读取（软删除留存） |
| `ai_model_config`：`model_type='chat'` + `test_status=1` + `status=1` | ✅ | 唯一可被绑定的模型池 |
| `ai_chat_model_binding.scene=chitchat/summary/safety/answer` | ❌ | 已归档（`answer` 已拆为 `chat` + `rag`） |

- 三个运行场景必须与提示词场景**同名同数**：`intent` 意图路由 / `chat` 闲聊应答 / `rag` 知识库问答。
- **两层语义**：绑定是覆盖层，Prompt 设置是默认值层。运行时先看绑定场景 `prompt_key`
  （`PromptService.getConfigByKey` 还原），取不到才回退 `PromptSelector`。
  排查"改了提示词没反应"看 `GET /ai/admin/chat/model/effective`。
- 默认值层唯一真源 `PromptSelector.pickEffectiveByScene()`（`update_time DESC, id ASC`，
  同场景 `is_default=1` 覆盖）。
- 模型门禁（`ChatModelBindingServiceImpl.resolveNames`）：绑定模型必须 chat 类型 + 测试通过 + 已启用。
  `test_status` 由 `POST /ai/admin/model/config/test/{id}` 落库；改 apiUrl/apiKey/modelName/modelType 即失效。
- `content_type='keywords'` 的行**不参与**场景指令选择。
- 关键词切分在 `KeywordUtils.split`（逗号/中文逗号/顿号/分号/换行），归一化去所有标点与空白，
  **管理端计数必须同一套规则**。⚠️ 在 **finance-ai**（`com.kzip.app.util`），**不在 api 模块**。
- 两张关键词表已被运行时使用 → 禁止删除/停用（`assertRuntimeKeywordNotDisabled`），关闭只能清空内容。
- 改 Prompt 走 `PromptService.ChangedEvent`（`@TransactionalEventListener(AFTER_COMMIT)`）立即刷缓存，
  5 分钟兜底。日志 `Prompt 配置变更，立即刷新缓存`。
- 三入口都已接前置拦截：`/ai/chat`、`/ai/chat-stream`（SSE，**前台主链路**）、`/ai/chat-sync`。
  SSE 拦截标记走响应头 `X-Guard`，不拼进 token 流。
- 改 `scene` 字面量要同时改三处：Java 常量、`sql/fix_ai_config.sql`、前端 `sceneText` —— 必漏其一。
- `binding.model_name` 会被 `ReActService` 当真实模型名发出，改「模型配置」modelName 必须同步
  `syncModelName` 全部绑定，否则线上静默请求旧模型名。
- apiKey 接口只返回 `****` + 末 4 位；保存时入参是掩码则沿用原值，防回传覆盖真密钥。

## 测试用例：位置与跑法
- **单元测试**：`D:\桌面\实训\demo\api\src\test\java\com\example\demo\**`，共 **18 个 `*Test.java`**。
  其中 **12 个纯 Mockito/纯 JUnit、离线可跑（87 用例；未含 2026-10-08 新增的
  `LocalFileStorageOwnershipTest` 12 用例 → 全绿时共 99）**：`TransFlowNotifyServiceTest`(12)、`BalanceGuardTest`(12)、
  `LocalFileStorageOwnershipTest`(12，文件归属隔离)、`LendOfflineClosureTest`(9)、
  `ReconciliationServiceImplTest`(8)、`StatusEnumTest`(7)、`UserAccountWithdrawGuardTest`(7)、
  `PageUtilsTest`(7)、`BankNotifyControllerTest`(6)、`FinanceJobHandlerTest`(5)、
  `UserBindNotifyServiceTest`(5)、`TokenUtilTest`(5)、`HfbConstTest`(4)。
  需真实环境、**不进白名单**：`TokenFlowTest`、`CrudTest`、`DbConnectTest`、`BankBindTest`、`DemoApplicationTests`。
- 跑法见 skill `finance-demo-build`：`... Launcher -o -B -f "D:/桌面/实训/demo/api/pom.xml" test
  -Dtest="$TESTS" -DfailIfNoTests=false`。**多类必须用逗号**；`-Dtest=A+B+C` 会**静默跑 0 个并报
  BUILD SUCCESS**（假绿灯），跑完必须核对 `Tests run:` 总数。
- **接口测试**：`D:\桌面\实训\demo\docs\postman\` —— `ww_finance.postman_collection.json`（55 个请求，
  10 个业务文件夹）、`ww_finance.postman_environment.json`、`generate_collection.py`、`README.md`。
  集合级 Pre-request Script 自动登录取 token（已排除 `/login`、`/register`、`/sendCode` 防死循环）；
  admin 用 `token` 头、user 用 `Authorization` 头，**两端 token 不能混用**。
  **`generate_collection.py` 是集合 JSON 的唯一真源**（手写 JSON 要转义换行/引号，不可维护）——
  接口有增删改这个脚本后 `python docs/postman/generate_collection.py` 重跑，**别手改 JSON**。
  ⚠️ **它只是生成器，不发任何请求**（只 import json/os）。真正执行断言要靠 newman 或 Postman
  Collection Runner；本机 newman **未安装**。
- **项目根目录已有纯 Python 联调范式**（写 Python runner 时的现成地基，别从零造）：
  - `_token.py` —— 自签 JWT（秘钥 `vsofo-5grcs-secret`，**jjwt 0.9.1 会把密钥 base64 解一次**
    再当 HMAC key，直接用原文会判"token 无效"）+ 写 `login:token:<role>:<id>`；绕过 BCrypt 密码。
  -   `_e2e_market.py` —— 端到端断言脚本范式：`req()` / `check()` / 末尾 PASS-FAIL 汇总，
    还含 `form_post()` 按银行签名规则（TreeMap 排序 + 每值后拼 `|` + MD5）模拟回调。
    **惯例是打隔离实例 8991，不碰用户正在用的 8990。**
  - `_e2e_owner_isolation.py`（2026-10-08 新增）—— OSS 文件归属隔离的端到端回归探针（14 项断言）。
    可作新脚本的模板：`import _token` 复用登录态签发，PNG 内联 base64 免外部素材，
    `disk_path(url)` 把 URL 换算回磁盘路径直接断言文件存在性。

## 用户隔离审计（2026-10-08，只读审计）
**结论：用户端水平隔离基本完整；1 处确认越权 + 2 处信息暴露疑点；管理端无权限分级。**
三层机制：① `JwtAuthInterceptor`（token 需 `5grcs ` 前缀 + 与 Redis 会话**完全相等**，可实现强制下线）
② `LoginUserContext`（ThreadLocal，`afterCompletion` 清理）③ Controller/Service 用**登录态 userId** 下推
SQL 限定归属，不信前端传参。Redis key 按角色分命名空间（user 与 sys_admin 主键可能相同，混用会顶掉会话）。
跨角色双向隔离：`/admin/**` 需 admin，`/api/**` 禁 admin（`JwtAuthInterceptor:76-88`）。
- ✅ **文件删除越权已修（2026-10-08）**：原 `DELETE /api/oss/file/remove?url=...` 只校验 url 前缀 +
  无 `..`、**无属主判断**（`LocalFileStorageStrategy.delete` 按路径直接删盘），任意登录用户拿到他人 URL
  即可删实名身份证图与合同签名存证（签名图 URL 经 `ContractServiceImpl.detail()` 的
  `signRecord.signImgUrl` 暴露给同合同另一方 → URL 来源现成）。
  **修法 = 归属写进物理路径**：上传落 `images/u<userId>/<file>`（`upload(file, category, ownerId)`），
  删除走 `deleteOwned(url, ownerId)` 严格匹配 `<category>/u<ownerId>/<file>` **三段式**
  （天然排除 `..` 穿越与 `u33`/`u330` 误判）；匹配失败统一 403「无权删除该文件」，
  不区分"不存在"与"不是你的"（防探测）。无属主段的**旧文件一律拒绝删除（fail-closed）**。
  `delete(String)` 已**重命名为 `deleteUnchecked(String)`** —— 故意让调用点编译报错、强制逐个人工确认；
  唯一保留的调用是 `KnowledgeDocServiceImpl.removeWithFile`（文件地址取自库记录而非请求参数，
  且入口受管理端鉴权保护）。**新增用户上传必须走三参 `upload`，不得回退到无属主版本。**
  验收：单测 `LocalFileStorageOwnershipTest`(12) + 端到端 `_e2e_owner_isolation.py`（8991，14/14 全绿）。
  ⚠️ 前端 `deleteFile()` 定义了但**从未被调用**，故收紧删除接口零功能回归。
- ⚠️ **管理端无权限分级**：`sys_admin` 表**无角色字段**，`TokenUtil:91` 硬编码 `token_role="admin"`，
  全项目 `@PreAuthorize`/`@Secured` **零命中**（`SecurityConfig` 已开 `@EnableGlobalMethodSecurity(prePostEnabled=true)`
  —— 能力在但没用）。任何管理员都能审核/下架/放款/作废合同，无最小权限边界。
- 疑点：`GET /api/core/lend/{id}` 返回完整 `Lend` 实体（含借款人 userId）；
  `GET /api/core/lend/{lendId}/investments` 返回全部 `LendItem`（含他人 `investUserId`/`investName`/`investAmount`）
  —— 若产品不做「投资人名单公开」即为他人投资信息泄露。
- ⚠️ **`GET /api/core/user/checkMobile/{mobile}` 在拦截器 exclude 白名单里**（`MyWebConfig:50`）→
  **无需登录**即可枚举手机号是否已注册。
- 已抽查确认**正确**的：`UserMessageController`（markRead 校验 `message.userId`）、
  `ContractController`（pageMy 按 partyA/partyB 过滤；detail/sign 校验签署方；prepare 校验 403）、
  `LendReturnController.repay/{id}`、`TransFlowController`、`UserAccountController`、`LendController.myInvestments`、
  `UserChatController`（`checkOwnSession`）、`BankBindController`、`Borrower/InvestorController`、
  `BorrowInfoController`、`IntegralGradeController`。

## 生产就绪度（2026-10-06 审计）
**结论：业务功能完备；工程化与合规此前未就绪。**
- ✅ **`HfbConst` 地址外置化**：21 处 `localhost` → `bank.base-url`（银行网关）/ `bank.notify-base-url`
  （我方回调）/ `bank.front-base-url`，环境变量 `BANK_BASE_URL` / `BANK_NOTIFY_BASE_URL` / `BANK_FRONT_BASE_URL`。
  ⚠️ **保留静态字段 + `@Value` setter**（`HfbConst.X` 散落 6 个 Service 共 26 处；不启容器即纯 Mockito
  时行为与改造前一致、不 NPE）。任一 setter 被调即 `refresh()` 重算 21 个地址并吃掉结尾斜杠，
  用例 `HfbConstTest`(4) 钉住。⚠️ `bank.notify-base-url` **必须银行能访问**，填 localhost = 回调永远收不到。
- ✅ **定时任务分布式锁**：`utils/RedisDistributedLock`（`setIfAbsent` + `lua/unlock.lua` 校验 token 后释放）。
  `FinanceJobHandler` 4 任务统一 `runWithLock(...)`，开关 `job.distributed-lock.enabled`（默认 true）。
  **Redis 不可达 → 跳过本次而非放行**（宁可少跑一次不能重复动钱）。单测里 `@Value` 不生效 → 默认 false → 直接跑。
- ❌ 无 profile 隔离（未配 `spring.profiles.active`）；凭据 `${ENV:明文默认值}` 硬兜底，`BANK_DB_PASSWORD` 是真密码。
- ❌ 无 Dockerfile / 部署脚本 / logback 定制 / actuator 健康检查 / 监控告警 / DB 备份脚本 / 部署文档。
- ❌ 无方法级鉴权（只有 URL 级拦截器 + `JwtAuthInterceptor`），无 `@PreAuthorize`；无身份证/银行卡脱敏。
- ❌ 无乐观锁 / `SELECT FOR UPDATE`（`@Version` / `FOR UPDATE` 全项目零命中），并发扣款靠 Redis 锁 + 唯一约束。
- ❌ 无集成测试 / 契约测试（单测见上节）。
- 运行时硬依赖：Docker(redis+chroma)、`ww_bank-1.0.0.jar`(手动启动)、Nacos(`:8848`)、
  `finance-message`(`:8120`，Feign `listOfServers` 硬编码)。
