import json

BASE = "{{baseUrl}}"

def make_url(path):
    return {"raw": f"{BASE}{path}", "host": ["{{baseUrl}}"], "path": path.lstrip("/").split("/")}

def make_json_body(raw_str):
    return {"mode": "raw", "raw": raw_str, "options": {"raw": {"language": "json"}}}

collection = {
    "info": {
        "_postman_id": "ww-finance-api-v2",
        "name": "旺旺金融信贷系统 API 测试集",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        "_exporter_id": "whymcgml8963",
        "description": "旺旺金融信贷系统 API 测试用例\n\n# 使用说明\n\n1. 导入后在 Postman/Apifox 中设置 Environment 变量：\n   - baseUrl: http://localhost:8080\n   - token: 留空（执行登录后自动填充）\n\n2. 执行顺序：先执行「用户接口」中的「用户登录」获取token\n3. 登录成功后，将返回的token复制到Environment的token变量中\n4. 所有需要登录的接口会自动携带Authorization: Bearer {{token}}",
        "variable": [
            {"key": "baseUrl", "value": "http://localhost:8080", "type": "string"},
            {"key": "token", "value": "", "type": "string"}
        ]
    },
    "item": []
}

# ============================================================
# 模块1: 用户接口
# ============================================================
user_mod = {"name": "01-用户接口", "item": []}

user_mod["item"].append({
    "name": "用户登录（获取JWT Token）",
    "event": [{"listen": "test", "script": {"exec": ["var jsonData = pm.response.json();\nif(jsonData.code === 200) {\n    var token = jsonData.data;\n    pm.environment.set(\"token\", token);\n    console.log(\"Token saved: \" + token);\n} else {\n    console.log(\"Login failed: \" + jsonData.msg);\n}", "type": "text/javascript"]}}},
    "request": {
        "method": "POST",
        "header": [{"key": "Content-Type", "value": "application/json"}],
        "url": make_url("/api/user/login"),
        "body": make_json_body('{"mobile": "13800001111", "password": "123456"}'),
        "description": "用户登录，成功后在Environment中自动保存token到变量{{token}}"
    }
})

user_mod["item"].append({
    "name": "验证码登录",
    "request": {
        "method": "POST",
        "header": [{"key": "Content-Type", "value": "application/json"}],
        "url": make_url("/api/user/loginByCode"),
        "body": make_json_body('{"mobile": "13800001111", "code": "1234"}'),
        "description": "验证码登录（返回token）"
    }
})

user_mod["item"].append({
    "name": "用户注册",
    "request": {
        "method": "POST",
        "header": [{"key": "Content-Type", "value": "application/json"}],
        "url": make_url("/api/user/register"),
        "body": make_json_body('{"mobile": "13800001222", "password": "123456", "name": "张三", "nickName": "张三"}'),
        "description": "用户注册"
    }
})

user_mod["item"].append({
    "name": "获取当前登录用户信息",
    "request": {
        "method": "GET",
        "header": [{"key": "Authorization", "value": "Bearer {{token}}"}],
        "url": make_url("/api/user/info"),
        "description": "获取当前登录用户信息（需JWT认证）"
    }
})

user_mod["item"].append({
    "name": "修改用户资料",
    "request": {
        "method": "PUT",
        "header": [{"key": "Authorization", "value": "Bearer {{token}}"}, {"key": "Content-Type", "value": "application/json"}],
        "url": make_url("/api/user/profile"),
        "body": make_json_body('{"name": "李四", "email": "lisi@qq.com"}'),
        "description": "修改用户资料（需JWT认证）"
    }
})

user_mod["item"].append({
    "name": "发送验证码",
    "request": {
        "method": "POST",
        "header": [{"key": "Content-Type", "value": "application/json"}],
        "url": make_url("/api/user/sendCode?mobile=13800001222"),
        "description": "发送短信验证码"
    }
})

user_mod["item"].append({
    "name": "用户退出登录",
    "request": {
        "method": "POST",
        "header": [{"key": "Authorization", "value": "Bearer {{token}}"}],
        "url": make_url("/api/user/logout"),
        "description": "退出登录（清除JWT）"
    }
})

user_mod["item"].append({
    "name": "获取用户详情",
    "request": {
        "method": "GET",
        "url": make_url("/api/user/1"),
        "description": "根据ID获取用户信息（公开接口，无需认证）"
    }
})

user_mod["item"].append({
    "name": "首页",
    "request": {
        "method": "GET",
        "url": make_url("/api/user/hello"),
        "description": "首页测试接口（公开）"
    }
})

collection["item"].append(user_mod)

# ============================================================
# 模块2: 借款信息
# ============================================================
bi_mod = {"name": "02-借款信息（需JWT认证）", "item": []}
bi_mod["item"].append({"name": "提交借款申请(正式)", "request": {"method": "POST", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}, {"key": "Content-Type", "value": "application/json"}], "url": make_url("/api/core/borrowInfo/auth/submit"), "body": make_json_body('{"amount": 10000, "period": 12, "returnMethod": 1, "moneyUse": 1, "isDraft": false}'), "description": "提交借款申请（正式提交）"}})
bi_mod["item"].append({"name": "保存借款草稿", "request": {"method": "POST", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}, {"key": "Content-Type", "value": "application/json"}], "url": make_url("/api/core/borrowInfo/auth/submit"), "body": make_json_body('{"amount": 5000, "period": 6, "returnMethod": 2, "moneyUse": 2, "isDraft": true}'), "description": "保存借款申请草稿"}})
bi_mod["item"].append({"name": "查询借款申请列表", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/core/borrowInfo/auth/list"), "description": "查询当前用户的借款申请列表"}})
bi_mod["item"].append({"name": "获取借款状态", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/core/borrowInfo/auth/status"), "description": "获取当前用户最近一次借款申请的状态"}})
bi_mod["item"].append({"name": "删除借款草稿", "request": {"method": "DELETE", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/core/borrowInfo/auth/draft"), "description": "删除当前用户的借款申请草稿"}})
collection["item"].append(bi_mod)

# ============================================================
# 模块3: 标的投资
# ============================================================
lend_mod = {"name": "03-标的投资", "item": []}
lend_mod["item"].append({"name": "获取募资中的标的列表", "request": {"method": "GET", "url": make_url("/api/core/lend/list?pageNum=1&pageSize=10"), "description": "获取募资中的标的列表（公开）"}})
lend_mod["item"].append({"name": "获取标的详情", "request": {"method": "GET", "url": make_url("/api/core/lend/1"), "description": "获取标的详情（公开）"}})
lend_mod["item"].append({"name": "投资人投标", "request": {"method": "POST", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}, {"key": "Content-Type", "value": "application/json"}], "url": make_url("/api/core/lend/invest/1"), "body": make_json_body('{"amount": 1000}'), "description": "投资人投标（需JWT认证，跳转旺旺银行）"}})
lend_mod["item"].append({"name": "获取标的的投资记录", "request": {"method": "GET", "url": make_url("/api/core/lend/1/investments"), "description": "获取标的的投资记录列表（公开）"}})
lend_mod["item"].append({"name": "查询我的投资记录", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/core/lend/myInvestments?pageNum=1&pageSize=10"), "description": "查询我的投资记录（需JWT认证）"}})
collection["item"].append(lend_mod)

# ============================================================
# 模块4: 实名认证
# ============================================================
bor_mod = {"name": "04-实名认证（需JWT认证）", "item": []}
bor_mod["item"].append({"name": "提交实名认证", "request": {"method": "POST", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}, {"key": "Content-Type", "value": "application/json"}], "url": make_url("/api/core/borrower/auth/submit"), "body": make_json_body('{"name": "张三", "idCard": "110101199001011234", "idCardFrontUrl": "http://oss.com/front.jpg", "idCardBackUrl": "http://oss.com/back.jpg", "sex": 1, "age": 30, "education": 1}'), "description": "提交实名认证资料"}})
bor_mod["item"].append({"name": "查询实名认证状态", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/core/borrower/auth/status"), "description": "查询当前用户认证状态"}})
collection["item"].append(bor_mod)

# ============================================================
# 模块5: 交易流水
# ============================================================
tf_mod = {"name": "05-交易流水（需JWT认证）", "item": []}
tf_mod["item"].append({"name": "交易流水列表", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/core/transFlow/auth/list/1/10"), "description": "分页查询当前用户的交易流水"}})
collection["item"].append(tf_mod)

# ============================================================
# 模块6: 银行绑定
# ============================================================
bb_mod = {"name": "06-银行绑定（需JWT认证）", "item": []}
bb_mod["item"].append({"name": "发起绑卡", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/user/bind?code=123456"), "description": "发起绑卡（需先实名认证+短信验证码）"}})
bb_mod["item"].append({"name": "查询绑卡状态", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/user/bindStatus"), "description": "查询绑卡状态"}})
collection["item"].append(bb_mod)

# ============================================================
# 模块7: 银行异步回调
# ============================================================
bn_mod = {"name": "07-银行异步回调（无需JWT）", "item": []}
bn_mod["item"].append({"name": "绑卡异步回调", "request": {"method": "POST", "url": make_url("/api/core/userBind/notify"), "body": {"mode": "urlencoded"}, "description": "银行绑卡异步回调（银行服务器调用，无需认证）"}})
bn_mod["item"].append({"name": "充值异步回调", "request": {"method": "POST", "url": make_url("/api/core/userAccount/notify"), "body": {"mode": "urlencoded"}, "description": "银行充值异步回调"}})
bn_mod["item"].append({"name": "提现异步回调", "request": {"method": "POST", "url": make_url("/api/core/userAccount/notifyWithdraw"), "body": {"mode": "urlencoded"}, "description": "银行提现异步回调"}})
bn_mod["item"].append({"name": "投标异步回调", "request": {"method": "POST", "url": make_url("/api/core/lendItem/notify"), "body": {"mode": "urlencoded"}, "description": "银行投标异步回调"}})
bn_mod["item"].append({"name": "放款异步回调", "request": {"method": "POST", "url": make_url("/api/core/lend/notifyMakeLoan"), "body": {"mode": "urlencoded"}, "description": "银行放款异步回调"}})
bn_mod["item"].append({"name": "还款异步回调", "request": {"method": "POST", "url": make_url("/api/core/lendReturn/notifyUrl"), "body": {"mode": "urlencoded"}, "description": "银行还款异步回调"}})
collection["item"].append(bn_mod)

# ============================================================
# 模块8: 积分等级
# ============================================================
ig_mod = {"name": "08-积分等级（需JWT认证）", "item": []}
ig_mod["item"].append({"name": "查询可借额度", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/core/integralGrade/borrowAmount"), "description": "查询当前登录用户可借额度"}})
collection["item"].append(ig_mod)

# ============================================================
# 模块9: 字典
# ============================================================
dc_mod = {"name": "09-字典", "item": []}
dc_mod["item"].append({"name": "查询字典分类列表", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/core/dict/categories"), "description": "查询全部分类列表"}})
dc_mod["item"].append({"name": "按分类编码查选项列表", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/core/dict/listByCode?dictCode=returnMethod"), "description": "按分类编码查选项列表"}})
collection["item"].append(dc_mod)

# ============================================================
# 模块10: 站内消息
# ============================================================
msg_mod = {"name": "10-站内消息（需JWT认证）", "item": []}
msg_mod["item"].append({"name": "分页查询我的消息", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/core/message/myList?pageNum=1&pageSize=10"), "description": "分页查询我的消息"}})
msg_mod["item"].append({"name": "未读消息数", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/core/message/unreadCount"), "description": "获取未读消息数"}})
msg_mod["item"].append({"name": "标记消息已读", "request": {"method": "PUT", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/core/message/read/1"), "description": "标记消息已读"}})
collection["item"].append(msg_mod)

# ============================================================
# 模块11: 用户账户
# ============================================================
ac_mod = {"name": "11-用户账户（需JWT认证）", "item": []}
ac_mod["item"].append({"name": "查询账户信息", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/user/account/auth/getAccount"), "description": "查询账户信息（个人中心）"}})
ac_mod["item"].append({"name": "发起充值", "request": {"method": "POST", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}, {"key": "Content-Type", "value": "application/json"}], "url": make_url("/api/user/account/auth/recharge"), "body": make_json_body('{"amount": 1000.00}'), "description": "发起充值（跳转旺旺银行）"}})
ac_mod["item"].append({"name": "发起提现", "request": {"method": "POST", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}, {"key": "Content-Type", "value": "application/json"}], "url": make_url("/api/user/account/auth/withdraw"), "body": make_json_body('{"amount": 500.00}'), "description": "发起提现（跳转旺旺银行）"}})
collection["item"].append(ac_mod)

# ============================================================
# 模块12: 还款管理
# ============================================================
lr_mod = {"name": "12-还款管理（需JWT认证）", "item": []}
lr_mod["item"].append({"name": "借款人还款计划", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/core/lendReturn/myBorrowList"), "description": "借款人查询我的还款计划"}})
lr_mod["item"].append({"name": "借款人偿还某一期", "request": {"method": "PUT", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/core/lendReturn/repay/1"), "description": "借款人偿还某一期（跳转旺旺银行）"}})
lr_mod["item"].append({"name": "投资人回款计划", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{token}}"}], "url": make_url("/api/core/lendReturn/myInvestList"), "description": "投资人查询我的回款计划"}})
lr_mod["item"].append({"name": "投资人回款明细", "request": {"method": "GET", "url": make_url("/api/core/lendReturn/itemReturns/1"), "description": "查询某一期的投资人回款明细（公开）"}})
collection["item"].append(lr_mod)

# ============================================================
# 模块13: 后台管理（管理员JWT认证）
# ============================================================
admin_mod = {"name": "13-后台管理（管理员JWT认证）", "item": []}

admin_login = {"name": "13.1-后台管理员登录", "item": []}
admin_login["item"].append({"name": "管理员登录", "request": {"method": "POST", "header": [{"key": "Content-Type", "value": "application/json"}], "url": make_url("/admin/core/login"), "body": make_json_body('{"username": "admin", "password": "admin123"}'), "description": "管理员登录，返回管理员JWT token，需手动保存到adminToken环境变量"}})
admin_login["item"].append({"name": "退出登录", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/logout"), "description": "管理员退出登录"}})
admin_login["item"].append({"name": "获取当前管理员信息", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/userInfo"), "description": "获取当前管理员信息"}})
admin_mod["item"].append(admin_login)

admin_member = {"name": "13.2-后台会员管理", "item": []}
admin_member["item"].append({"name": "获取会员分页列表", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/member/page?pageNum=1&pageSize=10"), "description": "获取会员分页列表"}})
admin_member["item"].append({"name": "修改会员状态", "request": {"method": "PUT", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}, {"key": "Content-Type", "value": "application/json"}], "url": make_url("/admin/core/member/1/status"), "body": make_json_body('{"status": 1}'), "description": "启用/禁用会员"}})
admin_member["item"].append({"name": "新增会员", "request": {"method": "POST", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}, {"key": "Content-Type", "value": "application/json"}], "url": make_url("/admin/core/member"), "body": make_json_body('{"phone": "13900001111", "nickname": "李四", "realName": "李四", "idCard": "110101199001011234", "score": 100}'), "description": "管理员手动开户新增会员"}})
admin_member["item"].append({"name": "登录日志分页查询", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/member/loginLog/page?pageNum=1&pageSize=10"), "description": "会员登录日志分页查询"}})
admin_mod["item"].append(admin_member)

admin_borrow = {"name": "13.3-后台借款记录管理", "item": []}
admin_borrow["item"].append({"name": "借款记录分页列表", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/borrow/record/page?pageNum=1&pageSize=10"), "description": "获取借款记录分页列表"}})
admin_borrow["item"].append({"name": "审核借款申请", "request": {"method": "PUT", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}, {"key": "Content-Type", "value": "application/json"}], "url": make_url("/admin/core/borrow/record/1/audit"), "body": make_json_body('{"status": 2}'), "description": "审核借款申请（2=通过, -1=拒绝）"}})
admin_mod["item"].append(admin_borrow)

admin_borrower = {"name": "13.4-后台借款人管理", "item": []}
admin_borrower["item"].append({"name": "借款人分页列表", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/borrower/page?pageNum=1&pageSize=10"), "description": "获取借款人分页列表"}})
admin_borrower["item"].append({"name": "审核借款人实名认证", "request": {"method": "PUT", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}, {"key": "Content-Type", "value": "application/json"}], "url": make_url("/admin/core/borrower/1/audit"), "body": make_json_body('{"status": 2, "score": 85}'), "description": "审核借款人实名认证（2=通过, -1=拒绝）"}})
admin_mod["item"].append(admin_borrower)

admin_loginlog = {"name": "13.5-后台登录日志管理", "item": []}
admin_loginlog["item"].append({"name": "登录日志分页查询", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/loginLog/page?pageNum=1&pageSize=10"), "description": "后台登录日志分页查询"}})
admin_loginlog["item"].append({"name": "登录日志详情", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/loginLog/1"), "description": "获取登录日志详情"}})
admin_mod["item"].append(admin_loginlog)

admin_transflow = {"name": "13.6-后台流水监控", "item": []}
admin_transflow["item"].append({"name": "全平台流水分页查询", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/transFlow/page?pageNum=1&pageSize=10"), "description": "全平台流水分页查询"}})
admin_mod["item"].append(admin_transflow)

admin_dict = {"name": "13.7-后台字典管理", "item": []}
admin_dict["item"].append({"name": "查询字典分类列表", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/dict/categories"), "description": "查询全部分类列表"}})
admin_dict["item"].append({"name": "按编码查选项列表", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/dict/listByCode?dictCode=returnMethod"), "description": "按分类编码查选项列表"}})
admin_dict["item"].append({"name": "新增分类", "request": {"method": "POST", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}, {"key": "Content-Type", "value": "application/json"}], "url": make_url("/admin/core/dict/category"), "body": make_json_body('{"dictCode": "industry", "name": "行业"}'), "description": "新增字典分类"}})
admin_dict["item"].append({"name": "新增选项", "request": {"method": "POST", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}, {"key": "Content-Type", "value": "application/json"}], "url": make_url("/admin/core/dict/option"), "body": make_json_body('{"parentId": 1, "name": "教育", "value": "3"}'), "description": "新增字典选项"}})
admin_dict["item"].append({"name": "更新字典节点", "request": {"method": "PUT", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}, {"key": "Content-Type", "value": "application/json"}], "url": make_url("/admin/core/dict/update"), "body": make_json_body('{"id": 1, "name": "教育", "value": "3"}'), "description": "更新字典节点"}})
admin_dict["item"].append({"name": "删除字典节点", "request": {"method": "DELETE", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/dict/remove/1"), "description": "删除字典节点"}})
admin_mod["item"].append(admin_dict)

admin_lend = {"name": "13.8-后台标的管理", "item": []}
admin_lend["item"].append({"name": "标的分页列表", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/loan/project/page?pageNum=1&pageSize=10"), "description": "获取标的分页列表"}})
admin_lend["item"].append({"name": "下架标的", "request": {"method": "PUT", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/loan/project/1/offline"), "description": "下架标的"}})
admin_lend["item"].append({"name": "发布标的", "request": {"method": "PUT", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/loan/project/1/publish"), "description": "发布标的（待发布→募资中）"}})
admin_lend["item"].append({"name": "满标放款", "request": {"method": "PUT", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/loan/project/1/makeLoan"), "description": "满标放款（跳转旺旺银行）"}})
admin_lend["item"].append({"name": "查询标的还款计划", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/loan/project/1/returns"), "description": "查询标的还款计划"}})
admin_lend["item"].append({"name": "手动触发逾期扫描", "request": {"method": "POST", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/loan/project/overdueCheck"), "description": "手动触发逾期扫描（测试/补单用）"}})
admin_mod["item"].append(admin_lend)

admin_integral = {"name": "13.9-后台积分等级管理", "item": []}
admin_integral["item"].append({"name": "积分等级列表", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/integralGrade/list"), "description": "查询所有积分等级"}})
admin_integral["item"].append({"name": "积分等级分页查询", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/integralGrade/page?pageNum=1&pageSize=10"), "description": "分页查询积分等级"}})
admin_integral["item"].append({"name": "按ID查询积分等级", "request": {"method": "GET", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/integralGrade/1"), "description": "按ID查询积分等级"}})
admin_integral["item"].append({"name": "新增积分等级", "request": {"method": "POST", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}, {"key": "Content-Type", "value": "application/json"}], "url": make_url("/admin/core/integralGrade/save"), "body": make_json_body('{"gradeName": "普通会员", "minScore": 0, "maxScore": 999}'), "description": "新增积分等级"}})
admin_integral["item"].append({"name": "更新积分等级", "request": {"method": "PUT", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}, {"key": "Content-Type", "value": "application/json"}], "url": make_url("/admin/core/integralGrade/update"), "body": make_json_body('{"id": 1, "gradeName": "VIP会员", "minScore": 1000, "maxScore": 9999}'), "description": "更新积分等级"}})
admin_integral["item"].append({"name": "删除积分等级", "request": {"method": "DELETE", "header": [{"key": "Authorization", "value": "Bearer {{adminToken}}"}], "url": make_url("/admin/core/integralGrade/remove/1"), "description": "删除积分等级"}})
admin_mod["item"].append(admin_integral)

collection["item"].append(admin_mod)

output_path = r"D:\桌面\实训\demo\api\旺旺金融信贷系统_API测试用例_Postman_Apifox.json"
with open(output_path, "w", encoding="utf-8") as f:
    json.dump(collection, f, ensure_ascii=False, indent=4)

total = sum(len(m["item"]) for m in collection["item"])
print(f"Done: {len(collection['item'])} modules, {total} APIs")
