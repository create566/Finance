"""通用接口响应探针：读 stdin 的 JSON，打印 code/msg/total 与要点。

用法：
  curl ... | python _probe.py                    # 打印 data 下的全部键（截断）或 list 前几条
  curl ... | python _probe.py id status amount   # 只打印 list 每条的指定字段
"""
import json
import sys

raw = sys.stdin.read()
try:
    d = json.loads(raw)
except Exception:
    print("NOT-JSON:", raw[:400])
    sys.exit(1)

print("code=", d.get("code"), "| msg=", d.get("msg") or d.get("message"))
data = d.get("data")
fields = sys.argv[1:]

if isinstance(data, dict):
    if "total" in data:
        print("total=", data.get("total"))
    lst = data.get("list")
    if isinstance(lst, list):
        for it in lst[:8]:
            if fields and isinstance(it, dict):
                print("  ", json.dumps({k: it.get(k) for k in fields}, ensure_ascii=False))
            else:
                print("  ", json.dumps(it, ensure_ascii=False)[:400])
    else:
        for k, v in data.items():
            print(f"  {k} = {json.dumps(v, ensure_ascii=False)[:300]}")
elif isinstance(data, list):
    for it in data[:8]:
        if fields and isinstance(it, dict):
            print("  ", json.dumps({k: it.get(k) for k in fields}, ensure_ascii=False))
        else:
            print("  ", json.dumps(it, ensure_ascii=False)[:300])
else:
    print("data=", json.dumps(data, ensure_ascii=False)[:300])
