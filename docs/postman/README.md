# ww_finance Postman 接口测试集合

## 导入

1. Postman → `Import` → 选这两个文件：
   - `ww_finance.postman_collection.json`（集合，55 个请求）
   - `ww_finance.postman_environment.json`（可选，本地环境变量）
2. 导入后在左上角环境下拉里选 `ww_finance 本地环境`（若导入了环境文件）。

## 使用前必做一件事

改密码。集合变量里的 `adminPassword` / `userPassword` 默认填的是 `123456`（猜的），
**必须改成你库里的真实密码**，否则所有请求都会 401。

改法：集合 → `Variables` 标签页 → 改 `adminPassword` / `userPassword`。

其他变量：

| 变量 | 默认 | 说明 |
|---|---|---|
| `baseUrl` | `http://localhost:8990` | api 端口，改过就改这里 |
| `adminUsername` | `admin` | 库里 `sys_admin.username` |
| `userMobile` | `13800000000` | 库里 `user.mobile` |
| `lendId` / `contractId` 等 | `1` | 列表接口会自动回写第一个 id，一般不用手填 |

## token 自动获取怎么实现的

集合级 **Pre-request Script** 会在 `adminToken` / `userToken` 为空时，自动调一次登录接口补上，
所以**直接点任意一个业务请求就能跑**，不用先手动登录。

两个细节：

- 脚本里排除了 `/login`、`/register`、`/sendCode`，否则登录请求自己会触发"取 token→去登录"的死循环。
- 两个端的 token **不能混用**。后端 `JwtAuthInterceptor` 按 role 校验：
  admin 角色访问 `/api/**` 会 403，user 角色访问 `/admin/**` 也会 403。
  所以管理端请求用 `token` 头，用户端用 `Authorization` 头 —— 集合里已分别配好。

## 目录

| 文件夹 | 内容 |
|---|---|
| 0. 鉴权与环境自检 | 登录、注册、验证码、当前用户信息 |
| 1. 管理端-标的管理 | 列表/发布/下架/放款/投资记录/还款计划 |
| 2. 管理端-审核 | 借款申请、借款人、投资人三类审核 |
| 3. 管理端-合同 | 合同列表/详情/作废、模板 |
| 4. 管理端-会员与资金 | 会员、交易流水、积分等级 |
| 5. 用户端-标的与投资 | 标的列表/详情/推荐/我的投资 |
| 6. 用户端-认证与借款申请 | 借款人/投资人认证、借款申请 |
| 7. 用户端-合同签署 | 合同列表/详情/准备/签署 |
| 8. 用户端-资金流水 | 账户、绑卡、充值、提现、还款 |
| 9. 用户端-站内消息 | 消息列表、未读数、标记已读 |

## 常见报错怎么查

| 现象 | 原因 |
|---|---|
| 全部 401「缺少认证token」 | 密码不对 → token 没拿到；或 Redis(6379) 不通（登录态存 Redis） |
| `/api/**` 返回 403 | 用了 admin 的 token 打用户端接口 |
| 充值/提现断言失败 | 这两个接口**成功时返回银行 HTML 表单**不是 JSON。集合级只断言 HTTP 200，看 body 里的 HTML 是否正常 |
| 余额不足 | 提现可用额 = 银行余额 − 该用户 status=0 的提现金额之和 |
| 放款失败 | 《放款确认书》未签（签署关口三） |
| 发布失败 | 《借款合同》未签（签署关口一） |

> 充值/提现/还款依赖银行模拟服务（9090），它没起时这些接口不可用。

## 重新生成

接口有增删时，改 `generate_collection.py` 后重跑：

```bash
python docs/postman/generate_collection.py
```

用脚本生成而不是手写 JSON，是为了让里面的 JS 脚本保持可读 ——
手写 JSON 时每个换行和引号都要转义，改一次就想死。

生成后可顺手跑一遍路径自检（比对集合里的 URL 与 Controller 的 `@RequestMapping`
是否对得上），上面 55 个请求目前是 55/55 命中。
