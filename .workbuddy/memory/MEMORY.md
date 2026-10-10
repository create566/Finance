# ww_finance（D:\桌面\实训\demo）

P2P 借贷：发标→投标→满标→借款人签放款确认书→放款→分期还款。**平台不持有资金**；存管银行 `ww_bank-1.0.0.jar`（手动起 9090，无源码）。
> ⚠️ 长尾实证在 `reference/deep-dive.md`（先 Read）；本文只留高频铁律。仓库常有并行改动，动手前看 mtime；共享写入点（本文、`docs/遗留问题与整改清单.md`）写前重读。

## 结构/端口
api(8990,service-core)/ai(8081)/message(8120)/gateway(8088)/oss(8130)。Boot 2.3.4+MyBatis-Plus；**ai 例外 Boot 3.4.8+Java17**。前端 admin(5173)/user(5174) 统一代理网关 8088。Nacos 8848 只注册中心；注册方=api/message/oss/gateway，**ai 未注册**（`/ai/**` 直连 8081）；银行 9090 由 api 直连。MySQL 3306 root/123456 `ww_finance`；Mongo 27017；Docker 只 redis+chroma。⚠️ 银行库在阿里云 RDS(`ww_bank`) 无法本地备份。

## 构建/部署
- ⚠️ 父 pom 自建（无 starter-parent）⇒ `spring-boot-maven-plugin` **必须显式 `<executions><goal>repackage</goal></executions>`**（只配 classifier 出 thin jar）。上传=5 个 `-exec.jar`+`ww_bank-1.0.0.jar`；JDK8(api/gateway/message/oss/bank)/JDK17(ai)。`Nothing to compile` 不代表改动进包 ⇒ 开 jar 核验。
- ⚠️ 排依赖三禁：`springfox-swagger2`、message 的 `nacos-discovery`、`finance-common` 的 security；lombok 别进 fat jar。
- 部署包 `D:\桌面\实训jar包`：`manage.sh <start|stop|restart|status|logs> <svc>`，**必须先设 `JAVA8_HOME`/`JAVA17_HOME`**（否则退 JDK24 ⇒ 全崩、伪装成 Nacos 注册失败）。一键 `start-nacos.sh`（等 readiness=OK）→ `start-all.ps1 start`（UTF-8 带 BOM）。
- 🔴 Nacos 三坑（已修，备份 `*.bak_20261010_095322`）：cluster→**standalone**（ErrCode:400）；`auth.enabled`→**false**（ErrCode:403）；硬杀后删 `data/derby-data/db.lck`。
- 🔗 前端 502：网关/oss/sms/core **必须先注册进 Nacos 才能启动** ⇒ Nacos 未就绪 ⇒ 8088 不在 ⇒ 502（前端无 bug）。先起服务后起 Nacos 无效，须重起服务。

## 环境铁律
- **不占 8081**；自测用 8082/8991/8220/8230，**测完必杀**；沙箱注入 `SERVER_PORT` ⇒ **永远显式传端口**；**进程随启动 shell 退出而死** ⇒ 起服务+自测放同一常驻任务。自测实例会自注册进 Nacos，杀后 ~15s 内走网关必 500（非故障）；不想污染加 `--spring.cloud.nacos.discovery.register-enabled=false`。
- bash 先 `export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"`；PortableGit 无 git；mysql 读加 `--default-character-set=utf8mb4`；javap 用 jdk-17；托管 python 不认 `/d/桌面/...` 先 cd；curl 本机加 `--noproxy "*"`；非 HTTP 探活用 `/dev/tcp`；改 finance-common 后 `-f api/pom.xml` 必带 `-am`。冷启动 message 35s / oss 45s，以 `Started XxxApplication` 为准。
- 🔴 旧 api 实例缓存 `NoClassDefFoundError` ⇒ 单一动作持续 500，**重启即恢复**（症状=表单全绿、唯独放款 500）。日志两处：`实训jar包/logs/*.log` + `demo/logs/<svc>/<svc>.log`。

## 资金链路（谁是账本）
- **余额真源=银行库 `user_account`（`user_code`=bindCode）**，平台表只是镜像。**可用额唯一真源 `BankAccountSyncService.getAvailableBalance(userId)`**=银行余额−Σ(status=0 占位流水)，钳 0；提现/投标/还款全调它。⚠️ **投标=银行直接扣款**（非冻结，`freeze_amount` 恒 0）。
- `trans_flow.status` 0处理/1成功/2失败；幂等 `existsSucceededByTransNo()`（只认 SUCCESS）；在途占位兜底 `releaseStalePendingFlows`（阈值 `job.pending-flow.timeout-minutes` 默认 10）；**手写 SQL 必带 `is_deleted=0`**。`trans_type` 1充值/2提现/3投标/4回款/5放款/6还款；退资回补复用 4(RETURN)。平台只赚服务费且一分未收（fee 硬编码 0）。
- ✅ 放款=服务端同步（`resultCode=0000` 即成功，`mchFee=0`）。🔴 放款「银行无应答」=银行侧 NPE：`makeLoan` 受益人取 DB `user_invest.benefit_bind_code`（请求 bindCode 被忽略，循环覆盖 ⇒ 生效值=该项目**最后一行**），值为 `'0'` ⇒ NPE。⚠️ 投标表单必传 `benefitBindCode=借款人 bindCode`（已修）。
- ⚠️ 银行 jar 单边记账+无 null 兜底（查无此户即 NPE）⇒ 操作前确认 bind_code 在银行有户。✅ 退资回补 `LendItemService.refundInvestment(item,memo)` 一处完成，**只能走 RETURN**。6 回调都验签；前端充值/提现/还款 `responseType:'text'`，业务失败=200+JSON `{"code":400}`。定时：逾期 02:00/对账 03:30/未满标失败 01:00/宽限期退资 01:30。
- ✅ 对账心跳 `trans_no=RECON-HEARTBEAT-<日期>` 一天一行 ⇒ 别把 "reconciliation_record 0 行" 当"没差异"。⚠️ 银行幂等不均（`vote`/`makeLoan` 有，`recharge`/`withdraw`/`returnCommit` 无）⇒ 按 `agent_batch_no` 去重。平账：镜像 `user_account` 是纯 ±增量账本 ⇒ **看本轮 Δ 增量而非绝对额**。

## 标的循环 / 合同
- ✅ **放款闸门=PAYMENT（放款确认书，甲方=借款人）**；满标后系统主动催签（`MAKE_LOAN_PENDING(30)`），超期未签定时任务自动终止撮合并退款（`MAKE_LOAN_TIMEOUT(31)`）。⚠️ **E2E 必须补"借款人签 PAYMENT"，否则放款被拦（易误判成缺陷）**。⚠️ LOAN 只在 `publish()` 校验 ⇒ 走 `AdminLendController.create` 绕过属设计使然。
- 还款三铁律：① total=已舍入 principal+interest；② 投资人明细按投资占比切分；③ 倒挤到投资额最大一户。还款**两段式**：`AgreeUserRepayment`（不动钱）→ `returnCommit`（真扣款，不验签）；回调 `/api/core/lendReturn/notifyUrl`。⚠️ 银行 `returnCommit` 强等式 `voteFeeAmt+ΣtransitAmt==totalAmt`（double），改还款代码前必读。`LendStatusEnum` 0待发布/1募资中/2满标待放款/3逾期/4下架/5还款中/6完结/7流标。
- **5 类合同**：LOAN/INVEST/PAYMENT/SERVICE(居间)/DEPOSIT(存管)；`ContractBizTypeEnum`=LEND/BORROW_INFO/LEND_ITEM/USER（用户级 `biz_id=party_a_id=userId`、`party_b_id=NULL`）。✅ 主体：`TPL_LOAN` v3=甲方(借款人)/乙方(全体出借人)；⚠️ `party_b_id` 为 NULL 是"每人一份"有意建模，别当缺陷。闸门：LOAN=建标+上架前/INVEST=投标前/PAYMENT=放款前/SERVICE=发标+投标/DEPOSIT=绑卡。`detail` 返 `signable`（可见≠可签）；`revoke()` 只作废待签且 reason 必填；`offline(lendId,adminId)` 必传操作人；宽限期起算 `complete_time`。⛔ 罚息归平台、满标不签章、正文未渲染还款计划表（设计取舍）。

## 平台约定
- 分页必须 `PageUtils.of(pageNum,pageSize)`（MAX_PAGE_SIZE=100/MAX_PAGE_NUM=10000）。⚠️ `lend.period`=投资期数（**月/期**），管理端 `term` 就是它，展示拼"个月"不是"天"；⚠️ 而「投资记录」的 `term` 是募资窗口**天数** —— 同名不同义。
- 状态文案真源 `ww_finance_user/src/utils/enumText.ts`；加站内信类型须同改 Java 枚举（已到 31）+ 前端 `MESSAGE_TYPE_META` + 统计 SQL。登录态 Redis `login:token:<role>:<id>`（`5grcs ` 前缀、值全等）；Docker 未起 ⇒ 6379 不可达 ⇒ /api、/admin 全进不去。
- 脚本调接口三铁律：token 走**真实登录 API**（别手搓 JWT）；用隔离端口；输出写文件再 Read。admin 登录 `POST /admin/core/login`（响应 `data` 即 token）；头 **`token`**（前台 `Authorization`）；放款 `PUT /admin/core/loan/project/{lendId}/makeLoan`（前置 lend status=2+PAYMENT status=1）。
- ✅ 银行收银台一律新窗口：`utils/bankForm.ts#openBankForm`（三态；被拦不回退、不谎报成功）；6 个 returnUrl 全指免登录 `/bank/return` 且按请求 Origin 动态推导（`HfbConst.returnUrl()`）。
- ✅ 手机号唯一（UNIQUE+捕 `DuplicateKeyException`）。角色：借款=`borrow_auth_status=2`；投资=`user_type==1` 或 `invest_auth_status==2`；投标不校验 invest_auth。权限=`sys_admin.role`+JWT `token_admin_role`+`@RequireAdminRole`（不能用 `@PreAuthorize`）。

## 测试 / 账号 / 文档
- 白名单 21–22 类用例全绿（`-pl api -am`）；集成类依赖 Redis/MySQL，Docker 没起必 FAIL（非回归）。资产：Postman `docs/postman/`（105 请求自动取 token）、JMeter `docs/JMeter/`（未跑过）、报告 `docs/test-reports/`（全量轮 90/105）。
- 账号密码全 `123456`；管理员 admin/123456。借款人 33/15138019132、投资人 22/17431209467；干净借款人 35/张三/13800001111。日志根 `D:/桌面/实训/demo/logs`。
- 📄 报告产物 `D:\桌面\实训\报告`（项目方案 / API 接口文档 / 项目大纲，依《学生资料清单》规范）。取材：`docs/数据模型与表关系总览.html`、`docs/旺旺金融P2P信贷撮合系统_业务概述.docx`、`docs/微服务架构与启动说明.md`、`docs/服务器部署与测试方案.md`、`docs/postman/接口测试使用说明.md`。
