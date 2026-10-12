#!/usr/bin/env bash
###############################################################################
# ww_finance 一键启动脚本（Linux 服务器）
#
# 用途：按依赖顺序启动「存管银行 → 五个后端微服务 → 两个前端站点」，
#       并在每一步做就绪探测，全部就绪后打印访问入口。
#
# 用法：
#   ./start-all.sh                 启动全部
#   ./start-all.sh backend         只启动银行 + 后端 5 个 jar（不起前端）
#   ./start-all.sh frontend        只启动前端（假定后端已就绪）
#   ./start-all.sh stop            停止全部本脚本启动的进程
#   ./start-all.sh status          只查看状态，不启动
#   ./start-all.sh restart         先 stop 再 start
#
# 目录约定（可通过环境变量覆盖）：
#   APP_HOME    jar 与前端 dist 的根目录            默认 /opt/ww
#   LOG_DIR     日志目录                            默认 $APP_HOME/logs
#   RUN_DIR     PID 目录                            默认 $APP_HOME/run
#
# 环境变量（都有默认值，服务器按需 export）：
#   JDK8_HOME       跑 api/gateway/message/oss/bank  的 JDK8  默认 java
#   JDK17_HOME      跑 finance-ai 的 JDK17                    默认 $JDK8_HOME
#   NACOS_ADDR      Nacos 地址                                 默认 127.0.0.1:8848
#   NODE_BIN        node 可执行文件                            默认 node
#   FRONT_PORT      前端静态服务器端口（仅无 nginx 时的兜底）  默认 5174
#
###############################################################################
set -uo pipefail

# 若同目录存在 env.sh 则自动加载（统一管理服务器地址等变量）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
[ -f "$SCRIPT_DIR/env.sh" ] && . "$SCRIPT_DIR/env.sh"

# ------------------------------------------------------------------ 路径与配置
APP_HOME="${APP_HOME:-/opt/ww}"
LOG_DIR="${LOG_DIR:-$APP_HOME/logs}"
RUN_DIR="${RUN_DIR:-$APP_HOME/run}"
FRONT_ROOT="${FRONT_ROOT:-$APP_HOME}"

JDK8_HOME="${JDK8_HOME:-}"
JDK17_HOME="${JDK17_HOME:-${JDK8_HOME}}"
JAVA8="${JDK8_HOME:+$JDK8_HOME/bin/}java"
JAVA17="${JDK17_HOME:+$JDK17_HOME/bin/}java"
NODE_BIN="${NODE_BIN:-node}"
NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"

# jar 文件名：与 target 产物一致（注意 -exec 后缀）
JAR_BANK="ww_bank-1.0.0.jar"
JAR_API="api-1.0-exec.jar"
JAR_GATEWAY="finance-gateway-1.0-exec.jar"
JAR_MESSAGE="finance-message-1.0-exec.jar"
JAR_OSS="finance-oss-1.0-exec.jar"
JAR_AI="finance-ai-1.0-exec.jar"

# 前端 dist 目录
DIST_USER="$FRONT_ROOT/user-dist"
DIST_ADMIN="$FRONT_ROOT/admin-dist"

# 端口
PORT_BANK=9090
PORT_API=8990
PORT_GATEWAY=8088
PORT_MESSAGE=8120
PORT_OSS=8130
PORT_AI=8081
PORT_FRONT=5174

# 日志滚动参数（单文件上限 100MB，保留 5 个）
LOG_MAX_SIZE=$((100 * 1024 * 1024))
LOG_KEEP=5

mkdir -p "$LOG_DIR" "$RUN_DIR"

# ------------------------------------------------------------------ 工具函数
c_ok()   { printf '\033[32m%s\033[0m\n' "$*"; }
c_warn() { printf '\033[33m%s\033[0m\n' "$*"; }
c_err()  { printf '\033[31m%s\033[0m\n' "$*"; }
c_info() { printf '\033[36m%s\033[0m\n' "$*"; }

# 端口是否已被监听（ss 优先，退回 netstat）
port_in_use() {
    local p="$1"
    if command -v ss >/dev/null 2>&1; then
        ss -lnt 2>/dev/null | awk '{print $4}' | grep -qE "[:.]${p}\$"
    else
        netstat -lnt 2>/dev/null | awk '{print $4}' | grep -qE "[:.]${p}\$"
    fi
}

# HTTP 就绪探测：非 HTTP 服务用端口探测
wait_port() {
    local name="$1" port="$2" timeout="${3:-60}" i=0
    while [ $i -lt "$timeout" ]; do
        if port_in_use "$port"; then
            c_ok "  [就绪] $name (端口 $port)"
            return 0
        fi
        sleep 1; i=$((i + 1))
    done
    c_warn "  [超时] $name 端口 $port ${timeout}s 内未监听（继续，稍后可用 status 复查）"
    return 1
}

wait_http() {
    local name="$1" url="$2" timeout="${3:-120}" i=0 code
    while [ $i -lt "$timeout" ]; do
        code=$(curl -s -m 3 -o /dev/null -w '%{http_code}' "$url" 2>/dev/null)
        if [ -n "$code" ] && [ "$code" != "000" ]; then
            c_ok "  [就绪] $name (HTTP $code) $url"
            return 0
        fi
        sleep 2; i=$((i + 2))
    done
    c_warn "  [超时] $name ${timeout}s 内未响应 $url"
    return 1
}

# 日志滚动：超过阈值就把当前日志改名归档，保留最近 N 份
rotate_log() {
    local log="$1"
    [ -f "$log" ] || return 0
    local size
    size=$(stat -c%s "$log" 2>/dev/null || echo 0)
    if [ "$size" -ge "$LOG_MAX_SIZE" ]; then
        local ts; ts=$(date +%Y%m%d_%H%M%S)
        mv "$log" "${log}.${ts}" 2>/dev/null
        # 只保留最近 LOG_KEEP 份
        ls -1t "${log}".* 2>/dev/null | tail -n +$((LOG_KEEP + 1)) | xargs -r rm -f
        c_info "  日志已归档: ${log}.${ts}"
    fi
}

# 启动一个 java 进程
# 用法: start_java <名称> <jar绝对路径> <端口> <java可执行> [额外JVM参数...]
start_java() {
    local name="$1" jar="$2" port="$3" java_bin="$4"; shift 4
    local extra="$*"
    local pidfile="$RUN_DIR/${name}.pid"
    local logfile="$LOG_DIR/${name}.log"

    if [ ! -f "$jar" ]; then
        c_err "  [跳过] $name 找不到 jar: $jar"
        return 1
    fi
    if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile" 2>/dev/null)" 2>/dev/null; then
        c_warn "  [已在跑] $name (PID $(cat "$pidfile"))"
        return 0
    fi
    if port_in_use "$port"; then
        c_warn "  [端口占用] $name 端口 $port 已被别的进程监听，跳过启动"
        return 1
    fi

    rotate_log "$logfile"
    c_info "  启动 $name -> $jar"
    # shellcheck disable=SC2086
    nohup "$java_bin" $extra -jar "$jar" >> "$logfile" 2>&1 &
    local pid=$!
    echo "$pid" > "$pidfile"
    c_ok "  $name 已拉起 PID=$pid  日志=$logfile"
    return 0
}

# 启动前端静态站点（无 nginx 时的兜底：node 起个静态服务）
# 生产推荐用 nginx 托管 dist，见 deploy/nginx.conf.sample
start_front() {
    local name="$1" dist="$2" port="$3"
    local pidfile="$RUN_DIR/${name}.pid"
    local logfile="$LOG_DIR/${name}.log"

    if [ ! -d "$dist" ]; then
        c_err "  [跳过] $name 找不到 dist 目录: $dist"
        return 1
    fi
    if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile" 2>/dev/null)" 2>/dev/null; then
        c_warn "  [已在跑] $name (PID $(cat "$pidfile"))"
        return 0
    fi
    if ! command -v "$NODE_BIN" >/dev/null 2>&1; then
        c_err "  [跳过] $name 未找到 node（生产建议用 nginx 托管 dist）"
        return 1
    fi

    rotate_log "$logfile"
    c_info "  启动 $name (静态服务) -> $dist"
    # serve 优先；没有就用 npx 现拉一个（内网可能失败，故生产建议 nginx）
    nohup "$NODE_BIN" -e '
        const http=require("http"),fs=require("fs"),path=require("path");
        const root=process.argv[1],port=+process.argv[2];
        const mime={".html":"text/html",".js":"application/javascript",".css":"text/css",
          ".json":"application/json",".png":"image/png",".jpg":"image/jpeg",".svg":"image/svg+xml",
          ".ico":"image/x-icon",".woff2":"font/woff2",".woff":"font/woff",".ttf":"font/ttf",".map":"application/json"};
        http.createServer((req,res)=>{
          let p=decodeURIComponent(req.url.split("?")[0]);
          let f=path.join(root,p);
          if(!fs.existsSync(f)||fs.statSync(f).isDirectory()) f=path.join(root,"index.html");
          fs.readFile(f,(e,d)=>{
            if(e){res.writeHead(404);return res.end("404");}
            res.writeHead(200,{"Content-Type":mime[path.extname(f)]||"application/octet-stream"});
            res.end(d);
          });
        }).listen(port,()=>console.log("static server on "+port+" root="+root));
    ' "$dist" "$port" >> "$logfile" 2>&1 &
    local pid=$!
    echo "$pid" > "$pidfile"
    c_ok "  $name 已拉起 PID=$pid  端口=$port  日志=$logfile"
    return 0
}

# 停止单个服务
stop_one() {
    local name="$1" pidfile="$RUN_DIR/${name}.pid"
    if [ ! -f "$pidfile" ]; then
        return 1
    fi
    local pid; pid=$(cat "$pidfile" 2>/dev/null)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        kill "$pid" 2>/dev/null
        local i=0
        while [ $i -lt 20 ] && kill -0 "$pid" 2>/dev/null; do sleep 1; i=$((i + 1)); done
        if kill -0 "$pid" 2>/dev/null; then
            c_warn "  $name 未正常退出，强制 kill -9"
            kill -9 "$pid" 2>/dev/null
        fi
        c_ok "  已停止 $name (PID $pid)"
    fi
    rm -f "$pidfile"
    return 0
}

# ------------------------------------------------------------------ 环境自检
precheck() {
    c_info "== 环境自检 =="
    local ok=1

    # 1) 依赖的中间件
    local nhost nport
    nhost="${NACOS_ADDR%%:*}"; nport="${NACOS_ADDR##*:}"
    if curl -s -m 5 -o /dev/null "http://${NACOS_ADDR}/nacos/v1/console/server/state" 2>/dev/null; then
        c_ok "  Nacos 可达 ($NACOS_ADDR)"
    else
        c_err "  Nacos 不可达 ($NACOS_ADDR) —— 注册失败会导致 api/gateway/message/oss 启不来"; ok=0
    fi
    for item in "MySQL:3306" "Redis:6379" "MongoDB:27017"; do
        local n="${item%%:*}" p="${item##*:}"
        if (echo > "/dev/tcp/127.0.0.1/$p") 2>/dev/null; then
            c_ok "  $n 可达 (127.0.0.1:$p)"
        else
            c_warn "  $n 不可达 (127.0.0.1:$p)"
        fi
    done

    # 2) JDK
    if "$JAVA8" -version >/dev/null 2>&1; then
        c_ok "  JDK8 可用 ($JAVA8)"
    else
        c_err "  JDK8 不可用 ($JAVA8)"; ok=0
    fi
    if "$JAVA17" -version >/dev/null 2>&1; then
        c_ok "  JDK17 可用 ($JAVA17)"
    else
        c_warn "  JDK17 不可用 ($JAVA17) —— finance-ai 会启动失败"
    fi

    # 3) 必备 jar
    for j in "$JAR_BANK" "$JAR_API" "$JAR_GATEWAY" "$JAR_MESSAGE" "$JAR_OSS" "$JAR_AI"; do
        if [ -f "$APP_HOME/$j" ]; then
            c_ok "  找到 $j"
        else
            c_err "  缺失 $j ($APP_HOME/$j)"; ok=0
        fi
    done
    return $ok
}

# ------------------------------------------------------------------ 各阶段
start_backend() {
    c_info "== [1/3] 启动存管银行（其余服务依赖它做资金划转）=="
    start_java "bank" "$APP_HOME/$JAR_BANK" "$PORT_BANK" "$JAVA8"
    wait_port "bank" "$PORT_BANK" 60

    c_info "== [2/3] 启动后端微服务（顺序：api → gateway → message → oss → ai）=="
    start_java "api" "$APP_HOME/$JAR_API" "$PORT_API" "$JAVA8"
    wait_port "api" "$PORT_API" 120

    start_java "gateway" "$APP_HOME/$JAR_GATEWAY" "$PORT_GATEWAY" "$JAVA8"
    wait_port "gateway" "$PORT_GATEWAY" 90

    start_java "message" "$APP_HOME/$JAR_MESSAGE" "$PORT_MESSAGE" "$JAVA8"
    wait_port "message" "$PORT_MESSAGE" 90

    start_java "oss" "$APP_HOME/$JAR_OSS" "$PORT_OSS" "$JAVA8"
    wait_port "oss" "$PORT_OSS" 90

    # ai 需要 JDK17
    start_java "ai" "$APP_HOME/$JAR_AI" "$PORT_AI" "$JAVA17"
    wait_port "ai" "$PORT_AI" 150
}

start_frontend() {
    c_info "== [3/3] 启动前端站点 =="
    start_front "front-user"  "$DIST_USER"  "$PORT_FRONT"
    start_front "front-admin" "$DIST_ADMIN" "$((PORT_FRONT + 1))"
    wait_port "front-user"  "$PORT_FRONT" 30
    wait_port "front-admin" "$((PORT_FRONT + 1))" 30
}

verify_backend() {
    c_info "== 验证：Nacos 注册情况 =="
    local svc
    for svc in service-core service-gateway service-sms service-oss; do
        printf '  %-18s -> ' "$svc"
        curl -s -m 5 "http://${NACOS_ADDR}/nacos/v1/ns/instance/list?serviceName=${svc}" 2>/dev/null \
            | grep -o '"ip":"[^"]*"' | tr '\n' ' ' || true
        echo ""
    done
    c_warn "  ⚠ 注册 IP 必须是服务器真实内网 IP；若是 172.x/192.168.x 虚拟网卡地址，"
    c_warn "    需给每个服务配 spring.cloud.nacos.discovery.ip=<真实内网IP>"
}

show_status() {
    c_info "== 运行状态 =="
    local name pid
    for name in bank api gateway message oss ai front-user front-admin; do
        local pidfile="$RUN_DIR/${name}.pid"
        if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile" 2>/dev/null)" 2>/dev/null; then
            c_ok "  [运行中] $name (PID $(cat "$pidfile"))"
        else
            printf '\033[90m  [未运行] %s\033[0m\n' "$name"
        fi
    done
    echo ""
    c_info "== 端口监听 =="
    for pp in "$PORT_BANK:bank" "$PORT_API:api" "$PORT_GATEWAY:gateway" \
              "$PORT_MESSAGE:message" "$PORT_OSS:oss" "$PORT_AI:ai" \
              "$PORT_FRONT:front-user" "$((PORT_FRONT + 1)):front-admin"; do
        local p="${pp%%:*}" n="${pp##*:}"
        if port_in_use "$p"; then c_ok "  $n 监听 $p"; else printf '\033[90m  %s 未监听 %s\033[0m\n' "$n" "$p"; fi
    done
}

stop_all() {
    c_info "== 停止全部（逆序：前端 → ai → oss → message → gateway → api → bank）=="
    for name in front-admin front-user ai oss message gateway api bank; do
        stop_one "$name" || printf '\033[90m  %s 未在运行\033[0m\n' "$name"
    done
}

print_entry() {
    local ip; ip=$(hostname -I 2>/dev/null | awk '{print $1}'); ip="${ip:-<服务器IP>}"
    echo ""
    c_ok "==================== 启动完成 ===================="
    echo "  用户端   http://${ip}:${PORT_FRONT}"
    echo "  管理端   http://${ip}:$((PORT_FRONT + 1))"
    echo "  网关     http://${ip}:${PORT_GATEWAY}       (前端唯一入口)"
    echo "  银行模拟 http://${ip}:${PORT_BANK}"
    echo "  Nacos    http://${NACOS_ADDR}/nacos"
    echo ""
    echo "  日志目录 $LOG_DIR      PID 目录 $RUN_DIR"
    echo "  停止     $0 stop      状态   $0 status"
    c_ok "=================================================="
}

# ------------------------------------------------------------------ 主流程
CMD="${1:-start}"

case "$CMD" in
    start)
        precheck || { c_err "环境自检未通过，请先修复上面标红的项"; exit 1; }
        start_backend
        verify_backend
        start_frontend
        show_status
        print_entry
        ;;
    backend)
        precheck || { c_err "环境自检未通过"; exit 1; }
        start_backend
        verify_backend
        show_status
        ;;
    frontend)
        start_frontend
        show_status
        ;;
    stop)
        stop_all
        ;;
    restart)
        stop_all
        sleep 3
        precheck || { c_err "环境自检未通过"; exit 1; }
        start_backend
        verify_backend
        start_frontend
        show_status
        print_entry
        ;;
    status)
        show_status
        ;;
    *)
        echo "用法: $0 {start|backend|frontend|stop|restart|status}"
        exit 1
        ;;
esac
