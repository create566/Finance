#!/usr/bin/env python
"""本地联调用：签发与 api 模块 TokenUtil 完全等价的 token，并写入 Redis 会话。

为什么要自己签：项目里的测试账号密码都是 BCrypt，明文不可知；
而 JWT 秘钥在 TokenUtil 里是硬编码的（vsofo-5grcs-secret / HS512），
claims 只有 token_userid（+ admin 的 token_username/token_role）。
自己签一个再塞进 Redis 的 login:token:<role>:<id>，就能拿到登录态，
不必改任何账号密码，也不影响别人的会话。

用法：
    python _token.py user 33              # 打印 token 并写入 Redis
    python _token.py admin 1
    python _token.py user 33 --print      # 只打印
"""
import base64
import hashlib
import hmac
import json
import socket
import sys
import time

SECRET = "vsofo-5grcs-secret"
TOKEN_HEAD = "5grcs "
TTL = 604800


def signing_key() -> bytes:
    """jjwt 0.9.1 的 signWith(alg, String) 会把密钥当 base64 解一次再用。

    所以真正的 HMAC key 是 base64-decode("vsofo-5grcs-secret")，
    直接用 UTF-8 原文当 key 会被服务端判成"token无效"（这个坑踩过一次）。
    """
    return base64.b64decode(SECRET + "=" * (-len(SECRET) % 4))


def b64(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def make_token(claims: dict, ttl: int = TTL) -> str:
    payload = dict(claims)
    payload["exp"] = int(time.time()) + ttl
    header = b64(json.dumps({"alg": "HS512"}, separators=(",", ":")).encode())
    body = b64(json.dumps(payload, separators=(",", ":")).encode())
    signing_input = f"{header}.{body}".encode()
    sig = hmac.new(signing_key(), signing_input, hashlib.sha512).digest()
    return f"{TOKEN_HEAD}{header}.{body}.{b64(sig)}"


def redis_cmd(*args: str, host: str = "127.0.0.1", port: int = 6379) -> str:
    """直连 Redis 发一条命令并读回一行应答。

    不用 `docker exec ww_redis redis-cli`：本机 Bash 里没有 docker，
    而 Redis 的 6379 已经映射到宿主机，直接用 RESP 协议说话最省事。
    """
    payload = f"*{len(args)}\r\n".encode()
    for a in args:
        raw = a.encode()
        payload += f"${len(raw)}\r\n".encode() + raw + b"\r\n"
    with socket.create_connection((host, port), timeout=5) as sock:
        sock.sendall(payload)
        return sock.recv(65536).decode(errors="replace").strip()


def redis_set(key: str, value: str) -> None:
    reply = redis_cmd("SET", key, value)
    if not reply.startswith("+OK"):
        raise RuntimeError(f"Redis SET 失败: {reply}")


def redis_get(key: str) -> str:
    return redis_cmd("GET", key)


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    role = sys.argv[1]
    uid = sys.argv[2]
    print_only = "--print" in sys.argv

    if role == "admin":
        token = make_token({"token_userid": uid, "token_username": "admin", "token_role": "admin"})
        key = f"login:token:admin:{uid}"
    else:
        token = make_token({"token_userid": uid})
        key = f"login:token:user:{uid}"

    if not print_only:
        redis_set(key, token)
        print(f"OK  {key} 已写入（有效期 7 天）", file=sys.stderr)
    print(token)
    return 0

if __name__ == "__main__":
    sys.exit(main())
