# ww_finance 金融信贷系统 学生开发 API 接口文档

> 项目代号：`ww_finance-master`
> 文档版本：v1.0 · 2026-09-07
> 目标读者：参与本项目后端开发的学生工程师
> 内容来源：`D:\919办公室\金融信贷源码\资料\后端项目\ww_finance-master`

---

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档名称 | ww_finance 学生开发 API 接口文档 |
| 适用版本 | ww_finance-master（main 分支当前快照） |
| 技术栈 | Spring Boot 2.3.4 + Spring Cloud Hoxton.SR8 + Spring Cloud Alibaba 2.2.2 |
| 接口总数 | 60+ 个 REST 接口，覆盖 20 个 Controller |
| 鉴权方式 | JWT（HS512），请求头 `Authorization: 5grcs <token>` |
| 文档目的 | 快速理解接口清单、掌握参数约定、明确开发优先级 |

---

## 目录

1. [项目概述](#一项目概述)
2. [整体架构](#二整体架构)
3. [技术栈与依赖](#三技术栈与依赖)
4. [开发与访问规范](#四开发与访问规范)
5. [统一返回与错误码](#五统一返回与错误码)
6. [业务域接口清单](#六业务域接口清单)
   - 6.1 用户与认证
   - 6.2 账户绑定与充值
   - 6.3 借款人认证
   - 6.4 借款申请
   - 6.5 标的与投资
   - 6.6 还款计划
   - 6.7 交易流水
   - 6.8 数据字典
   - 6.9 短信服务
   - 6.10 文件上传
   - 6.11 后台管理
7. [数据模型与 DTO](#七数据模型与-dto)
8. [开发指引](#八开发指引)
9. [附录](#九附录)

---

## 一、项目概述

### 1.1 项目定位

**ww_finance** 是一套面向互联网金融场景的"信贷 + 投资"双端撮合系统，前端用户（出借人/借款人）和后台管理人员共用一套后端服务：

- **前台用户端**（`/api/core/**`、`/api/sms/**`、`/api/oss/**`）面向投资人、借款人，本地开发经 Gateway(80) 接入。
- **后台管理端**（`/admin/core/**`）面向平台运营人员，复用 core 服务但走独立路径前缀以便独立鉴权与权限控制。

### 1.2 核心业务流程

```
借款人 ─► 提交认证 ─► 借款申请 ─► 平台审批 ─► 生成标的 ─► 投资人投资 ─► 满标放款 ─► 还款计划 ─► 按期还款
   │                                                                                            │
   └─► 充值（绑银行卡） ─► 账户余额 ─► 投资/还款                                                                                  │
                                                                                          ◄── 交易流水 ──┘
```

### 1.3 角色与权限

| 角色 | 主要使用接口 | 说明 |
|------|------------|------|
| 未登录用户 | 登录、注册、发送验证码 | 通过 `/api/core/user/**` 与 `/api/sms/**` |
| 借款人 | 借款人认证、借款申请、还款 | 完成 Borrower 认证 + 提交借款信息 |
| 投资人 | 浏览标的、投资、查看流水 | 完成账户绑定 + 充值即可投资 |
| 平台管理员 | 后台审核、放款 | 经 `/admin/core/**`，需管理员登录态 |

---

## 二、整体架构

### 2.1 微服务架构

本项目采用 Spring Cloud Alibaba 微服务架构，共 6 个模块：

| 模块 | 服务名 | 端口 | 角色 |
|------|--------|------|------|
| `finance-common` | — | — | 公共基础（统一返回 PccAjaxResult / 全局异常 / 工具类） |
| `finance-base` | — | — | 公共依赖聚合（Swagger、Knife4j、JWT、Nacos、Feign、Sentinel） |
| `finance-api` | `service-core` | **8110** | 核心业务 API（Controller / Service / Mapper / 实体） |
| `finance-gateway` | `service-gateway` | **80** | Spring Cloud Gateway 网关，统一入口 |
| `finance-message` | `service-sms` | **8120** | 短信微服务（模拟） |
| `finance-oss` | `service-oss` | **8130** | 文件上传服务（本地 + Aliyun OSS【需要扩展】） |

### 2.2 调用关系

```
         前端 / 移动端
                │
                ▼
   ┌────────────────────────────┐
   │  finance-gateway  (:80)   │  ← Spring Cloud Gateway
   │  路由: /api/core/**         │
   │        /api/sms/**          │
   │        /api/oss/**          │
   │        /admin/core/**       │
   └────────────────────────────┘
       │             │           │
       │             │           │
       ▼             ▼           ▼
 ┌──────────┐  ┌──────────┐  ┌──────────┐
 │ service- │  │ service- │  │ service- │
 │  core    │◄─┤   sms    │  │   oss    │
 │  (:8110) │  │  (:8120) │  │  (:8130) │
 └──────────┘  └──────────┘  └──────────┘
       ▲             │
       │ Feign       │
       └─────────────┘
        (CoreUserInfoClient
         校验手机号)
```

- **core → sms**：`SMSApiSmsClient.sendMsg()`（投资满标、放款、还款提醒时调用）
- **sms → core**：`CoreUserInfoClient.checkMobile()`（发送注册验证码前校验手机号是否已注册）

### 2.3 数据库与缓存

| 组件 | 配置 | 用途 |
|------|------|------|
| MySQL | `172.16.5.213:3306` / `ww_finance` 库 | 持久化 15 张业务表 |
| Redis | `localhost:6379`（lettuce 客户端） | 验证码缓存（5 分钟）、分布式 Session、可选缓存 |
| Nacos | `localhost:8848` | 服务注册 + 配置中心 |

---

## 三、技术栈与依赖

| 类别 | 技术 | 版本 |
|------|------|------|
| 基础框架 | Spring Boot | `2.3.4.RELEASE` |
| 微服务 | Spring Cloud | `Hoxton.SR8` |
| 微服务 | Spring Cloud Alibaba | `2.2.2.RELEASE` |
| 持久层 | MyBatis-Plus | `3.4.1` |
| 持久层 | MySQL Connector/J | — |
| 缓存 | Spring Data Redis + commons-pool2 | — |
| 鉴权 | jjwt | `0.7.0` |
| API 文档 | Swagger 2 + Knife4j | `swagger 2.9.2` / `knife4j 2.0.8` |
| 服务注册/配置 | Nacos Discovery + Config | `2.2.2.RELEASE` |
| 服务调用 | OpenFeign | — |
| 容错 | Sentinel | — |
| 短信 | 容联云 `java-sms-sdk` | `1.0.4` |
| 对象存储 | Aliyun OSS SDK | `3.17.4` |
| 定时任务 | xxl-job-core | `2.3.1` |
| 工具 | Lombok / fastjson / gson / commons-lang3 / commons-io / httpclient | — |

---

## 四、开发与访问规范

### 4.1 网关路由规则

所有外部请求经 `service-gateway (80)` 进入，路由规则如下：

| 外部路径前缀 | 转发目标 | 说明 |
|-------------|---------|------|
| `/api/core/**` | `lb://service-core` | 前台用户核心业务 |
| `/admin/core/**` | `lb://service-core` | 后台管理（共用 service-core，路径前缀隔离） |
| `/api/sms/**` | `lb://service-sms` | 短信服务 |
| `/api/oss/**` | `lb://service-oss` | 文件服务 |

> **学生开发提示**：本地调试若 gateway 未启动，可直接访问各微服务真实端口（8110/8120/8130），但请求头与路径完全一致。

### 4.2 鉴权规范

绝大多数需要登录态的接口都在 Controller 中通过 **`@RequestHeader("Authorization")` + 自研 `TokenUtil`** 解析。

- **Header 格式**：

```
Authorization: 5grcs eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxMzg4MDAwMDAwMSIs...
```

- **令牌生成**：`finance-base/finance-api` 的 `utils.TokenUtil`，使用 **HS512**，密钥 `vsofo-5grcs-secret`，有效期 **7 天**。
- **解析结果**：从 JWT payload 取字段 `token_userid`（登录用户手机号），业务侧再据此查询 user。

> 路径含 `/auth/` 关键字的接口**绝大多数需要鉴权**；无 `/auth/` 的接口为公开接口。
> 另：`/notify` 类异步回调接口无需鉴权，按银行/三方支付签名 + JSON 验签。

### 4.3 跨域

网关层配置 `CorsConfig`，全局 `Access-Control-Allow-Origin=*`，允许携带 Cookie，前端无需单独处理。

### 4.4 接口版本与命名

- **路径前缀**：`/api/core/<资源>` 或 `/admin/core/<资源>`，资源名采用驼峰复数或业务域命名（如 `borrowInfo`、`lendItem`）。
- **HTTP 方法语义**：`GET` 查询、`POST` 创建/动作（含非幂等操作）、`PUT`/`PATCH` 更新（少量接口用到）、`DELETE` 删除。
- **接口命名**：驼峰动词性 URL（如 `getIndexUserInfo`、`commitInvest`、`makeLoan`）。

---

## 五、统一返回与错误码

### 5.1 统一返回结构

所有 Controller 方法的返回类型为 `com.wwfinance.common.result.PccAjaxResult`（本质 `HashMap`），结构如下：

```json
{
  "code": 200,
  "msg": "成功",
  "data": { /* 业务对象，可为 List/Map/简单类型/null */ }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 状态码：200=成功；其他=失败 |
| `msg` | String | 提示信息 |
| `data` | Object | 业务数据，可空 |

### 5.2 常见返回码

| code | 含义 | 常见场景 |
|------|------|----------|
| `200` | 成功 | 业务正常处理 |
| `301` | 参数错误 | 校验未通过（如身份证号非法） |
| `401` | 未授权 | JWT 缺失/过期/无效 |
| `403` | 禁止访问 | 权限不足 |
| `404` | 资源不存在 | 记录不存在 |
| `500` | 服务器异常 | 业务未捕获异常 |

### 5.3 异步回调返回值

银行/三方异步通知（如绑定、充值、投资、还款）返回值约定为 **`String`**，必须返回：

- `"success"` —— 业务侧验签并处理成功
- `"fail"` —— 失败（银行侧会重试）

### 5.4 全局异常处理

`com.wwfinance.common.exception` 提供：

- `GlobalExceptionHandler`（`@RestControllerAdvice`）：统一捕获 `BizException`、`ResourceNotFoundException`、`ValidationException`、参数绑定异常，返回标准 `PccAjaxResult`。
- `ResourceNotFoundException`：抛出会被转为 `404`。

---

## 六、业务域接口清单

> 接口表头：`HTTP 方法 | URL | 入参 | 鉴权 | 业务说明`
> 鉴权列：`✅` 表示需要 `Authorization` 头；`—` 表示公开。

### 6.1 用户与认证

| HTTP | URL | 入参 | 鉴权 | 说明 |
|------|-----|------|:---:|------|
| GET | `/api/core/user/hello` | 无 | — | 连通性测试 |
| POST | `/api/core/user/login` | `User{mobile, password}`（MD5 加密） | — | 登录，校验手机号/密码/用户类型，写登录日志，返回 JWT |
| GET | `/api/core/user/checkMobile/{mobile}` | 路径变量 | — | 校验手机号是否可用（被 sms 服务 Feign 调用） |
| GET | `/api/core/user/userInfo` | — | ✅ | 获取当前登录用户信息 |
| POST | `/api/core/user/register` | `UserDTO{userType, mobile, code, password, passwordto}` | — | 注册：校验验证码 + 手机号唯一 + 两次密码一致，写 user 与 user_account |
| GET | `/api/core/user/logout` | — | — | 退出登录（当前实现仅返成功） |
| GET | `/api/core/user/auth/getIndexUserInfo` | — | ✅ | 个人空间首页信息（UserIndexDTO） |

**请求示例 - 登录**：

```http
POST /api/core/user/login
Content-Type: application/json

{
  "mobile": "13800000001",
  "password": "e10adc3949ba59abbe56e057f20f883e"  // MD5("123456")
}
```

**响应示例**：

```json
{
  "code": 200,
  "msg": "成功",
  "data": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxMzg4MDAwMDAwMSIs..." 
  // JWT，前端需存入 storage，后续请求加到 Authorization 头（带 5grcs 前缀）
}
```

> **Controller**：`com.wwfinance.controller.api.UserController`
> **路径**：`finance-api/src/main/java/com/wwfinance/controller/api/UserController.java`

---

### 6.2 账户绑定与充值

#### 6.2.1 UserBindController（账户绑定）

| HTTP | URL | 入参 | 鉴权 | 说明 |
|------|-----|------|:---:|------|
| GET | `/api/core/userBind/getBindInfo` | — | ✅ | 查询当前用户绑定信息 |
| POST | `/api/core/userBind/auth/bind` | `UserBindDTO{idCard, name, bankType, bankNo, mobile}` | ✅ | 提交绑定，调用汇付宝接口，返回 form 表单串 |
| POST | `/api/core/userBind/notify` | `HttpServletRequest`（银行回调参数） | 验签 | 汇付宝异步回调：验签后更新绑定状态 |

#### 6.2.2 UserAccountController（账户与充值）

| HTTP | URL | 入参 | 鉴权 | 说明 |
|------|-----|------|:---:|------|
| GET | `/api/core/userAccount/auth/commitCharge/{chargeAmt}` | 路径变量 `BigDecimal` | ✅ | 提交充值，返回 form 表单串 |
| POST | `/api/core/userAccount/notify` | `HttpServletRequest` | 验签 | 充值异步回调，同步账户余额 |
| GET | `/api/core/userAccount/auth/getAccount` | — | ✅ | 查询账户可用余额（BigDecimal） |

> **汇付宝对接要点**：`xxBank.RequestHelper` 构造签名、`xxBank.FormHelper` 生成自动提交表单、`xxBank.HfbConst` 常量。学生开发时主要复用这三个工具类。
> **Controller**：
> - `finance-api/src/main/java/com/wwfinance/controller/api/UserBindController.java`
> - `finance-api/src/main/java/com/wwfinance/controller/api/UserAccountController.java`

---

### 6.3 借款人认证

| HTTP | URL | 入参 | 鉴权 | 说明 |
|------|-----|------|:---:|------|
| POST | `/api/core/borrower/auth/save` | `BorrowerDTO{sex, age, education, marry, industry, income, returnSource, contactsName, contactsMobile, contactsRelation, List<BorrowerAttach>}` | ✅ | 借款人认证信息保存，同时写 `borrower` + `borrower_attach` + 更新 `user` 字段 |
| GET | `/api/core/borrower/auth/getBorrowerStatus` | — | ✅ | 获取借款人认证状态（0=未认证, 1=认证中, 2=通过, -1=拒绝） |

> **Controller**：`com.wwfinance.controller.api.BorrowerController`
> **路径**：`finance-api/src/main/java/com/wwfinance/controller/api/BorrowerController.java`

---

### 6.4 借款申请

| HTTP | URL | 入参 | 鉴权 | 说明 |
|------|-----|------|:---:|------|
| GET | `/api/core/borrowInfo/auth/getBorrowInfoStatus` | — | ✅ | 查询当前用户借款申请审批状态 |
| GET | `/api/core/borrowInfo/auth/getBorrowAmount` | — | ✅ | 查询可借额度（基于积分等级规则） |
| POST | `/api/core/borrowInfo/auth/save` | `BorrowInfo{...}`（含金额、用途、期数、年化、还款方式等） | ✅ | 提交借款申请，进入审批流程 |

> **Controller**：`com.wwfinance.controller.api.BorrowInfoController`

---

### 6.5 标的与投资

#### 6.5.1 LendController（api 包）

| HTTP | URL | 入参 | 鉴权 | 说明 |
|------|-----|------|:---:|------|
| GET | `/api/core/lend/list` | — | — | 标的列表（公开） |
| GET | `/api/core/lend/show/{id}` | 路径变量 `Long` | — | 标的详情（含借款人信息、投资进度） |
| GET | `/api/core/lend/getInterestCount/{invest}/{yearRate}/{totalmonth}/{returnMethod}` | 路径变量 4 个 | — | 投资收益计算器 |
| GET | `/api/core/lend/auth/recommend` | Query `topN`（默认 5） | ✅ | 基于 Item-CF 协同过滤的标的推荐 |

#### 6.5.2 LendItemController（投资记录与下单）

| HTTP | URL | 入参 | 鉴权 | 说明 |
|------|-----|------|:---:|------|
| GET | `/api/core/lendItem/list/{lendId}` | 路径变量 `Long` | — | 某标的的投资记录列表 |
| POST | `/api/core/lendItem/auth/commitInvest` | `InvestDTO{lendId, investAmount, investUserId, investName}` | ✅ | 提交投资，返回支付 form 表单串 |
| POST | `/api/core/lendItem/notify` | `HttpServletRequest` | 验签 | 投资异步回调，验签后写投资记录、扣减标的金额 |

> **Controller**：
> - `com.wwfinance.controller.api.LendController`
> - `com.wwfinance.controller.api.LendItemController`

---

### 6.6 还款计划

| HTTP | URL | 入参 | 鉴权 | 说明 |
|------|-----|------|:---:|------|
| 任意 | `/api/core/lendReturn/list/{id}` | 路径变量 `Long`（标的 ID） | — | 查询还款计划列表（Controller 未限定 HTTP 方法） |

> **Controller**：`com.wwfinance.controller.api.LendReturnController`
> **路径**：`finance-api/src/main/java/com/wwfinance/controller/api/LendReturnController.java`

---

### 6.7 交易流水

| HTTP | URL | 入参 | 鉴权 | 说明 |
|------|-----|------|:---:|------|
| GET | `/api/core/transFlow/list` | — | ✅ | 当前用户交易流水 |

> **Controller**：`com.wwfinance.controller.api.TransFlowController`

---

### 6.8 数据字典

| HTTP | URL | 入参 | 鉴权 | 说明 |
|------|-----|------|:---:|------|
| GET | `/api/core/dict/findByDictCode/{dictCode}` | 路径变量 `String` | — | 按字典编码取下级字典节点（如教育背景、行业） |

> **Controller**：`com.wwfinance.controller.api.DictController`

---

### 6.9 短信服务

服务名 `service-sms`，端口 8120。

| HTTP | URL | 入参 | 鉴权 | 说明 |
|------|-----|------|:---:|------|
| POST | `/api/sms/user/sendSMS` | Query `mobile` | — | 发送注册验证码：先 Feign 调 core 校验未注册 → 容联云发送 → 写入 Redis `xx:code:{mobile}`，TTL 5 分钟 |
| GET | `/api/sms/user/sendMsg/{mobile}` | 路径变量 | — | 发送催还款短信（被 core 通过 Feign 调用） |

> **Controller**：`com.wwfinance.controller.api.ApiSmsController`
> **路径**：`finance-message/src/main/java/com/wwfinance/controller/api/ApiSmsController.java`

---

### 6.10 文件上传

服务名 `service-oss`，端口 8130。

| HTTP | URL | 入参 | 鉴权 | 说明 |
|------|-----|------|:---:|------|
| GET | `/api/oss/file/hello` | 无 | — | 连通性测试 |
| POST | `/api/oss/file/upload` | Form `file: MultipartFile` | ✅ | 上传文件（本地落地至 `D:/uploads/`，单文件上限 50MB） |
| DELETE | `/api/oss/file/remove` | Query `url` | ✅ | 删除已上传文件 |

> **Controller**：`com.wwfinance.oss.controller.api.FileController`
> **路径**：`finance-oss/src/main/java/com/wwfinance/oss/controller/api/FileController.java`

---

### 6.11 后台管理

所有后台接口前缀 `/admin/core/**`，共享 `service-core` 实例，前端拼上 `/admin` 段即可。

| # | Controller | 接口路径 | 说明 |
|---|-----------|---------|------|
| 1 | **AdminUserController** | `POST /admin/core/user/list/{page}/{limit}` | 会员分页列表（按 mobile/status/userType 过滤） |
|   |                    | `POST /admin/core/user/lock/{id}/{status}` | 锁定/解锁用户（0锁定, 1解锁） |
| 2 | **AdminUserLoginRecordController** | `GET /admin/core/userLoginRecord/listTop50/{userId}` | 会员登录日志 Top 50 |
| 3 | **IntegralGradeController**        | 任意方法 `/admin/core/integralGrade/list`        | 积分等级列表 |
|   |                                    | 任意方法 `/admin/core/integralGrade/save`        | 新增积分等级 |
|   |                                    | 任意方法 `/admin/core/integralGrade/removeById`  | 删除积分等级 |
|   |                                    | `POST /admin/core/integralGrade/selectById`      | 按 ID 查询 |
|   |                                    | `POST /admin/core/integralGrade/update`          | 更新积分等级 |
| 4 | **AdminBorrowerController**        | `POST /admin/core/borrower/list/{page}/{limit}`  | 借款人分页（模糊搜姓名/身份证/手机） |
|   |                                    | `GET /admin/core/borrower/show/{id}`             | 借款人详情（含 BorrowerAttach 列表） |
|   |                                    | `POST /admin/core/borrower/approval`             | 借款人认证审批 |
| 5 | **AdminBorrowInfoController**      | `GET /admin/core/borrowInfo/list`                | 借款信息列表 |
|   |                                    | `GET /admin/core/borrowInfo/show/{id}`           | 借款信息详情 |
|   |                                    | `POST /admin/core/borrowInfo/approval`           | 借款审批（通过则生成标的） |
| 6 | **AdminLendController**            | `GET /admin/core/lend/list`                      | 标的列表 |
|   |                                    | `GET /admin/core/lend/show/{id}`                 | 标的详情 |
|   |                                    | `GET /admin/core/lend/makeLoan/{id}`             | 放款 |
| 7 | **AdminLendItemController**        | `GET /admin/core/lendItem/list/{lendId}`         | 某标的的投资记录列表 |
| 8 | **AdminLendReturnController**       | `GET /admin/core/lendReturn/list/{lendId}`       | 某标的的还款记录列表 |

**Controller 路径前缀**：`com.wwfinance.controller.admin.*`

> **特别说明**：`IntegralGradeController` 用 `@RequestMapping` 而非具体 HTTP 方法，意味着 GET/POST 均可访问该路径。这是个值得注意的"历史写法"。

---

## 七、数据模型与 DTO

### 7.1 DTO 总览

`finance-api/src/main/java/com/wwfinance/entity/dto/`

| DTO | 用途 | 主要字段 |
|-----|------|---------|
| **UserDTO** | 注册入参 | `userType`, `mobile`, `code`, `password`, `passwordto` |
| **BorrowerDTO** | 借款人认证入参 | `sex`, `age`, `education`, `marry`, `industry`, `income`, `returnSource`, `contactsName`, `contactsMobile`, `contactsRelation`, `List<BorrowerAttach>` |
| **BorrowerDetailDTO** | 借款人详情出参 | `userId`, `name`, `idCard`, `mobile`, `sex`, `age`, `education`, ..., `status`, `createTime`, `List<BorrowerAttachDTO>` |
| **BorrowerAttachDTO** | 借款人附件 | `imageType`（idCard1/idCard2/house/car）, `imageUrl` |
| **BorrowerApprovalDTO** | 借款人审批入参 | `borrowerId`, `status`, `isIdCardOk`, `isHouseOk`, `isCarOk`, `infoIntegral` |
| **BorrowInfoApprovalDTO** | 借款审批入参 | `id`, `status`, `content`, `title`, `lendYearRate`, `serviceRate`, `lendStartDate`, `lendInfo` |
| **InvestDTO** | 投资入参 | `lendId`, `investAmount`, `investUserId`, `investName` |
| **UserBindDTO** | 账户绑定入参 | `idCard`, `name`, `bankType`, `bankNo`, `mobile` |
| **UserIndexDTO** | 个人首页出参 | `userId`, `name`, `nickName`, `userType`, `headImg`, `bindStatus`, `amount`, `freezeAmount`, `lastLoginTime` |
| **AdminUserQuery** | 后台用户分页查询 | `mobile`, `status`, `userType` |
| **TransFlowDTO** | 交易流水 | `agentBillNo`, `bindCode`, `amount`, `transTypeEnum`, `memo` |
| **ExcelDictDTO** | 字典 Excel 导入 | `id`, `parentId`, `name`, `value`, `dictCode` |
| **SmsDTO**（base 模块） | 短信消息 | `mobile`, `message` |

### 7.2 核心实体表（15 张）

| 表名 | 用途 |
|------|------|
| `user` | 用户 |
| `user_account` | 用户资金账户 |
| `user_bind` | 用户银行卡绑定 |
| `user_login_record` | 登录日志 |
| `user_integral` | 用户积分 |
| `borrower` | 借款人认证 |
| `borrower_attach` | 借款人附件 |
| `borrow_info` | 借款申请 |
| `integral_grade` | 积分等级规则 |
| `lend` | 标的 |
| `lend_item` | 投资记录 |
| `lend_return` | 还款计划 |
| `lend_item_return` | 投资人回款计划 |
| `trans_flow` | 交易流水 |
| `dict` | 数据字典 |
| `investment_list` | 投资榜单 |

> 部分接口**直接使用实体类**作入参/出参（如 `User`/`BorrowInfo`/`IntegralGrade`），开发时按 Controller 方法签名实际类型为准。

---

## 八、学生开发指引

### 8.1 接口开发顺序建议（由易到难）

| 阶段 | 任务 | 推荐接口 |
|------|------|----------|
| **阶段 1：环境熟悉（1-2 天）** | 启动 `service-core` + `service-gateway`，跑通 hello 接口 | `GET /api/core/user/hello`、`GET /api/oss/file/hello` |
| **阶段 2：基础读写（2-3 天）** | 字典、登录、注册、发送验证码 | `DictController`、`UserController`、`ApiSmsController` |
| **阶段 3：业务主体（5-7 天）** | 借款人认证、借款申请 | `BorrowerController`、`BorrowInfoController` |
| **阶段 4：核心域（5-7 天）** | 标的、投资、还款 | `LendController`、`LendItemController`、`LendReturnController` |
| **阶段 5：扩展（3-5 天）** | 后台管理、流水、文件 | `AdminXxxController`、`TransFlowController`、`FileController` |

### 8.2 关键技术要点

#### 8.2.1 JWT 鉴权

- 工具类：`finance-api/src/main/java/com/wwfinance/utils/TokenUtil.java`
- 算法：`HS512`，密钥 `vsofo-5grcs-secret`，TTL 7 天
- Payload 关键 claim：`token_phone`（登录用户手机号）

```java
// 生成
String token = TokenUtil.createToken(user.getUserId());
// 解析
String mobile = TokenUtil.parseToken(authorizationHeader);
```

#### 8.2.2 统一返回

所有 Controller 方法统一返回 `PccAjaxResult`：

```java
return new new PccAjaxResult(500, "用户不存在");
return new PccAjaxResult(200, "成功", object);
return PccAjaxResult.success().setData(obj);
return PccAjaxResult.error().setMsg("参数错误").setCode(301);
return PccAjaxResult.ok();  // 无数据成功
```

#### 8.2.3 MyBatis-Plus

- `MybatisPlusConfig` 已配置分页插件
- 实体继承 `BaseEntity`（含 `id`/`createTime`/`updateTime`/`isDeleted` 逻辑删除字段）
- Service 接口继承 `IService<T>`，实现继承 `ServiceImpl<M, T>`
- 自动填充：留意 `@TableField(fill = ...)` 注解，配合 `MetaObjectHandler`

#### 8.2.4 汇付宝三方支付

- `xxBank.RequestHelper.buildRequestSign(Map)`：构造签名
- `xxBank.FormHelper.buildFormHtml(String url, Map<String,Object> params)`：生成自动提交 HTML
- `xxBank.HfbConst`：常量（商户号、密钥、接口地址）

#### 8.2.5 Feign 跨服务调用

```java
@FeignClient(value = "service-sms", fallback = SMSApiSmsClientFallback.class)
public interface SMSApiSmsClient {
    @GetMapping("/api/sms/user/sendMsg/{mobile}")
    String sendMsg(@PathVariable String mobile);
}
```

### 8.3 调试与测试建议

1. **单元测试**：使用 `MockMvc` + `@WebMvcTest`，覆盖 Controller 鉴权、入参校验、返回码。
2. **集成测试**：使用 `knife4j`（http://localhost:8110/doc.html）可视化调试：
   - 自动生成 Swagger UI，所有 Controller 暴露接口均可在线调用。
3. **手动冒烟**：完成注册 → 登录 → 借款人认证 → 借款申请 → 后台审批 → 投资 → 还款 全链路。
4. **Mock 三方支付**：因汇付宝涉及真实资金，测试时用 mock 替换 `RequestHelper`/`FormHelper`，或部署沙箱环境。

### 8.4 新增接口规范

1. **路径**：保持 `/api/core/<资源>` 或 `/admin/core/<资源>` 一致。
2. **命名**：动词性 URL（`getXxx` / `save` / `commitXxx` / `approval`）。
3. **鉴权**：需要登录态的接口**路径中加 `/auth/`**，并通过 `@RequestHeader Authorization` 取 token。
4. **返回**：统一 `PccAjaxResult`，错误场景用 `GlobalExceptionHandler`。
5. **Swagger 注解**：每个 Controller/方法加 `@Api`/`@ApiOperation`/`@ApiParam`，便于 Knife4j 展示。
6. **日志**：`@Slf4j`，关键节点（鉴权、签名、异步回调）记录 INFO 日志。

### 8.5 常见异常坑

| 现象 | 原因与解决 |
|------|-----------|
| Feign 调用失败 | 检查 Nacos 是否启动；`@FeignClient` 的 `value` 是否与服务名一致 |
| JWT 校验失败 | 检查 `Authorization` 头前缀是否为 `5grcs `；密钥是否一致 |
| 分页失效 | 确认 `MybatisPlusConfig` 已注册分页拦截器 |
| 异步回调返回 `fail` | 银行重试会很频繁，先写日志再返 `"success"` |
| Knife4j 看不到接口 | 检查 Controller 上是否有 `@Api`，方法是否有 `@ApiOperation` |

---

## 九、附录

### 9.1 Controller 全量索引

| 包 | Controller | 主要功能 |
|----|-----------|---------|
| `controller.api` | `UserController` | 用户与认证 |
| `controller.api` | `UserBindController` | 银行卡绑定 |
| `controller.api` | `UserAccountController` | 账户与充值 |
| `controller.api` | `BorrowerController` | 借款人认证 |
| `controller.api` | `BorrowInfoController` | 借款申请 |
| `controller.api` | `LendController`（api） | 标的展示与推荐 |
| `controller.api` | `LendItemController` | 投资 |
| `controller.api` | `LendReturnController`（api） | 还款计划 |
| `controller.api` | `TransFlowController` | 交易流水 |
| `controller.api` | `DictController` | 数据字典 |
| `controller.admin` | `AdminUserController` | 后台会员 |
| `controller.admin` | `AdminUserLoginRecordController` | 登录日志 |
| `controller.admin` | `IntegralGradeController` | 积分等级 |
| `controller.admin` | `AdminBorrowerController` | 后台借款人审批 |
| `controller.admin` | `AdminBorrowInfoController` | 后台借款审批 |
| `controller.admin` | `LendController`（admin） | 后台标的与放款 |
| `controller.admin` | `AdminLendItemController` | 投资记录管理 |
| `controller.admin` | `AdminLendReturnController` | 还款记录管理 |
| `finance-oss` | `FileController` | 文件上传 |
| `finance-message` | `ApiSmsController` | 短信服务 |

### 9.2 关键工具类索引

| 工具类 | 路径 | 用途 |
|--------|------|------|
| `TokenUtil` | `finance-api/.../utils/TokenUtil.java` | JWT 生成/解析 |
| `PccAjaxResult` | `finance-common/.../result/PccAjaxResult.java` | 统一返回 |
| `GlobalExceptionHandler` | `finance-common/.../exception/GlobalExceptionHandler.java` | 全局异常 |
| `MD5` | `finance-common/.../utils/MD5.java` | 密码加密 |
| `HttpUtils` | `finance-common/.../utils/HttpUtils.java` | HTTP 客户端 |
| `RequestHelper` | `finance-api/.../xxBank/RequestHelper.java` | 汇付宝签名 |
| `FormHelper` | `finance-api/.../xxBank/FormHelper.java` | 自动提交表单 |
| `HfbConst` | `finance-api/.../xxBank/HfbConst.java` | 汇付宝常量 |

### 9.3 参考配置

| 配置 | 默认值 |
|------|--------|
| MySQL | `jdbc:mysql://172.16.5.213:3306/ww_finance` |
| Redis | `localhost:6379` |
| Nacos | `localhost:8848` |
| Knife4j | `http://localhost:8110/doc.html` |
| 网关入口 | `http://localhost` |
| JWT 密钥 | `vsofo-5grcs-secret` |
| 文件上传 | `D:/uploads/`（单文件 ≤ 50MB） |
| 验证码有效期 | `5 分钟` |
| JWT 有效期 | `7 天` |

### 9.4 文档维护说明

- 文档基于 `ww_finance-master` 当前快照整理。
- 实际开发以代码注释与 `knife4j` 在线文档为准；本文档用于**宏观理解与教学指引**。
- 接口新增/废弃请同步更新本文档相应章节。

---

> 本文档完成。所有接口定义、参数约定、DTO 与开发指引均与源码现状保持一致，建议结合 Knife4j 在线文档进行交叉验证。
