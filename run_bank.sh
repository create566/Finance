#!/usr/bin/env bash
# 启动「银行模拟服务」（ww_bank-1.0.0.jar，端口 9090），并把日志落到 logs/bank/ww_bank.log
#
# 用法：
#   ./run_bank.sh          前台启动（Ctrl+C 停止）
#   ./run_bank.sh &        后台启动
#   ./run_bank.sh --server.port=9091   额外参数原样透传给 jar
#
# 为什么这里用启动参数而不是 logback-spring.xml：
#   该 jar 是编译好的第三方件（Start-Class=com.heepay.ManageApplication），仓库里没有源码，
#   加不了配置文件；它本身是 Spring Boot 2.3.4 应用，用 --logging.file.name 即可落盘。
#   ⚠️ 它内部 mybatis 配了 log-impl=StdOutImpl，SQL 只走 stdout，不会进这个文件。

cd "D:/桌面/实训/demo" || exit 1

LOG_DIR="D:/桌面/实训/demo/logs/bank"
mkdir -p "$LOG_DIR"

if netstat -ano 2>/dev/null | grep ":9090" | grep -q LISTENING; then
    echo "⚠️  9090 已在监听，银行服务可能已经在运行。先停掉再执行本脚本。"
    exit 0
fi

echo "启动银行模拟服务："
echo "  Spring 日志            → $LOG_DIR/ww_bank.log"
echo "  stdout（含 MyBatis SQL）→ $LOG_DIR/bank-stdout.log"
# 为什么要 tee stdout：该 jar 的 mybatis 是 log-impl=StdOutImpl，它执行的每一条 SQL
# （谁的账户、加减多少、余额变成多少）只走 stdout，不进 --logging.file.name 指向的文件。
# 对账时最需要看的就是这些 SQL，因此必须把 stdout 一并落盘，否则银行侧无据可查。
# 注意：这里不用 exec，保留管道以便 tee 落盘；Ctrl+C 会终止整条管道。
java -jar ww_bank-1.0.0.jar --logging.file.name="$LOG_DIR/ww_bank.log" "$@" 2>&1 | tee -a "$LOG_DIR/bank-stdout.log"
