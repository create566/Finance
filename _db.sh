#!/usr/bin/env bash
# 本地 ww_finance 库快捷查询：  ./_db.sh "select ..."  [库名]
export PATH="/c/Users/zhangxiangxiang/.workbuddy/binaries/PortableGit/versions/1.2.0/usr/bin:/c/Windows/System32:/c/Windows"
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
DB="${2:-ww_finance}"
"$MYSQL" -h127.0.0.1 -P3306 -uroot -p123456 --default-character-set=utf8mb4 -t "$DB" -e "$1" 2>&1 | grep -v "Using a password on the command line"
