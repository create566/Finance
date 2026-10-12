# ============================================================================
# ww_finance 部署环境变量（服务器上按需修改）
# 用法：source deploy/env.sh   或  在 start-all.sh 里 source 本文件
# ============================================================================

# ---------- 目录 ----------
export APP_HOME="/opt/ww"                 # jar 与 dist 根目录
export LOG_DIR="$APP_HOME/logs"
export RUN_DIR="$APP_HOME/run"
export FRONT_ROOT="$APP_HOME"

# ---------- JDK（两套，硬要求）----------
export JDK8_HOME="/usr/lib/jvm/jdk-8"
export JDK17_HOME="/usr/lib/jvm/jdk-17"   # finance-ai 专用

# ---------- Nacos ----------
export NACOS_ADDR="127.0.0.1:8848"

# ---------- 前端 ----------
export NODE_BIN="node"
export FRONT_PORT=5174

# ---------- 后端连接（供各服务 config/application.yml 或启动参数引用）----------
export DB_HOST="127.0.0.1"
export DB_USERNAME="root"
export DB_PASSWORD="123456"
export REDIS_HOST="127.0.0.1"
export REDIS_PORT="6379"
export MONGODB_URI="mongodb://127.0.0.1:27017/ww_finance_ai"
export CHROMADB_HOST="127.0.0.1"
export CHROMADB_PORT="8000"

# ---------- 银行接入 ----------
export BANK_BASE_URL="http://127.0.0.1:9090"
# ⚠️ 下面两个必须是「银行能访问到的公网地址」，不能是 localhost
export BANK_NOTIFY_BASE_URL="http://your-domain.com:8990"
export BANK_FRONT_BASE_URL="http://your-domain.com"

# ---------- 服务注册 IP（避免 Nacos 登记成虚拟网卡地址）----------
export NACOS_DISCOVERY_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"

# ---------- AI 服务地址 ----------
export AI_EMBEDDING_URL="http://127.0.0.1:8081"
export AI_SERVICE_URI="http://127.0.0.1:8081"
