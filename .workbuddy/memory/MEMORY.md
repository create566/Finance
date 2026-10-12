# ww_finance（D:\桌面\实训\demo）

P2P 借贷：发标→投标→满标→签放款确认书→放款→分期还款。**平台不持有资金**；存管银行 `ww_bank-1.0.0.jar`（手动起 9090，无源码）。动手前看 mtime；细节查 `deep-dive.md` 与 skill `finance-demo-build`/`docx-batch-restyle`。

## 结构/端口
api(8990)/ai(8081,**Boot3.4+Java17**)/message(8120)/gateway(8088)/oss(8130)。前端 admin(5173)/user(5174) 代理网关 8088。Nacos 8848 只注册（api/message/oss/gateway；**ai 不注册，直连 8081**）。MySQL root/123456 `ww_finance`；Mongo `ww_finance_ai`；Docker 只 redis+chroma。银行库在**阿里云 RDS `ww_bank`**（无法本地备份）。

## 构建/部署
- 父 pom 自建 ⇒ `spring-boot-maven-plugin` **必须显式 `<executions><goal>repackage</goal>`**（否则只出 thin jar）；打包 JDK8、ai JDK17，**单测必须 JDK17**。部署 `实训jar包`：`manage.sh`，**先设 JAVA8_HOME/JAVA17_HOME**。详见 skill `finance-demo-build`。
- 🔴 旧实例缓存 `NoClassDefFoundError` ⇒ 单一动作持续 500，**重启即愈**。Nacos 三坑已修：standalone+auth.enabled=false+删 db.lck。前端 502=服务没注册进 Nacos。
- 🔴 AI **独立 CORS 白名单**（`finance-ai` 的 `config/CorsConfig.java`）：非 GET 带 Origin 不在名单即 403 ⇒ **加公网入口必须同步改**。

## 环境铁律
- bash 先 `export PATH="/usr/bin:/bin:/c/Windows/System32:/c/Windows:$PATH"`；不占 8081；自测用 8991/8082 **测完必杀**+`register-enabled=false`；永远显式传端口。
- curl 本机加 `--noproxy "*"`；mysql 加 `--default-character-set=utf8mb4`；日志 `实训jar包/logs/`+`demo/logs/<svc>/`。

## 资金链路
- **余额真源=银行库 `user_account`**；可用额唯一真源 `BankAccountSyncService.getAvailableBalance(userId)`（银行余额−Σstatus=0 在途，钳 0）。投标=银行直接扣款（freeze 恒 0）。
- `trans_flow` status 0/1/2；幂等只认终态 SUCCESS；**手写 SQL 必带 `is_deleted=0`**；trans_type 1充值/2提现/3投标/4回款/5放款/6还款。
- 放款=服务端同步（`resultCode=0000`）；🔴 `makeLoan` 受益人取 `user_invest.benefit_bind_code` **最后一行** ⇒ 投标必传 `benefitBindCode=借款人 bindCode`。
- 🔴 改还款代码前必读 `returnCommit` 强等式（double）与两段式（AgreeUserRepayment→returnCommit）；幂等不均⇒按 `agent_batch_no` 去重；对账心跳 `RECON-HEARTBEAT-<日期>`。

## 标的循环 / 合同
- **放款闸门=PAYMENT《放款确认书》**（满标后签，超期定时退资）；⚠️ E2E 必补签 PAYMENT。LOAN=建标+上架前；INVEST+SERVICE=投标前；DEPOSIT=绑卡。⚠️ PAYMENT 强校验别加回 `publish()`。
- `LendStatusEnum` 0待发布/1募资/2满标/3逾期/4下架/5还款中/6完结/7流标。下架/流标必须共用 `withdrawAllInvestments`(status in (0,2))+`refundInvestment`（**只能走 RETURN**）。合同 5 类/`revoke()`/`uk_biz` 见 deep-dive。

## 平台约定
- 分页必须 `PageUtils.of`；`lend.period`=投资期数（月），「投资记录」`term`=募资天数。
- 注册 `uk_mobile` 真 UNIQUE+捕 `DuplicateKeyException`；**投资门槛=`user_type==1`，借款门槛=`borrow_auth_status==2`**。权限=`sys_admin.role`+JWT `token_admin_role`+`@RequireAdminRole`。
- 收银台 `utils/bankForm.ts#openBankForm`；登录态 Redis `login:token:<role>:<id>`（user 头 `Authorization`/admin 头 `token`）。站内信/统计接口见 deep-dive。
- 🔴 `@Transactional`+整段 try/catch 吞异常=静默部分提交（最典型缺陷）。给实体加字段必须同批跑 DDL。白名单 22 类/147 用例看 `api/target/surefire-reports/`。

## 测试 / 账号
- 账号密码全 `123456`；admin/123456；借款人 33/15138019132；投资人 22/17431209467、42/张伟；干净借款人 35/张三/13800001111。演示数据 `LEND2026…DEMO01~06` 别动。集成类依赖 Redis/MySQL，Docker 没起必 FAIL。

## 报告 / 前端
- 📄 `D:\桌面\实训\报告`：学院格式版四份（项目方案/API接口文档/大纲/测试报告，A4 左3.1/右1.9、正文5号宋体20磅、章4号黑体，配方 skill `docx-batch-restyle`）+ `re\…-项目方案（商丘师范格式）.docx`（封面按官方《本科毕业论文（设计）封面》逐段复刻；坑见 2026-10-12 日志：docDefaults after=200 会让 25磅行距变 35磅）。
- 🔴 docx `add_picture` 新段落必须覆盖行距（Normal 20磅 exact 压出页）。⛔ 并行多次 Edit 同一文件必互相覆盖 ⇒ 顺序改。前端 `vue-tsc -b`。
