# ww_finance（D:\桌面\实训\demo）长期笔记

P2P 借贷：发标→投标→满标放款→分期还款。**平台不持有资金**；存管银行 `ww_bank-1.0.0.jar`（手动起 9090，**无源码**）持有账户与划转。
构建/启动/排障见 skill `finance-demo-build`；日志见 skill `spring-boot-log-rolling`。
> ⚠️ **长尾实证全在 `reference/deep-dive.md`，相关问题先 Read 它**（收费字节码 / 数据核查逐条 / 合同签署时机与退出细节 / 网关·OSS·Nacos / 安全缺口 / Mongo·AI·Chroma / docs）。本文件只留高频铁律。
> ⚠️ **本仓库常有并行改动**：动手前先看目标文件 mtime。

## 结构与端口
- api(8990,`service-core`) / finance-ai(8081) / finance-message(8120) / finance-gateway(8088) / finance-oss(8130) / common+base；Spring Boot 2.3.4 + MyBatis-Plus。
- 前端 `ww_finance_admin`(5173) / `ww_finance_user`(5174)：**统一代理到网关 8088**（`VITE_API_TARGET`/`VITE_AI_TARGET` 可绕过；排障起隔离 8991）。
- **Nacos 8848 只作注册中心**（无配置条目）；注册方 = api/message/oss/gateway；**ai 未注册**（网关 `/ai/**` 直连 8081）。银行 9090 由 api 直连。
- Docker 只 redis+chroma；MySQL 3306 root/123456 `ww_finance`；MongoDB 27017（独立进程）。

## 环境铁律
- **不占 8081**（IDEA 在跑）；自测 8082/8991，**测完必杀**；沙箱注入 `SERVER_PORT`，永远显式传端口。
- bash 先 `export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"`；PortableGit **无 git**。
- mysql 读必加 `--default-character-set=utf8mb4`；javap 用 jdk-17；托管 python **不认 `/d/桌面/...` 先 cd**；curl 本机加 `--noproxy "*"`；**非 HTTP 探活用 `/dev/tcp`**。
- Maven：JDK17 + plexus-classworlds 启动器。**改 finance-common 后 `-f api/pom.xml` 必须带 `-am`**。

## 资金链路（谁是账本）
- **余额真源 = 银行库 `user_account`（`user_code`=bindCode）**；平台表只是镜像。生效密钥是 `BANK_SIGN_KEY`/`BANK_AGENT_ID`（三方同 `9876543210`）。
- **可用额唯一真源 `BankAccountSyncService.getAvailableBalance(userId)`** = 银行余额 − Σ(PROCESSING 流水)，钳到 0；提现/投标/还款三处全调它（配 `savePending/settlePending`）。
- `trans_flow.status` 0处理/1成功/2失败；幂等 `existsSettledByTransNo()` **只认终态**；**手写 SQL 必须显式 `is_deleted=0`**。`trans_type` 1充值/2提现/3投标/4回款/5放款/6还款；**退资回补复用 4(RETURN)** 靠 memo 区分。
- ✅ **放款=服务端同步**：`AgreeAccountLendProject` 同步 JSON、**不发回调**；`resultCode=0000` 即成功（别混异步回调的 `"1"`/`"0001"`）。`prepareMakeLoanParams()` 必传 **`mchFee=0`**；出站走 `RequestHelper.sendFormRequest()`。
- ✅ **投标表单必传 `benefitBindCode=借款人 bindCode`（别删）**：放款去向取自 `user_invest.benefit_bind_code`，缺了 → 银行返 **200+"出错了" HTML**。投标金额 ≤2 位小数。
- ⚠️ **银行 jar 单边记账**（`unLockAccount` 只 freeze−；`transferAccounts` 只 amount+）⇒ **银行侧账实不能作为资金正确性依据**。
- ✅ **退资回补**：`LendItemService.refundInvestment(item, memo)` 一处完成「翻已撤标 + `updateBalance(RETURN, amount)` + 退款流水」；⚠️ **只能走 `RETURN`**（投标用 `deductAmount`）。
- **6 个银行回调都验签**；前端充值/提现/还款 `responseType:'text'`，业务失败是 **200+JSON `{"code":400}`**。
- 定时（`FinanceJobHandler`，带 Redis 锁）：逾期 02:00 / 对账 03:30 / 未满标失败 01:00 / 宽限期退资 01:30。
- ⚠️ **平台只赚服务费且一分未收**（三处 `fee` 硬编码 0）。⚠️ 存量脏数据：银行 4 负余额 / 6 条悬挂 `status=0` 流水 / `user_bind` 仅 20 条 / 11 条 `benefit_bind_code='0'`。
- ✅ **对账心跳** `trans_no=RECON-HEARTBEAT-<日期>`，一天一行 ⇒ **别把 "reconciliation_record 0 行" 当"没差异"**。⚠️ **银行幂等不均**：`vote`/`makeLoan` 有去重，**`recharge`/`withdraw` 无** ⇒ 重复提交会重复入账。

## 标的业务循环（2026-10-08 全通）
- 借款申请→审批→发标→募资→满标→放款→**还款**全通；**至今无标的到 status=6**。
- ⚠️ **银行 `returnCommit` 强等式**：`voteFeeAmt + ΣtransitAmt == totalAmt`（转 double 比较），不等抛 `RETURN_AMMOUNT_MORE_ERROR`、返 200+错误页。**改还款代码前必读。**
- **还款三条铁律（已固化）**：① `total = 已舍入principal + 已舍入interest`，月供先舍入为权威、本金倒挤；② 投资人明细**按投资占比切分期供**；③ 生成期 `balanceItemReturns()` 倒挤到**投资额最大**一户。
- **逾期**：`voteFeeAmt = overdueTotal − total`。⚠️ 仅单测覆盖。
- 银行还款**两段式**：`/lendReturn/AgreeUserRepayment`（确认页不动钱）→ `/lendReturn/returnCommit`（真扣款，**不验签**）。充值/投标一段式。回调 **`/api/core/lendReturn/notifyUrl`**。
- ⚠️ `LendStatusEnum`：0待发布/1募资中/2已满标待放款/3已逾期/**4已下架**/5还款中/6已完结/7已流标。

## 合同体系（2026-10-09 整改后）
- **5 类**：`ContractTypeEnum` = LOAN 借款合同 / INVEST 出借协议 / PAYMENT 放款确认书 / **SERVICE 居间服务协议** / **DEPOSIT 资金存管协议**。`ContractBizTypeEnum` = LEND/BORROW_INFO/LEND_ITEM/**USER**（用户级：`biz_id=party_a_id=userId`、`party_b_id=NULL`；⚠️ amount **不能 NULL**）。
- ✅ 模板各 1 启用版本：`LOAN v3`/`INVEST v2`/`PAYMENT v1`/`SERVICE v1`/`DEPOSIT v1`（`status=1 ORDER BY version DESC LIMIT 1`）。SQL：`sql/contract_loan_party_fix.sql`、`sql/contract_service_deposit.sql`。
- ✅ **主体口径（答辩必用）**：`TPL_LOAN` v3 = **甲方（借款人）/ 乙方（全体出借人）**，与 `party_a_id=借款人` 一致。⚠️ **`party_b_id` 仍 NULL 是"每人一份"有意建模**，双边合同是《出借协议》。**别再当缺陷报。**
- ✅ 投资人可见《借款合同》《放款确认书》（`myInvestedLendIds` 分支）；`detail` 返 **`signable`**。**可见 ≠ 可签**。
- ✅ **闸门**：LOAN=建标预生成+上架前必签；INVEST=投标硬前置；PAYMENT=放款前必签；**SERVICE=发标+投标**；**DEPOSIT=绑卡**。⚠️ `getOrCreateUserAgreement` **模板缺失时降级放行**，别当"校验被绕过"。
- ✅ 导出 `GET /api/core/contract/auth/export/{contractId}` → `{fileName, content}`。
- ⛔ **未改（设计取舍）**：① **罚息归平台**（`LendReturnServiceImpl:817-823`）；② 满标不生成合同/不签章（有意）；③ 正文未渲染还款计划表。
- `contract` 状态 0待签/1已签/2已作废；`revoke()` 只作废待签且 reason 必填 → **批量作废逐个 try/catch**；`offline(lendId,adminId)` **必须传操作人**。
- **宽限期起算 `complete_time` 不是 `update_time`**；`MAKE_LOAN_SIGN_DEADLINE_DAYS=3` 须与模板一致。
- 脚本签合同：`POST /api/core/contract/auth/prepare` → `POST .../sign {contractId,signImgBase64}`（1x1 base64 PNG 即可）。

## 平台约定
- 分页必须 `PageUtils.of(pageNum,pageSize)`；MAX_PAGE_SIZE=100/MAX_PAGE_NUM=10000；**不可加 `(Long,Long)` 重载**。
- 状态文案真源 `ww_finance_user/src/utils/enumText.ts`（LEND 8态 / LEND_ITEM 3态，**绝不可合并**）。加站内信类型须同改 Java 枚举（已到 31）+ 前端 `MESSAGE_TYPE_META` + 统计 SQL。
- 登录态 Redis `login:token:<role>:<id>`，需 `5grcs ` 前缀且值完全相等；**Docker 未起 → 6379 不可达 → /api、/admin 全进不去**。
- ✅ **手机号唯一（10-08 修）**：真 UNIQUE + 捕 `DuplicateKeyException`（`sql/fix_user_mobile_unique.sql`）。
- ✅ **角色判据**：**借款**=`borrow_auth_status=2`；**投资**=`user_type==1` **或** `invest_auth_status==2`（`user_type` 只是注册时意向）。
- ✅ **合同类型下拉真源 = `ContractTypeEnum.values()`**；管理端 `CONTRACT_TYPE_META` **必须手加键**，否则类型检查报缺键。
- ✅ 管理端权限分级：`sys_admin.role`(SUPER_ADMIN/ADMIN) + JWT `token_admin_role` + `@RequireAdminRole`（**不能用 `@PreAuthorize`**，全站 500）。⚠️ **api 需重启生效**。
- ✅ **P5-23~27 已修**（`sql/fix_schema_drift.sql`，幂等）：16 表 utf8mb4 / 金额 decimal(20,2) / `lend_item.status` NOT NULL DEFAULT 0。⚠️ **P5-68：`db_init.sql` 已无法重建线上库，别按脚本重建。**
- 日志根**必须绝对路径** `D:/桌面/实训/demo/logs`；银行往来独立 `logs/bank-trade/bank-trade.log`（REQ/CALLBACK/RECON，90 天）—— **对账唯一凭据**。Gateway 只做路由+CORS，**不做鉴权**。未修项见 deep-dive。

## 测试（2026-10-09）
- 后端白名单 **22 类 / 144 用例全绿**（根 pom + `-pl api -am`）。⚠️ 集成类依赖 Redis/MySQL，Docker 没起时全量必 FAIL（**不是回归**）。⚠️ **改闸门必须同步改 `BankBindTest`**（绑卡有 DEPOSIT 闸门）。
- **接口测试资产（用户自跑）**：
  - **Postman**：`docs/postman/ww_finance.postman_collection.json` + `..._environment.json`（104 请求 / 12 目录，覆盖全部 103 接口）。**集合级 Pre-request 脚本自动登录取 token**（adminToken/userToken/investorToken；JWT exp 提前 60s 刷新 + 401 自愈重放）⇒ **导入即跑，不用管 token**。生成器 `generate_collection.py`（改接口后重跑）。
  - **JMeter**：`docs/jmeter/ww_finance_benchmark.jmx`。SetupThreadGroup 三方登录取 token → `props.put` → `__P(adminToken)`；主场景**只读接口**（充值/提现/投标/还款有脏数据风险，不压）。CLI：`jmeter -n -t ww_finance_benchmark.jmx -l result.jtl -e -o report`。
  - 报告/截图统一落 `docs/test-reports/`（`.html`/`.csv`/`.json`/`shots/` 15 张 PNG）。执行器 `.workbuddy/tmp/run_api_tests.py`。**实测 58 执行 / 58 通过 / 100%**。
  - ⚠️ 易踩坑：`dict/listByCode` 参数名是 **`dictCode`**；`prepare` 必须用借款人本人名下的标（集合变量 `prepLendId=5`）；`chat/message/list` 需本人会话（已给用户 30 插 fixture 会话 **id=55**）。
  - 🔴 **真缺陷待修**：`prepare` 非签署方调用 → 查重键与写入 `party_a_id` 不一致 → 撞 `uk_biz` → **500 + msg 泄露 SQL 异常**（应 403）。
  - 截图脚本 `.workbuddy/tmp/shot_report.js`（playwright + 显式 executablePath chromium-1228；**agent-browser 在沙箱内挂起不可用**）。使用说明 `docs/postman/接口测试使用说明.md`。

## 测试账号
- **全部密码 `123456`**（明文/MD5/BCrypt 混合，**仅 BCrypt 能登录**）。管理员 **admin/123456**。
- 集合内置三身份（已验证）：借款人 **18538313405**（bind=1）；投资人 **14725836945**（invest_auth=2）。
- 干净借款人：**35 / 张三 / 13800001111**。干净且已绑卡投资人：**22/26/28/29/32**（**42/张伟** 银行余额 −650.60 异常）。✅ **投标不校验 `invest_auth_status`**：①`user_type=1` ②绑卡且银行 bind_code 在 ③余额够 即可。
