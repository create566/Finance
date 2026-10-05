#!/usr/bin/env python
"""市场逻辑改造的端到端验证（打隔离实例 8991，不碰 8990）。

覆盖：
  A 余额不足 → 还款被拦      B 余额不足 → 提现被拦       C 投标余额不足被拦
  D 充值发起即落「处理中」流水  E 充值回调 → 同一条流水置成功（不重复插）
  F 提现回调失败 → 置失败 + 站内信  G 流水按类型筛选      H 消息分页/字段
  I 管理端流水监控接口
"""
import base64
import hashlib
import hmac
import json
import socket
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:8991"
SECRET = "vsofo-5grcs-secret"
BANK_SIGN_KEY = "9876543210"  # HfbConst/BankSignUtil 的默认值（BANK_SIGN_KEY 未设时）

OK = "\033[32mPASS\033[0m"
NG = "\033[31mFAIL\033[0m"
results = []


# ---------- infra ----------
def redis(*args):
    payload = f"*{len(args)}\r\n".encode()
    for a in args:
        raw = a.encode()
        payload += f"${len(raw)}\r\n".encode() + raw + b"\r\n"
    with socket.create_connection(("127.0.0.1", 6379), timeout=5) as s:
        s.sendall(payload)
        return s.recv(65536).decode(errors="replace").strip()


def b64(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def make_token(uid, admin=False):
    key = base64.b64decode(SECRET + "=" * (-len(SECRET) % 4))
    claims = {"token_userid": str(uid)}
    if admin:
        claims.update({"token_username": "admin", "token_role": "admin"})
    claims["exp"] = int(time.time()) + 604800
    h = b64(json.dumps({"alg": "HS512"}, separators=(",", ":")).encode())
    p = b64(json.dumps(claims, separators=(",", ":")).encode())
    sig = hmac.new(key, f"{h}.{p}".encode(), hashlib.sha512).digest()
    return f"5grcs {h}.{p}.{b64(sig)}"


def login(uid, admin=False):
    tok = make_token(uid, admin)
    redis("SET", f"login:token:{'admin' if admin else 'user'}:{uid}", tok)
    return tok


def req(method, path, token=None, body=None, raw_text=False):
    url = BASE + path
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization" if not admin_path(path) else "token"] = token
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=20) as resp:
            txt = resp.read().decode()
    except urllib.error.HTTPError as e:
        txt = e.read().decode()
        if raw_text:
            return txt
        try:
            return json.loads(txt)
        except Exception:
            return {"code": e.code, "msg": txt[:200]}
    if raw_text:
        return txt
    try:
        return json.loads(txt)
    except Exception:
        return {"code": -1, "msg": txt[:200]}


def admin_path(path):
    return path.startswith("/admin")


def form_post(path, params):
    """按平台约定给银行回调签名并发起表单 POST。

    签名算法见 BankSignUtil.getSign：TreeMap 按 key 排序后
    逐个 `value + "|"` 拼接（注意每个值后面都带竖线，包括最后一个），
    末尾再拼上 SIGN_KEY，整体取 MD5。
    """
    sign_src = "".join(f"{v}|" for _, v in sorted(params.items()) if v is not None) + BANK_SIGN_KEY
    params = {k: v for k, v in params.items() if v is not None}
    params["sign"] = hashlib.md5(sign_src.encode()).hexdigest()
    data = urllib.parse.urlencode(params).encode()
    r = urllib.request.Request(BASE + path, data=data, method="POST")
    with urllib.request.urlopen(r, timeout=20) as resp:
        return resp.read().decode()


def check(name, cond, detail=""):
    results.append((name, bool(cond), detail))
    print(f"[{OK if cond else NG}] {name}" + (f"  —— {detail}" if detail else ""))
    return bool(cond)


def api_code(res):
    return res.get("code") if isinstance(res, dict) else None


def api_msg(res):
    return (res or {}).get("msg") or (res or {}).get("message") or ""


def main():
    t33 = login(33)   # 借款人，银行余额 -469.52（故意保留的"不够"状态）
    t42 = login(42)   # 投资人，银行余额 -1520.48
    ta = login(1, admin=True)

    print("\n========== A 余额不足 → 还款被拦 ==========")
    res = req("PUT", "/api/core/lendReturn/repay/92", t33, raw_text=False)
    check("A1 还款接口返回 400 业务错误", api_code(res) == 400, f"code={api_code(res)} msg={api_msg(res)}")
    check("A2 错误文案点明余额不足", "余额不足" in api_msg(res), api_msg(res))

    print("\n========== B 余额不足 → 提现被拦 ==========")
    res = req("POST", "/api/user/account/auth/withdraw", t33, {"amount": 1000}, raw_text=False)
    check("B1 提现接口返回 400 业务错误", api_code(res) == 400, f"code={api_code(res)} msg={api_msg(res)}")
    check("B2 错误文案点明余额不足", "余额不足" in api_msg(res), api_msg(res))

    print("\n========== C 投标余额不足被拦 ==========")
    # 投标入口的校验顺序是「标的 → 合同关口 → 余额闸门」，所以必须先找到一份
    # 本人已签署、且金额一致的《出借协议》，否则会被更前面的合同关口拦掉，测不到余额闸门。
    contracts = req("GET", "/api/core/contract/auth/list?contractType=INVEST&status=1&pageNum=1&pageSize=50", t42)
    signed = [c for c in ((contracts.get("data") or {}).get("list") or [])
              if c.get("contractType") == "INVEST" and c.get("status") == 1 and c.get("amount")]
    target = None
    for c in signed:
        lend = req("GET", f"/api/core/lend/{c['bizId']}", t42)
        if (lend.get("data") or {}).get("status") == 1:
            target = (c["bizId"], float(c["amount"]))
            break
    if target:
        lid, amt = target
        res = req("POST", f"/api/core/lend/invest/{lid}", t42, {"amount": amt}, raw_text=False)
        check("C1 投标被余额闸门拦住且文案点明余额不足",
              "余额不足" in api_msg(res),
              f"lendId={lid} amount={amt} code={api_code(res)} msg={api_msg(res)}")
    else:
        check("C1 投标余额闸门", False, "没找到「已签出借协议 + 标的募资中」的组合，未能覆盖（已由 A/B 同源逻辑保证）")

    print("\n========== D 充值发起即落「处理中」流水 ==========")
    html = req("POST", "/api/user/account/auth/recharge", t33, {"amount": 500}, raw_text=True)
    check("D1 返回银行表单 HTML", "<form" in html, html[:80].replace("\n", " "))
    # 从表单里抠出 agentBillNo（回调对号用的业务单号）。
    # 注意 FormHelper 渲染出来的是单引号属性： name='agentBillNo' value='...'
    import re
    agent_bill_no = None
    m = re.search(r"name=['\"]agentBillNo['\"][^>]*?value=['\"]([^'\"]+)['\"]", html)
    if m:
        agent_bill_no = m.group(1)
    check("D2 表单带回 agentBillNo（供回调对号）", bool(agent_bill_no), str(agent_bill_no))

    flows = req("GET", "/api/core/transFlow/auth/list/1/5", t33)
    records = (flows.get("data") or {}).get("records") or []
    pending = [r for r in records if r.get("status") == 0]
    check("D3 流水列表出现「处理中」的充值记录", len(pending) >= 1,
          f"status 分布={[r.get('status') for r in records]}")
    if pending:
        check("D4 处理中流水的单号 = 表单 agentBillNo",
              pending[0].get("transNo") == agent_bill_no,
              f"{pending[0].get('transNo')} vs {agent_bill_no}")
        check("D5 处理中流水备注可读", "待银行处理" in (pending[0].get("memo") or ""),
              pending[0].get("memo"))

    print("\n========== E 充值回调 → 同一条流水置成功 ==========")
    cnt_before = (req("GET", "/api/core/transFlow/auth/list/1/50", t33).get("data") or {}).get("total")
    body = form_post("/api/core/userAccount/notify", {
        "bindCode": "3d4043d7ed6946abb25fc6358b2d49c3",
        "agentBillNo": agent_bill_no or "",
        "chargeAmt": "500",
        "status": "1",
    })
    check("E1 回调应答 success", body.strip() == "success", body[:80])
    flows2 = req("GET", "/api/core/transFlow/auth/list/1/50", t33)
    recs2 = (flows2.get("data") or {}).get("records") or []
    total2 = (flows2.get("data") or {}).get("total")
    target = [r for r in recs2 if r.get("transNo") == agent_bill_no]
    check("E2 该笔流水已置为成功", bool(target) and target[0].get("status") == 1,
          f"status={[r.get('status') for r in target]}")
    check("E3 没有多插一条重复流水（总数未增加）", total2 == cnt_before,
          f"before={cnt_before} after={total2}")

    print("\n========== F 提现失败回调 → 置失败 + 站内信 ==========")
    # 说明：不能再用"发起提现"来造数据 —— 提现现在有余额闸门，
    # 而本次测试账号的银行余额是负数，发起阶段就会被拦住（这恰恰是本次改造的效果）。
    # 所以这里直接模拟银行回一笔带新单号的失败通知，覆盖「找不到处理中流水 → 补插失败流水」这条分支。
    w_no = "WITHDRAWF" + str(int(time.time() * 1000))[-10:]
    body = form_post("/api/core/userAccount/notifyWithdraw", {
        "bindCode": "3d4043d7ed6946abb25fc6358b2d49c3",
        "agentBillNo": w_no,
        "fetchAmt": "0.01",
        "status": "0",  # 银行侧失败
    })
    check("F1 提现失败回调应答 success", body.strip() == "success", body[:80])
    recs3 = (req("GET", "/api/core/transFlow/auth/list/1/50", t33).get("data") or {}).get("records") or []
    tgt = [r for r in recs3 if r.get("transNo") == w_no]
    check("F2 该笔提现流水落库且为失败",
          bool(tgt) and tgt[0].get("status") == 2 and tgt[0].get("transType") == 2,
          f"status={[r.get('status') for r in tgt]} type={[r.get('transType') for r in tgt]}")
    msgs = req("GET", "/api/core/message/myList?pageNum=1&pageSize=10", t33)
    mlist = (msgs.get("data") or {}).get("list") or []
    check("F3 收到【提现失败】站内信",
          any(m.get("messageType") == 26 for m in mlist),
          f"最近消息类型={[m.get('messageType') for m in mlist]}")

    print("\n========== G 流水按类型筛选 ==========")
    only_recharge = req("GET", "/api/core/transFlow/auth/list/1/20?transType=1", t33)
    rows = (only_recharge.get("data") or {}).get("records") or []
    check("G1 transType=1 只返回充值", all(r.get("transType") == 1 for r in rows) and len(rows) > 0,
          f"n={len(rows)} types={sorted({r.get('transType') for r in rows})}")
    only_withdraw = req("GET", "/api/core/transFlow/auth/list/1/20?transType=2", t33)
    rows2 = (only_withdraw.get("data") or {}).get("records") or []
    check("G2 transType=2 只返回提现", all(r.get("transType") == 2 for r in rows2) and len(rows2) > 0,
          f"n={len(rows2)}")

    print("\n========== H 消息分页与字段 ==========")
    m1 = req("GET", "/api/core/message/myList?pageNum=1&pageSize=3", t33)
    l1 = (m1.get("data") or {}).get("list") or []
    check("H1 后端认 pageNum/pageSize", len(l1) <= 3 and len(l1) > 0, f"n={len(l1)}")
    m2 = req("GET", "/api/core/message/myList?page=1&limit=3", t33)
    l2 = (m2.get("data") or {}).get("list") or []
    check("H2 兼容前端旧的 page/limit 参数", len(l2) == len(l1), f"n={len(l2)}")
    check("H3 消息带 readFlag 字段（前端不再读空的 isRead）",
          bool(l2) and "readFlag" in l2[0], f"keys={sorted(l2[0].keys()) if l2 else []}")
    unread = req("GET", "/api/core/message/unreadCount", t33)
    check("H4 未读数接口通", api_code(unread) == 200, f"data={unread.get('data')}")

    print("\n========== I 管理端流水监控 ==========")
    adm = req("GET", "/admin/core/transFlow/page?pageNum=1&pageSize=5", ta)
    rows3 = (adm.get("data") or {}).get("list") or []
    check("I1 管理端流水接口返回数据", api_code(adm) == 200 and len(rows3) > 0,
          f"code={api_code(adm)} n={len(rows3)}")
    check("I2 流水行带 status 字段", bool(rows3) and "status" in rows3[0],
          f"keys={sorted(rows3[0].keys()) if rows3 else []}")
    adm2 = req("GET", "/admin/core/transFlow/page?pageNum=1&pageSize=5&transType=3", ta)
    rows4 = (adm2.get("data") or {}).get("list") or []
    check("I3 管理端可按类型筛选", all(r.get("transType") == 3 for r in rows4), f"n={len(rows4)}")

    print("\n================= 汇总 =================")
    passed = sum(1 for _, ok, _ in results if ok)
    print(f"通过 {passed}/{len(results)}")
    for name, ok, detail in results:
        if not ok:
            print(f"  {NG} {name} —— {detail}")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
