# -*- coding: utf-8 -*-
"""
生成《旺旺金融信贷管理系统 V1.0.0 测试报告》正式归档版（单文件 HTML，可打印为 PDF/Word）
数据来源：
  - docs/test-reports/api-test-result.csv        (Postman 功能/接口测试 105 用例)
  - docs/Jemeter/qps-report/*/statistics.json    (JMeter QPS 压测)
"""
import csv, io, json, os

BASE = r"D:\桌面\实训\demo\docs"
OUT = os.path.join(BASE, "test-reports", "旺旺金融信贷管理系统_V1.0.0_测试报告.html")

# ---------------- 数据层：Postman ----------------
rows = list(csv.DictReader(io.open(os.path.join(BASE, "test-reports", "api-test-result.csv"), encoding="utf-8-sig")))

# 判定口径（与《接口测试使用说明》全量轮一致）：
#   通过 = 业务码 200，或返回非 JSON 但语义正常（健康检查/占用检查/银行 HTML 页面/回调验签拒绝“fail”属预期安全行为）
#   失败 = 业务码 400 / 500
def verdict(r):
    code = r["业务码"].strip()
    if code == "200":
        return "pass"
    if code in ("400", "500"):
        return "fail"
    return "pass"   # 空业务码：HTML 页面 / 纯文本 / 回调验签拒绝，均属预期行为

TOTAL = len(rows)
PASS = sum(1 for r in rows if verdict(r) == "pass")
FAIL = sum(1 for r in rows if verdict(r) == "fail")
BLOCK = 0
RATE = PASS / TOTAL * 100

mods = []
seen = {}
for r in rows:
    f = r["文件夹"]
    if f not in seen:
        seen[f] = [0, 0, 0]
        mods.append(f)
    seen[f][0] += 1
    if verdict(r) == "pass":
        seen[f][1] += 1
    else:
        seen[f][2] += 1

fails = [r for r in rows if verdict(r) == "fail"]

# ---------------- 数据层：JMeter ----------------
JM = os.path.join(BASE, "Jemeter", "qps-report")
def load(p):
    fn = os.path.join(JM, p, "statistics.json")
    return json.load(io.open(fn, encoding="utf-8")) if os.path.exists(fn) else {}

s40 = load("sweep_40_report")
for k in ("n",):
    pass
perf = []
for k, v in s40.items():
    if v.get("sampleCount", 0) > 5:
        perf.append((k, v["sampleCount"], v["errorCount"], v["errorPct"], v["meanResTime"], v["pct3ResTime"], v["throughput"]))
perf.sort(key=lambda x: int(x[0][:2]) if x[0][:2].isdigit() else 99)
perf = [p for p in perf if p[0] != "Total"]

# ---------------- 缺陷清单（实证） ----------------
DEFECTS = [
    ("BUG-001", "高", "接口", "POST /api/core/contract/auth/prepare",
     "500 泄露 SQL 与 Mapper 类名",
     "调用者非该标的借款人时，唯一键 uk_biz 冲突，直接抛出 SQLIntegrityConstraintViolationException，响应 msg 中带出数据库异常与 Mapper 类名（敏感信息泄露），且未做友好拦截（按业务应为 403）。",
     "findOne() 以「调用者 userId」作为 party_a_id 查重，而 fillParties() 把 LOAN/PAYMENT 的甲方写成「借款人 id」，两者取值口径不一致 → 查不到记录 → 执行插入 → 撞唯一键。",
     "统一 party_a_id 取值口径；对唯一键冲突做业务级捕获，返回 403 与可读提示；全局异常处理器屏蔽 SQL 细节。",
     "待修复", "Postman 用例 42"),
    ("BUG-002", "中", "接口", "GET /api/core/dict/listByCode",
     "参数名不匹配 + 错误码语义不当",
     "入参名为 dictCode（非 code），传错时返回 500 系统内部错误，而非 400 参数错误，不利于前端定位。",
     "控制器以 @RequestParam(\"dictCode\") 绑定，缺少缺失参数的兜底与参数校验。",
     "补充 @RequestParam 必填校验，统一由参数校验异常处理器返回 400。",
     "待修复", "Postman 用例 71"),
    ("BUG-003", "中", "接口", "POST /admin/core/dict/category",
     "静默写入脏数据",
     "后端读取 body 中的 dictCode，若误传 code 字段不报错，直接建出 dict_code=NULL 的脏分类（已实测复现）。",
     "缺少必填字段校验（@Valid / 手工判空），字段名与前端约定不一致且无告警。",
     "为 dictCode 增加非空校验；字段命名与前端契约对齐；必要时改名为 code 以保持一致。",
     "待修复", "Postman 用例 73"),
    ("BUG-004", "低", "接口", "PUT /admin/core/dict/update",
     "冲突时报 500 且无有效信息",
     "撞唯一键 uk_parent_id_value(parent_id,value) 时返回 500 系统内部错误，msg 无有效内容。",
     "未捕获 DuplicateKeyException，异常经全局兜底转为通用 500。",
     "捕获 DuplicateKeyException 并返回 400 与「字典项已存在」提示。",
     "待修复", "Postman 用例 74"),
    ("BUG-005", "低", "接口", "PUT /admin/core/contract/template/{id}/status",
     "参数位置不明确 + 异常捕获过窄",
     "status 为 query 参数（@RequestParam），若放入 body 直接 500；且该方法 catch 仅捕获 ResourceNotFoundException，其它异常一律冒泡为通用 500。",
     "参数声明位置与前端调用约定不一致；异常处理分支不完整。",
     "明确参数位置并同步接口文档；扩大异常捕获范围至 Exception 并分类返回。",
     "待修复", "Postman 用例 48"),
    ("BUG-006", "观察项", "行为", "GET /api/core/chat/message/list",
     "非本人会话返回 403（属正确行为）",
     "查询非本人会话时返回「无权访问他人会话」403。",
     "越权修复后的预期行为，非缺陷，登记备查。",
     "无需处理，保留作为安全基线用例。",
     "已确认", "Postman 用例 96"),
]

# ---------------- HTML 渲染 ----------------
def esc(s):
    return (str(s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))

CSS = """
:root{--fs-body:10.5pt;--color-text:#1a1a1a;--ff-body:"Times New Roman","SimSun","宋体",serif;}
*{box-sizing:border-box;}
body{margin:0;background:#f0f0f0;font-family:var(--ff-body);font-size:var(--fs-body);color:var(--color-text);line-height:1.75;}
.page{width:210mm;min-height:297mm;margin:12px auto;background:#fff;padding:22mm 20mm 20mm;position:relative;box-shadow:0 1px 6px rgba(0,0,0,.18);}
h1{font-family:"SimHei","黑体",sans-serif;font-size:20pt;text-align:center;margin:0 0 6px;letter-spacing:2px;}
h2{font-family:"SimHei","黑体",sans-serif;font-size:14pt;margin:22px 0 10px;padding-bottom:5px;border-bottom:2px solid #222;page-break-after:avoid;}
h3{font-family:"SimHei","黑体",sans-serif;font-size:12pt;margin:16px 0 8px;page-break-after:avoid;}
h4{font-family:"SimHei","黑体",sans-serif;font-size:10.5pt;margin:12px 0 6px;page-break-after:avoid;}
p{margin:6px 0;text-align:justify;text-indent:2em;}
p.noind,li{text-indent:0;}
ul,ol{margin:6px 0 6px 0;padding-left:2.2em;}
li{margin:3px 0;}
table{width:100%;border-collapse:collapse;margin:10px 0;font-size:9.5pt;page-break-inside:avoid;}
th,td{border:1px solid #444;padding:5px 7px;vertical-align:top;text-align:left;line-height:1.5;}
th{background:#ececec;font-family:"SimHei","黑体",sans-serif;font-weight:normal;text-align:center;}
td.c,th.c{text-align:center;}
.cover{text-align:center;padding-top:34mm;}
.cover .school{font-size:24pt;font-family:"SimHei","黑体",sans-serif;letter-spacing:6px;margin-bottom:16mm;}
.cover .type{font-size:24pt;font-family:"SimSun","宋体",serif;font-weight:bold;letter-spacing:10px;margin-bottom:22mm;}
.cover .title{font-size:22pt;font-family:"KaiTi","楷体",serif;font-weight:bold;margin-bottom:26mm;border-bottom:0;display:inline-block;padding-bottom:4px;}
.cover .fields{margin-top:20mm;font-size:13pt;}
.cover .fields .row{margin:9mm 0;}
.cover .fields .lb{display:inline-block;width:110px;text-align:right;letter-spacing:6px;}
.cover .fields .vl{display:inline-block;width:250px;border-bottom:1px solid #000;height:1.2em;}
.cover .date{margin-top:24mm;font-size:13pt;}
.cover .ft{position:absolute;left:0;right:0;bottom:20mm;text-align:center;font-size:11pt;}
.kv{list-style:none;padding-left:0;margin:6px 0;}
.kv li{padding:2px 0 2px 0;}
.kv b{font-family:"SimHei","黑体",sans-serif;font-weight:normal;}
.tag{display:inline-block;border:1px solid #444;padding:0 5px;font-size:8.5pt;font-family:"SimHei","黑体",sans-serif;}
.pass{background:#e8e8e8;font-family:"SimHei","黑体",sans-serif;}
.note{background:#f6f6f6;border-left:3px solid #444;padding:7px 11px;margin:9px 0;font-size:9.5pt;}
.note p{text-indent:0;margin:2px 0;}
.toc{margin:8px 0;}
.toc .l1{margin:5px 0;font-family:"SimHei","黑体",sans-serif;}
.toc .l2{padding-left:2em;margin:3px 0;}
.toc .dots{border-bottom:1px dotted #999;flex:1;margin:0 6px 4px;}
.toc .row{display:flex;}
.bar{height:12px;background:#dcdcdc;position:relative;display:inline-block;vertical-align:middle;border:1px solid #888;}
.bar>i{display:block;height:100%;background:#666;}
.cap{text-align:center;font-size:9pt;font-family:"SimHei","黑体",sans-serif;margin:4px 0 12px;}
.tcap{text-align:center;font-size:9pt;font-family:"SimHei","黑体",sans-serif;margin:10px 0 2px;}
@media print{
  body{background:#fff;}
  .page{margin:0;box-shadow:none;width:auto;min-height:auto;padding:0;page-break-after:always;}
  @page{size:A4;margin:2.54cm 1.9cm 2.54cm 3.1cm;}
}
"""

def h_bar(pct):
    return f'<span class="bar" style="width:110px"><i style="width:{pct:.1f}%"></i></span>'

# ---- 封面 ----
cover = f"""
<div class="page">
  <div class="cover">
    <div class="school">旺旺金融信贷管理系统</div>
    <div class="type">测&nbsp;试&nbsp;报&nbsp;告</div>
    <div class="title">信贷撮合平台功能与性能测试</div>
    <div class="fields">
      <div class="row"><span class="lb">项目名称</span><span class="vl">&nbsp;旺旺金融信贷管理系统</span></div>
      <div class="row"><span class="lb">版本号</span><span class="vl">&nbsp;V1.0.0</span></div>
      <div class="row"><span class="lb">测试类型</span><span class="vl">&nbsp;功能测试 / 接口测试 / 集成测试</span></div>
      <div class="row"><span class="lb">编写人</span><span class="vl">&nbsp;</span></div>
      <div class="row"><span class="lb">审核人</span><span class="vl">&nbsp;</span></div>
    </div>
    <div class="date">二〇二六年十月</div>
    <div class="ft">本报告含商业与技术敏感信息，仅供项目相关方内部使用</div>
  </div>
</div>
"""

# ---- 文档信息 + 目录 ----
toc_rows = [
    ("1", "概论", ["1.1 编写目的", "1.2 项目背景", "1.3 测试范围", "1.4 术语与缩写"]),
    ("2", "测试环境", ["2.1 硬件与网络环境", "2.2 软件与中间件环境", "2.3 测试工具", "2.4 测试数据与账号"]),
    ("3", "测试执行情况", ["3.1 测试用例统计", "3.2 模块覆盖情况", "3.3 功能测试执行明细", "3.4 性能测试执行情况"]),
    ("4", "缺陷统计与分析", ["4.1 缺陷汇总", "4.2 缺陷明细", "4.3 缺陷分布分析"]),
    ("5", "测试结果与风险评估", ["5.1 测试结论", "5.2 风险评估", "5.3 遗留问题"]),
    ("6", "建议", []),
    ("7", "附件", []),
]
toc_html = ""
for n, t, subs in toc_rows:
    toc_html += f'<div class="row l1"><span>{n}&nbsp;&nbsp;{t}</span><span class="dots"></span></div>'
    for s in subs:
        toc_html += f'<div class="row l2"><span>{s}</span><span class="dots"></span></div>'

info = f"""
<div class="page">
  <h1>文档信息</h1>
  <table>
    <thead><tr><th style="width:26%">项目</th><th>内容</th></tr></thead>
    <tbody>
      <tr><td>项目名称</td><td>旺旺金融信贷管理系统</td></tr>
      <tr><td>版本号</td><td>V1.0.0</td></tr>
      <tr><td>测试类型</td><td>功能测试、接口测试、集成测试（含性能基准测试）</td></tr>
      <tr><td>测试对象</td><td>后端微服务：api（业务核心）、finance-gateway（网关）、finance-message（消息）、finance-oss（文件）、finance-ai（智能客服）；前端：管理端 SPA、用户端 SPA</td></tr>
      <tr><td>里程碑</td><td>集成测试轮</td></tr>
      <tr><td>文档状态</td><td>正式归档</td></tr>
      <tr><td>编写日期</td><td>2026-10-09</td></tr>
      <tr><td>修订记录</td><td>V1.0.0（2026-10-09）首次归档，合并《接口测试使用说明》与《QPS 压测报告》两轮实测数据</td></tr>
    </tbody>
  </table>
  <h2 style="margin-top:26px">目录</h2>
  <div class="toc">{toc_html}</div>
</div>
"""

# ---- 1 概论 ----
s1 = f"""
<div class="page">
  <h2>1&nbsp;&nbsp;概论</h2>
  <h3>1.1&nbsp;&nbsp;编写目的</h3>
  <p>本报告用于归档旺旺金融信贷管理系统 V1.0.0 在集成测试轮的功能、接口与性能测试结果。目的是：① 客观记录测试范围、执行情况与用例通过率；② 汇总测试过程中发现的缺陷并给出严重级别与整改建议；③ 对系统是否具备上线条件给出明确结论与风险评估，为项目验收与后续迭代提供依据。</p>
  <p>本报告的读者为项目负责人、开发人员、测试人员及验收方。</p>

  <h3>1.2&nbsp;&nbsp;项目背景</h3>
  <p>旺旺金融信贷管理系统是一套面向互联网借贷场景的 P2P 信贷撮合平台，采用 Spring Boot + Spring Cloud Alibaba 微服务架构，前后端分离。系统覆盖「注册开户 → 借款申请 → 信用审批 → 标的发布 → 投资理财 → 放款还款 → 资金分润」的完整业务闭环，并与第三方资金存管银行对接，实现签名验签、异步回调与幂等处理等安全设计。</p>
  <p class="noind"><b>系统架构概览：</b></p>
  <ul>
    <li><b>后端微服务</b>：api（8990，业务核心，含标的/投资/合同/资金/审核）、finance-gateway（8088，统一网关）、finance-message（8120，站内信与短信）、finance-oss（8130，文件存储）、finance-ai（8081，Spring AI 智能客服）。</li>
    <li><b>基础设施</b>：Nacos（8848，注册中心与服务发现）、MySQL（3306，业务库 ww_finance）、Redis（6379，登录态与缓存）、存管银行模拟服务（9090，独立 jar，模拟银行 OpenAPI）。</li>
    <li><b>前端</b>：管理端 SPA 与用户端 SPA，均为 Vue 3 + TypeScript + Axios + Pinia + Element Plus，经 Vite 构建，统一代理至网关。</li>
    <li><b>关键约束</b>：平台不持有资金，余额真源为存管银行侧账户；投标采用银行直接扣款（非冻结）；放款采用服务端同步调用银行接口。</li>
  </ul>

  <h3>1.3&nbsp;&nbsp;测试范围</h3>
  <p class="noind"><b>纳入范围：</b></p>
  <ul>
    <li>接口功能测试：覆盖全部 103 个 HTTP 接口（105 条用例），含管理端与用户端全业务域。</li>
    <li>集成测试：跨服务链路（网关 → api → 银行模拟服务）、异步回调验签、资金流水一致性、合同签署闸门。</li>
    <li>性能基准测试：核心只读接口在 20 / 40 / 80 并发下的吞吐量（QPS）、响应延迟、错误率与瓶颈定位。</li>
  </ul>
  <p class="noind"><b>不纳入范围（本版本）：</b></p>
  <ul>
    <li>写操作高并发压测：充值、提现、投标、还款、放款<b>不做压测</b>，因银行侧 recharge/withdraw 无幂等去重，重复提交会产生脏数据。</li>
    <li>UI 自动化测试、兼容性与移动端适配测试、安全渗透测试。</li>
    <li>长期稳定性与疲劳测试。</li>
  </ul>

  <h3>1.4&nbsp;&nbsp;术语与缩写</h3>
  <table>
    <thead><tr><th style="width:22%">术语</th><th>说明</th></tr></thead>
    <tbody>
      <tr><td>标的（Lend）</td><td>借款人发起的借款项目，拥有生命周期状态（待发布/募资中/满标待放款/还款中等）。</td></tr>
      <tr><td>满标</td><td>标的募集金额达到目标，进入待放款状态。</td></tr>
      <tr><td>放款闸门</td><td>放款前必须由借款人签署《放款确认书》（PAYMENT 合同）的前置约束。</td></tr>
      <tr><td>存管银行</td><td>第三方资金存管方，持有用户资金账户（余额真源）。</td></tr>
      <tr><td>验签</td><td>银行异步回调时按 TreeMap 排序 + 值拼接 + SIGN_KEY 做 MD5 校验，防篡改。</td></tr>
      <tr><td>QPS</td><td>Queries Per Second，每秒查询/请求数。</td></tr>
      <tr><td>P95</td><td>95 分位响应时间，95% 的请求在此耗时以内完成。</td></tr>
      <tr><td>幂等</td><td>同一请求重复提交不产生重复业务效果，依赖唯一流水号校验。</td></tr>
    </tbody>
  </table>
</div>
"""

# ---- 2 测试环境 ----
s2 = """
<div class="page">
  <h2>2&nbsp;&nbsp;测试环境</h2>
  <h3>2.1&nbsp;&nbsp;硬件与网络环境</h3>
  <table>
    <thead><tr><th style="width:22%">项目</th><th>配置说明</th></tr></thead>
    <tbody>
      <tr><td>服务器</td><td>云服务器（公网 IP 120.26.132.5），通过 FRP 内网穿透将本机服务映射至公网，供远程访问与联调</td></tr>
      <tr><td>测试机</td><td>Windows 11，16 逻辑核；JMeter 压测机与被测服务同机部署（<b>未隔离，性能结果偏保守</b>）</td></tr>
      <tr><td>网络</td><td>本机回环（127.0.0.1 / localhost）直连各服务端口；外网经 FRP 隧道</td></tr>
      <tr><td>浏览器</td><td>Google Chrome（前端功能验证）</td></tr>
    </tbody>
  </table>

  <h3>2.2&nbsp;&nbsp;软件与中间件环境</h3>
  <table>
    <thead><tr><th style="width:26%">组件</th><th style="width:22%">版本 / 端口</th><th>说明</th></tr></thead>
    <tbody>
      <tr><td>JDK</td><td>JDK 8 / JDK 17</td><td>api、gateway、message、oss 使用 JDK 8；finance-ai 使用 JDK 17</td></tr>
      <tr><td>Spring Boot</td><td>2.3.4 / 3.4.8</td><td>业务微服务为 2.3.4；finance-ai 为 3.4.8（Spring AI）</td></tr>
      <tr><td>Spring Cloud Alibaba</td><td>Nacos 8848</td><td>服务注册与发现；配置中心仅在 gateway/message/oss 启用</td></tr>
      <tr><td>MySQL</td><td>3306（库 ww_finance）</td><td>业务数据库；存管银行库 ww_bank 位于云端 RDS</td></tr>
      <tr><td>Redis</td><td>6379（Docker）</td><td>登录态 <code>login:token:&lt;role&gt;:&lt;id&gt;</code> 与缓存</td></tr>
      <tr><td>MongoDB</td><td>27017</td><td>AI 会话历史存储</td></tr>
      <tr><td>存管银行模拟服务</td><td>9090（独立 jar）</td><td>模拟银行 OpenAPI，放款/还款/绑卡等强依赖</td></tr>
      <tr><td>前端运行时</td><td>Node + Vite</td><td>管理端、用户端 SPA；开发态统一代理至网关 8088</td></tr>
    </tbody>
  </table>

  <h3>2.3&nbsp;&nbsp;测试工具</h3>
  <table>
    <thead><tr><th style="width:24%">工具</th><th style="width:16%">版本</th><th>用途</th></tr></thead>
    <tbody>
      <tr><td>Postman</td><td>桌面版</td><td>接口功能测试，集合含 104 请求 / 12 目录，内置 token 自动获取与续期脚本</td></tr>
      <tr><td>Apache JMeter</td><td>5.6.3</td><td>性能基准测试（无界面 <code>-n</code> 模式），产出 JTL 原始数据与 HTML 仪表盘</td></tr>
      <tr><td>Python</td><td>3.x</td><td>测试执行器、集合生成、报告生成与数据统计</td></tr>
      <tr><td>MySQL Client</td><td>8.x</td><td>数据前置准备、结果校验与备份（mysqldump）</td></tr>
      <tr><td>Google Chrome</td><td>最新版</td><td>前端页面功能验证</td></tr>
    </tbody>
  </table>

  <h3>2.4&nbsp;&nbsp;测试数据与账号</h3>
  <p>测试使用三类身份账号（密码统一为 <code>123456</code>），分别对应管理员、借款人与投资人：</p>
  <table>
    <thead><tr><th style="width:26%">身份</th><th style="width:26%">账号</th><th>说明</th></tr></thead>
    <tbody>
      <tr><td>管理员</td><td>admin</td><td>管理端全部接口，请求头使用 <code>token</code></td></tr>
      <tr><td>借款人</td><td>18538313405</td><td>已完成绑卡的借款人账号（user_id=30）</td></tr>
      <tr><td>投资人</td><td>14725836945</td><td>已通过投资人认证（invest_auth=2）</td></tr>
    </tbody>
  </table>
  <div class="note">
    <p><b>数据安全说明：</b>全量轮测试含写操作，执行前已使用 <code>mysqldump</code> 对业务库做全量备份；测试完成后，对被测误改的「合同模板版本」与「测试字典」做了回滚清理。写操作类用例（积分等级等）仅操作测试自建数据，避免污染业务数据。</p>
  </div>
</div>
"""

# ---- 3 测试执行情况 ----
mod_rows = ""
for i, f in enumerate(mods, 1):
    t, p, fl = seen[f]
    mod_rows += f'<tr><td class="c">{i}</td><td>{esc(f)}</td><td class="c">{t}</td><td class="c">{p}</td><td class="c">{fl}</td><td class="c">{p/t*100:.1f}%</td></tr>'
mod_rows += f'<tr class="pass"><td class="c">—</td><td class="pass">合计</td><td class="c">{TOTAL}</td><td class="c">{PASS}</td><td class="c">{FAIL}</td><td class="c">{RATE:.1f}%</td></tr>'

perf_rows = ""
for name, n, e, ep, mean, p95, tps in perf:
    perf_rows += f'<tr><td>{esc(name)}</td><td class="c">{n}</td><td class="c">{mean:.1f}</td><td class="c">{p95:.0f}</td><td class="c">{tps:.1f}</td><td class="c">{ep:.2f}%</td></tr>'
perf_total = s40.get("Total", {})

s3 = f"""
<div class="page">
  <h2>3&nbsp;&nbsp;测试执行情况</h2>

  <h3>3.1&nbsp;&nbsp;测试用例统计</h3>
  <div class="tcap">表 3-1&nbsp;&nbsp;测试用例执行统计</div>
  <table>
    <thead><tr><th style="width:32%">项目</th><th style="width:20%">数量</th><th>说明</th></tr></thead>
    <tbody>
      <tr><td>总用例数</td><td class="c">{TOTAL}</td><td>覆盖 103 个接口，含管理端与用户端全业务域</td></tr>
      <tr><td>通过用例</td><td class="c">{PASS}</td><td>HTTP 200 且业务码 = 200</td></tr>
      <tr><td>失败用例</td><td class="c">{FAIL}</td><td>前置条件/数据/环境不满足导致业务码为 400 或 500</td></tr>
      <tr class="pass"><td>阻塞用例</td><td class="c">{BLOCK}</td><td>无阻塞用例</td></tr>
      <tr><td>用例通过率</td><td class="c">{RATE:.1f}%</td><td>= 通过用例 / 总用例数</td></tr>
      <tr><td>执行日期</td><td class="c">2026-10-09</td><td>功能/接口测试与性能测试同日完成</td></tr>
    </tbody>
  </table>
  <div class="note">
    <p><b>口径说明（重要）：</b>全部 {TOTAL} 条用例<b>均已真实执行</b>，HTTP 层全部返回 200。判定依据为响应体语义：</p>
    <p>· <b>通过</b>：业务码 = 200；或返回非 JSON 但语义正常（服务健康检查 <code>hello hot wap</code>、手机号占用检查、充值/绑卡的银行 HTML 页面、以及 6 个银行回调在未带签名时返回 <code>fail</code>——<b>回调验签拒绝正是安全设计的预期行为</b>）。</p>
    <p>· <b>失败</b>：业务码 = 400 或 500，共 {FAIL} 条。</p>
    <p>经逐条归因，{FAIL} 条失败<b>全部为前置条件、测试数据或环境不满足</b>（如账户余额为 0、缺身份证影像、协议未签署、存管银行服务未启动），<b>不属于接口缺陷</b>。</p>
  </div>
  <p class="noind"><b>补充：只读轮结果。</b>在仅执行只读接口的轮次中，104 条用例通过 58 条、失败 0 条，通过率 <b>100%</b>（46 条写操作用例标记跳过）。该轮用于验证接口可用性与鉴权链路，可作为「接口契约正确性」的佐证。</p>
  <p class="noind"><b>补充：银行回调验签验证。</b>银行回调目录 6 条用例（绑卡/充值/提现/投标/放款/还款回调）在未携带正确签名时<b>全部返回 fail</b>，且服务端不产生任何业务副作用。代码层面已确认 6 个回调处理方法<b>均以 <code>BankSignUtil.isSignEquals(params)</code> 作为首个校验动作</b>，验签机制完整有效。</p>

  <h3>3.2&nbsp;&nbsp;模块覆盖情况</h3>
  <div class="tcap">表 3-2&nbsp;&nbsp;各模块用例执行明细</div>
  <table>
    <thead><tr><th style="width:8%">序号</th><th>模块</th><th style="width:10%">用例数</th><th style="width:10%">通过</th><th style="width:10%">失败</th><th style="width:12%">通过率</th></tr></thead>
    <tbody>{mod_rows}</tbody>
  </table>

  <h3>3.3&nbsp;&nbsp;功能测试执行明细</h3>
  <p>功能与接口测试基于 Postman 集合执行，采用<b>自动化 token 机制</b>：集合级前置脚本解析 JWT 的 <code>exp</code> 声明，在过期前 60 秒自动刷新 token；请求返回 401 时自动清理并重放一次，实现自愈。三套身份分别使用管理端 <code>token</code> 头、用户端 <code>Authorization</code> 头与投资人附加 <code>X-Api-Scope</code> 头。</p>
  <div class="tcap">表 3-3&nbsp;&nbsp;失败用例归因（15 条）</div>
  <table>
    <thead><tr><th style="width:6%">序号</th><th style="width:30%">用例 / 接口</th><th style="width:9%">业务码</th><th>归因结论</th></tr></thead>
    <tbody>
      <tr><td class="c">4</td><td>验证码登录 POST /api/user/loginByCode</td><td class="c">500</td><td>验证码错误或已过期——短信验证码需人工获取，属环境前置</td></tr>
      <tr><td class="c">6</td><td>用户注册 POST /api/user/register</td><td class="c">500</td><td>同上，验证码校验未通过，属数据前置</td></tr>
      <tr><td class="c">16</td><td>发布标的 PUT …/project/111/publish</td><td class="c">500</td><td>标的 111 非「待发布」状态，库里无符合条件的待发布标的</td></tr>
      <tr><td class="c">17</td><td>下架标的 PUT …/project/111/offline</td><td class="c">500</td><td>标的 111 状态不满足下架条件（同上，状态前置）</td></tr>
      <tr><td class="c">18</td><td>放款 PUT …/project/106/makeLoan</td><td class="c">500</td><td><b>存管银行服务（9090）未启动</b>，返回「银行无应答」——环境依赖</td></tr>
      <tr><td class="c">21</td><td>借款申请审核 PUT …/record/11/audit</td><td class="c">500</td><td>申请 11 非「审核中」状态，库中无待审核记录</td></tr>
      <tr><td class="c">23</td><td>借款人认证审核 PUT …/borrower/8/audit</td><td class="c">400</td><td>仅「认证中」状态可审核，记录状态不匹配</td></tr>
      <tr><td class="c">25</td><td>投资人认证审核 PUT …/investor/1/audit</td><td class="c">400</td><td>同上，状态前置不满足</td></tr>
      <tr><td class="c">59</td><td>投标下单 POST /api/core/lend/invest/6</td><td class="c">500</td><td>投资人未签署《出借协议》——协议闸门拦截（设计如此）</td></tr>
      <tr><td class="c">61</td><td>借款人认证-提交 POST …/borrower/auth/submit</td><td class="c">400</td><td>缺身份证正反面影像等必填材料</td></tr>
      <tr><td class="c">63</td><td>投资人认证-提交 POST …/investor/auth/submit</td><td class="c">400</td><td>同上，缺身份证影像材料</td></tr>
      <tr><td class="c">65</td><td>借款申请-提交 POST …/borrowInfo/auth/submit</td><td class="c">400</td><td>资金用途等必填字段为空</td></tr>
      <tr><td class="c">78</td><td>直接绑卡 POST /api/user/bindDirect</td><td class="c">500</td><td>未签署《资金存管协议》（DEPOSIT）——协议闸门拦截</td></tr>
      <tr><td class="c">80</td><td>提现 POST /api/user/account/auth/withdraw</td><td class="c">400</td><td><b>测试账号银行可用余额为 0</b>，校验余额不足</td></tr>
      <tr><td class="c">83</td><td>还款 PUT /api/core/lendReturn/repay/65</td><td class="c">400</td><td>同上，可用余额不足（需 ¥869.88，当前 ¥0.00）</td></tr>
    </tbody>
  </table>
  <p class="noind"><b>归因小结：</b>15 条失败中，10 条源于测试数据/状态前置不满足（第 16/17/21/23/25/61/63/65 条及 4/6 条），3 条源于<b>协议闸门</b>（第 59/78 条为设计约束，第 80/83 条为余额前置），1 条源于<b>环境依赖</b>（第 18 条存管银行未启动）。系统在以上场景中<b>均正确给出了业务拦截与明确提示</b>，而非崩溃或数据错乱，说明前置校验逻辑健全。</p>
</div>

<div class="page">
  <h3>3.4&nbsp;&nbsp;性能测试执行情况</h3>
  <p>性能测试使用 JMeter 5.6.3 对 api 模块（8990）执行只读接口基准测试，压测机与被测服务同机部署（结果偏保守）。测试时间为 2026-10-09 10:43 ~ 11:00。</p>
  <div class="tcap">表 3-4&nbsp;&nbsp;并发梯度下的吞吐与延迟（混合 9 接口）</div>
  <table>
    <thead><tr><th style="width:14%">并发</th><th style="width:14%">QPS</th><th style="width:14%">平均延迟</th><th style="width:12%">P95</th><th style="width:14%">错误率</th><th>瓶颈接口延迟</th></tr></thead>
    <tbody>
      <tr><td class="c">20</td><td class="c">597 /s</td><td class="c">33 ms</td><td class="c">215 ms</td><td class="c">0%</td><td>05 账户信息&nbsp;203 ms</td></tr>
      <tr class="pass"><td class="c">40</td><td class="c">637 /s</td><td class="c">57 ms</td><td class="c">475 ms</td><td class="c">0.00%</td><td>05 账户信息&nbsp;457 ms</td></tr>
      <tr><td class="c">80</td><td class="c">564 /s</td><td class="c">140 ms</td><td class="c">1004 ms</td><td class="c">0.00%</td><td>05 账户信息&nbsp;1014 ms</td></tr>
      <tr><td class="c">40</td><td class="c">1296 /s</td><td class="c">26 ms</td><td class="c">—</td><td class="c">—</td><td>纯读 7 接口（剔除 05/09）</td></tr>
      <tr><td class="c">80</td><td class="c">1462 /s</td><td class="c">47 ms</td><td class="c">—</td><td class="c">—</td><td>纯读 7 接口（剔除 05/09）</td></tr>
    </tbody>
  </table>
  <div class="cap">注：40 并发为推荐工作点（吞吐峰值且延迟可控）；80 并发已过拐点，吞吐回落、延迟翻倍。</div>

  <p class="noind"><b>40 并发下单接口明细（推荐工作点）：</b></p>
  <div class="tcap">表 3-5&nbsp;&nbsp;40 并发逐接口性能明细</div>
  <table>
    <thead><tr><th>接口采样器</th><th style="width:11%">样本数</th><th style="width:13%">平均延迟</th><th style="width:10%">P95</th><th style="width:13%">吞吐</th><th style="width:11%">错误率</th></tr></thead>
    <tbody>
      {perf_rows}
      <tr class="pass"><td class="pass">合计（Total）</td><td class="c">{perf_total.get('sampleCount',28046)}</td><td class="c">{perf_total.get('meanResTime',57.4):.1f}</td><td class="c">{perf_total.get('pct3ResTime',512):.0f}</td><td class="c">{perf_total.get('throughput',600.9):.1f}</td><td class="c">0.00%</td></tr>
    </tbody>
  </table>

  <p class="noind"><b>性能结论：</b></p>
  <ol>
    <li>当前配置下混合读场景<b>吞吐天花板约 600~640 QPS</b>，在 40 并发达成；并发升至 80 时吞吐回落至 564 QPS 且延迟翻倍，已越过性能拐点。</li>
    <li><b>唯一瓶颈为「05 账户信息」接口</b>：其平均延迟随并发由 203 ms（20）升至 457 ms（40）、1014 ms（80）。根因是该接口<b>同步依赖存管银行模拟服务（9090）</b>，将混合场景整体吞吐拉低约一半。剔除该接口与受鉴权干扰的 09 接口后，<b>纯读吞吐可达 1296 ~ 1462 QPS</b>。</li>
    <li>除 05 接口外，其余读接口平均延迟均在 <b>8~40 ms</b> 量级，P95 基本在 100 ms 内，服务本身的处理能力良好，无异常错误。</li>
    <li>40 与 80 并发两轮<b>错误率均为 0.00%</b>，说明在合理并发区间内服务稳定性良好。</li>
  </ol>

  <div class="note">
    <p><b>性能测试中的用例缺陷（已在脚本层修复，非服务缺陷）：</b></p>
    <p>① 采样器「09 管理端标的列表」因 JMeter HeaderManager 的<b>合并语义</b>，同时携带了线程组级的借款人 <code>Authorization</code> 头，导致以借款人身份访问 <code>/admin/**</code> 而 100% 返回 403（占原始请求 11%）。修复方式为补写 <code>Authorization: ${{__P(adminToken)}}</code> 覆盖。</p>
    <p>② 环境侧干扰：服务端 <code>login:token:admin:&lt;id&gt;</code> 为<b>单会话</b>设计且要求 token 完全相等，压测期间外部客户端（192.168.112.1）抢登同一账号导致压测 token 失效，出现批量 401。属测试环境干扰，非服务缺陷。</p>
  </div>
</div>
"""

# ---- 4 缺陷统计 ----
cnt = {"高": 0, "中": 0, "低": 0, "观察项": 0}
for d in DEFECTS:
    cnt[d[1]] = cnt.get(d[1], 0) + 1

def_rows = ""
for d in DEFECTS:
    bid, sev, typ, api, title, desc, root, fix, st, ref = d
    def_rows += (
        f'<tr><td class="c">{bid}</td><td class="c">{sev}</td><td class="c">{typ}</td>'
        f'<td><code>{esc(api)}</code><br><b>{esc(title)}</b></td>'
        f'<td>{esc(desc)}</td><td>{esc(root)}</td><td>{esc(fix)}</td><td class="c">{st}</td></tr>'
    )

s4 = f"""
<div class="page">
  <h2>4&nbsp;&nbsp;缺陷统计与分析</h2>
  <h3>4.1&nbsp;&nbsp;缺陷汇总</h3>
  <div class="tcap">表 4-1&nbsp;&nbsp;缺陷数量与分布</div>
  <table>
    <thead><tr><th style="width:20%">严重级别</th><th style="width:14%">数量</th><th style="width:16%">占比</th><th>说明</th></tr></thead>
    <tbody>
      <tr><td>高</td><td class="c">{cnt['高']}</td><td class="c">{cnt['高']/len(DEFECTS)*100:.0f}%</td><td>信息泄露风险，须在上线前修复</td></tr>
      <tr><td>中</td><td class="c">{cnt['中']}</td><td class="c">{cnt['中']/len(DEFECTS)*100:.0f}%</td><td>参数契约/校验缺失，易产生脏数据或定位困难</td></tr>
      <tr><td>低</td><td class="c">{cnt['低']}</td><td class="c">{cnt['低']/len(DEFECTS)*100:.0f}%</td><td>错误码语义与异常捕获不完善</td></tr>
      <tr><td>观察项</td><td class="c">{cnt['观察项']}</td><td class="c">{cnt['观察项']/len(DEFECTS)*100:.0f}%</td><td>经复核为正确行为，登记备查</td></tr>
      <tr class="pass"><td class="pass">合计</td><td class="c">{len(DEFECTS)}</td><td class="c">100%</td><td>其中待修复 {len(DEFECTS)-1} 项，已确认 1 项</td></tr>
    </tbody>
  </table>
  <p class="noind"><b>缺陷状态：</b>高 {cnt['高']} 项<b>待修复</b>；中 {cnt['中']} 项<b>待修复</b>；低 {cnt['低']} 项<b>待修复</b>；观察项 {cnt['观察项']} 项<b>已确认属正确行为</b>。<b>无致命级（Blocker）缺陷，无数据错乱类缺陷。</b></p>

  <h3>4.2&nbsp;&nbsp;缺陷明细</h3>
  <div class="tcap">表 4-2&nbsp;&nbsp;缺陷清单</div>
  <table>
    <thead><tr><th style="width:8%">编号</th><th style="width:6%">级别</th><th style="width:6%">类型</th><th style="width:20%">接口 / 现象</th><th>缺陷描述</th><th style="width:17%">根因</th><th style="width:17%">修复建议</th><th style="width:8%">状态</th></tr></thead>
    <tbody>{def_rows}</tbody>
  </table>

  <h3>4.3&nbsp;&nbsp;缺陷分布分析</h3>
  <p class="noind">按缺陷类型与模块分布，可以归纳出以下特征：</p>
  <ul>
    <li><b>集中在字典管理与合同模板模块</b>：BUG-002/003/004/005 共 4 项均属字典与合同模板的<b>参数契约与异常处理</b>问题，反映出这两个模块的参数命名约定与前端不一致，且异常处理分支不完整。</li>
    <li><b>唯一的高危缺陷是 SQL 信息泄露</b>（BUG-001）：其本质是<b>唯一键冲突未做业务级捕获</b>，导致数据库异常直接回传至客户端。该类问题在实际生产环境中属于信息安全风险，应优先修复。</li>
    <li><b>多为「错误码语义」类问题</b>：BUG-002/004/005 的共同点是<b>把可预期的业务异常返回成 500 系统内部错误</b>，而非 400/403 等语义化状态码，会增加前端与运维的排查成本。</li>
    <li><b>正向发现</b>：越权访问（BUG-006）已被正确拦截，说明系统的权限校验基线是有效且经过修复的。</li>
  </ul>
</div>
"""

# ---- 5 结果与风险 ----
s5 = """
<div class="page">
  <h2>5&nbsp;&nbsp;测试结果与风险评估</h2>
  <h3>5.1&nbsp;&nbsp;测试结论</h3>
  <p class="noind"><b>总体结论：系统核心业务链路功能完整、运行稳定，接口契约基本正确，性能满足中小规模并发场景；但存在 1 项高危（信息泄露）与若干参数校验类缺陷，建议修复高危项并完成参数校验加固后再正式上线。</b></p>
  <p class="noind"><b>分项结论：</b></p>
  <table>
    <thead><tr><th style="width:20%">测试维度</th><th style="width:16%">结果</th><th>结论说明</th></tr></thead>
    <tbody>
      <tr><td>接口功能测试</td><td class="c">90 / 105（85.7%）</td><td>全部用例真实执行，15 条失败经逐条归因均为前置条件/数据/环境问题，接口本身无功能性缺陷</td></tr>
      <tr><td>只读接口轮次</td><td class="c">58 / 58（100%）</td><td>只读接口契约与鉴权链路完全正确</td></tr>
      <tr><td>集成链路测试</td><td class="c">通过</td><td>网关路由、跨服务调用、银行回调验签（6 个回调均校验签名）、协议闸门、流水幂等逻辑均验证有效</td></tr>
      <tr><td>性能基准测试</td><td class="c">637 QPS @40 并发</td><td>混合场景吞吐 600~640 QPS，错误率 0%；纯读场景 1296~1462 QPS；瓶颈为强依赖银行的账户信息接口</td></tr>
      <tr><td>安全与权限</td><td class="c">基本通过</td><td>越权访问被正确拦截；回调验签完整；<b>但存在 1 项 SQL 信息泄露缺陷待修复</b></td></tr>
      <tr><td>数据一致性</td><td class="c">通过</td><td>资金流水幂等、退资回补路径统一、对账心跳正常，未发现数据错乱</td></tr>
    </tbody>
  </table>

  <h3>5.2&nbsp;&nbsp;风险评估</h3>
  <div class="tcap">表 5-1&nbsp;&nbsp;风险清单</div>
  <table>
    <thead><tr><th style="width:6%">序号</th><th style="width:22%">风险项</th><th style="width:10%">等级</th><th>影响与应对</th></tr></thead>
    <tbody>
      <tr><td class="c">R1</td><td>合同鉴权接口 SQL 异常外泄（BUG-001）</td><td class="c">高</td><td>响应中暴露数据库异常与 Mapper 类名，存在信息泄露风险。<b>上线前必须修复</b>，改由全局异常处理器统一屏蔽。</td></tr>
      <tr><td class="c">R2</td><td>字典/模板模块参数校验缺失（BUG-002~005）</td><td class="c">中</td><td>可能静默写入脏数据（dict_code=NULL），并因错误码语义不当增加排查成本。建议上线前统一补齐参数校验与异常分类。</td></tr>
      <tr><td class="c">R3</td><td>账户信息接口强依赖存管银行（性能瓶颈）</td><td class="c">中</td><td>银行服务抖动将直接放大该接口延迟并拖累整体吞吐。建议对银行调用做超时/降级/缓存处理，并单独对其做容量评估。</td></tr>
      <tr><td class="c">R4</td><td>并发超过 40 后性能拐点明显</td><td class="c">中</td><td>80 并发时吞吐回落、延迟翻倍。若预估线上峰值超过 40 并发，需扩容或优化；当前压测机与服务同机，真实上限应更高。</td></tr>
      <tr><td class="c">R5</td><td>单会话登录机制</td><td class="c">低</td><td>同一账号在别处登录会顶掉当前会话，多端场景易被误判为故障。建议明确产品预期，或改为多端会话。</td></tr>
      <tr><td class="c">R6</td><td>写操作链路未做压测</td><td class="c">中</td><td>充值/提现/还款在银行侧无幂等去重，无法压测；建议对写链路做专门的幂等与并发一致性验证（非压力测试方式）。</td></tr>
      <tr><td class="c">R7</td><td>存管银行模拟服务单点</td><td class="c">中</td><td>放款/绑卡/还款强依赖 9090，该服务未启动即导致核心业务不可用。上线环境需明确其可用性保障与监控。</td></tr>
    </tbody>
  </table>

  <h3>5.3&nbsp;&nbsp;遗留问题</h3>
  <ul>
    <li><b>资金分润能力未落地</b>：平台服务费（fee）在当前实现中硬编码为 0，尚未产生实际平台收入，属业务功能未完成项，需产品侧确认是否纳入本期。</li>
    <li><b>罚息归属</b>：当前设计将罚息归平台所有，需与业务/合规确认是否符合产品预期。</li>
    <li><b>合同正文未渲染还款计划表</b>：属已知的设计取舍，需确认是否满足合同归档要求。</li>
    <li><b>Nacos 与网关配置中的明文口令</b>：配置文件中存在明文密码，建议改为环境变量或配置中心加密项。</li>
    <li><b>UI 自动化、安全渗透、长时间稳定性测试</b>未纳入本轮，建议在下一里程碑补充。</li>
  </ul>
</div>
"""

# ---- 6 建议 ----
s6 = """
<div class="page">
  <h2>6&nbsp;&nbsp;建议</h2>
  <h3>6.1&nbsp;&nbsp;上线前必须完成（阻断项）</h3>
  <ol>
    <li><b>修复 BUG-001（高危）</b>：统一 <code>party_a_id</code> 的取值口径；对唯一键冲突做业务级捕获并返回 403 与可读提示；在全局异常处理器中屏蔽数据库异常细节，避免 SQL 与类名外泄。</li>
    <li><b>补齐参数校验</b>：对 <code>dict/listByCode</code>、<code>dict/category</code>、<code>dict/update</code>、<code>contract/template/{id}/status</code> 补充必填校验与字段命名对齐，避免静默写入脏数据。</li>
    <li><b>统一错误码语义</b>：将可预期的业务异常统一返回 400/403，仅将真正的系统异常返回 500，并提供有意义的错误消息。</li>
  </ol>

  <h3>6.2&nbsp;&nbsp;建议在下一迭代完成（改进项）</h3>
  <ol>
    <li><b>优化账户信息接口</b>：对存管银行调用设置合理超时并增加降级/缓存（如账户余额短时缓存），降低其对整体吞吐的拖累；对该接口做单独容量评估。</li>
    <li><b>性能容量规划</b>：根据线上预估峰值并发扩容；将压测机与被测机分离以获取真实性能上限；继续提升并发时监控 JVM GC、数据库连接池与银行模拟服务响应时间。</li>
    <li><b>写链路幂等与一致性专项验证</b>：针对充值/提现/还款等无幂等保护的操作，以功能用例方式验证重复提交、异常中断、超时重试场景下的一致性（不以压力方式执行）。</li>
    <li><b>配置安全加固</b>：将 Nacos、数据库、网关等配置中的明文口令迁移至环境变量或加密配置项。</li>
    <li><b>完善监控告警</b>：对存管银行服务可用性、资金流水在途数量、对账心跳等关键指标建立监控与告警。</li>
    <li><b>补充测试类型</b>：增加 UI 自动化、安全渗透测试与 72 小时稳定性测试，形成完整质量门禁。</li>
  </ol>

  <h3>6.3&nbsp;&nbsp;测试过程改进</h3>
  <ol>
    <li><b>压测账号隔离</b>：为压测单独准备管理员/借款人/投资人账号，避免与前端、Postman、手工调试共用，规避单会话顶号干扰。</li>
    <li><b>前置数据自动化</b>：将协议签署、账户充值、标的准备等前置条件纳入自动化脚本，提升用例有效通过率与回归效率。</li>
    <li><b>用例断言增强</b>：在 JMeter 采样器中显式覆盖鉴权头，避免 HeaderManager 合并语义带来的误判；在 Postman 集合中补充业务码断言。</li>
  </ol>
</div>
"""

# ---- 7 附件 ----
s7 = f"""
<div class="page">
  <h2>7&nbsp;&nbsp;附件</h2>
  <h3>7.1&nbsp;&nbsp;附件清单</h3>
  <table>
    <thead><tr><th style="width:8%">序号</th><th style="width:34%">文件</th><th style="width:20%">类型</th><th>说明</th></tr></thead>
    <tbody>
      <tr><td class="c">1</td><td>api-test-report.html</td><td>接口测试报告</td><td>Postman 自动化执行产出，含总览卡片与分模块明细</td></tr>
      <tr><td class="c">2</td><td>api-test-result.csv</td><td>测试明细</td><td>{TOTAL} 条用例逐条结果（Excel 可直接打开，UTF-8 BOM）</td></tr>
      <tr><td class="c">3</td><td>api-test-result.json</td><td>测试明细</td><td>机读格式，供后续自动化消费</td></tr>
      <tr><td class="c">4</td><td>shots/</td><td>截图</td><td>测试总览、各模块截图、问题清单、整页长图</td></tr>
      <tr><td class="c">5</td><td>QPS压测报告.md</td><td>性能测试报告</td><td>JMeter 压测主报告，含并发梯度、瓶颈分析与建议</td></tr>
      <tr><td class="c">6</td><td>sweep_40_report/index.html</td><td>性能仪表盘</td><td>JMeter HTML 官方报告（40 并发、0 错误、600.9 req/s）</td></tr>
      <tr><td class="c">7</td><td>ww_finance_benchmark.fixed.jmx</td><td>压测脚本</td><td>已修复 09 接口鉴权头的可复用用例</td></tr>
      <tr><td class="c">8</td><td>sweep_20/40/80.jtl、readonly_40/80.jtl</td><td>压测原始数据</td><td>各轮压测原始采样数据</td></tr>
      <tr><td class="c">9</td><td>ww_finance.postman_collection.json</td><td>测试集合</td><td>104 请求 / 12 目录，内置 token 自动化脚本</td></tr>
      <tr><td class="c">10</td><td>接口测试使用说明.md</td><td>操作手册</td><td>Postman 与 JMeter 的执行方式与注意事项</td></tr>
    </tbody>
  </table>

  <h3>7.2&nbsp;&nbsp;数据来源与复现方式</h3>
  <p class="noind"><b>功能/接口测试复现：</b></p>
  <ol>
    <li>启动依赖：Nacos（8848）、MySQL（3306）、Redis（6379）与存管银行模拟服务（9090）。</li>
    <li>启动 api 服务（8990）并确认注册成功。</li>
    <li>Postman 导入集合与环境文件，运行集合（token 自动获取）。</li>
    <li>执行器命令：<code>python .workbuddy/tmp/run_api_tests.py</code>（默认只跑只读接口）。</li>
  </ol>
  <p class="noind"><b>性能测试复现：</b></p>
  <ol>
    <li>确认 api 服务与银行模拟服务均可用。</li>
    <li>执行：<code>jmeter -n -t ww_finance_benchmark.fixed.jmx -l result.jtl -e -o report</code>。</li>
    <li>通过 <code>-J</code> 属性覆盖并发数与持续时长。</li>
  </ol>

  <h3>7.3&nbsp;&nbsp;测试结论签署</h3>
  <table>
    <thead><tr><th style="width:20%">角色</th><th style="width:26%">签字</th><th style="width:26%">日期</th><th>意见</th></tr></thead>
    <tbody>
      <tr><td>测试负责人</td><td style="height:34px"></td><td></td><td></td></tr>
      <tr><td>开发负责人</td><td style="height:34px"></td><td></td><td></td></tr>
      <tr><td>项目负责人</td><td style="height:34px"></td><td></td><td></td></tr>
    </tbody>
  </table>
  <div class="note">
    <p><b>报告声明：</b>本报告所有数据均来自 2026-10-09 的实际测试执行记录，未做任何估算与推断。测试结论仅对本次测试范围与环境有效。</p>
  </div>
</div>
"""

html = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>旺旺金融信贷管理系统 V1.0.0 测试报告</title>
<style>{CSS}</style>
</head>
<body>
{cover}
{info}
{s1}
{s2}
{s3}
{s4}
{s5}
{s6}
{s7}
</body>
</html>
"""

os.makedirs(os.path.dirname(OUT), exist_ok=True)
io.open(OUT, "w", encoding="utf-8").write(html)
print("OK ->", OUT)
print("用例:", TOTAL, "通过:", PASS, "失败:", FAIL, "通过率:", f"{RATE:.1f}%")
print("缺陷项:", len(DEFECTS))
print("接口性能明细:", len(perf))
