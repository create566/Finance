@echo off
chcp 65001 >nul 2>&1
echo ==========================================
echo       环境健康检查 v1.0 (Windows)
echo ==========================================
echo.

echo [1/5] 检查 MySQL (3306) ...
mysql -u root -p123456 -e "SELECT 'MySQL连接成功' AS status;" 2>nul
if %errorlevel%==0 (
    echo     ✅ MySQL 运行正常
) else (
    echo     ❌ MySQL 未运行或密码错误
    echo        请执行: net start mysql
)
echo.

echo [2/5] 检查 Redis (6379) ...
redis-cli ping 2>nul | findstr /C:"PONG" >nul
if %errorlevel%==0 (
    echo     ✅ Redis 运行正常
) else (
    echo     ❌ Redis 未运行
    echo        请执行: redis-server
)
echo.

echo [3/5] 检查后端API服务 (8990) ...
curl -s -o nul -w "%%{http_code}" http://localhost:8990/api/core/user/getUserInfo 2>nul | findstr /R "[0-9][0-9][0-9]" >nul
if %errorlevel%==0 (
    for /f %%a in ('curl -s -o nul -w "%%{http_code}" http://localhost:8990/api/core/user/getUserInfo 2^nul') do set HTTP_CODE=%%a
    if "%HTTP_CODE%"=="200" (
        echo     ✅ 后端API运行正常 (HTTP 200)
    ) else (
        echo     ⚠️  后端API运行中但返回 HTTP %HTTP_CODE%
    )
) else (
    echo     ❌ 后端API未运行 (端口8990)
    echo        请启动: DemoApplication.java (Spring Boot)
)
echo.

echo [4/5] 检查银行模拟服务 (9090) ...
curl -s -o nul -w "%%{http_code}" http://localhost:9090/ 2>nul | findstr /R "[0-9][0-9][0-9]" >nul
if %errorlevel%==0 (
    echo     ✅ 银行服务运行正常
) else (
    echo     ⚠️  银行服务未运行 (端口9090)
    echo        充值/提现功能将不可用（这是正常的，如果没有银行模拟服务）
)
echo.

echo [5/5] 检查数据库表结构 ...
for /f %%i in ('mysql -u root -p123456 -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='ww_finance' AND TABLE_NAME IN ('lend', 'lend_item');" 2^>nul') do set TABLE_COUNT=%%i
if "%TABLE_COUNT%"=="2" (
    echo     ✅ lend 和 lend_item 表已存在
) else (
    echo     ❌ 数据库表缺失 (当前: %TABLE_COUNT%/2)
    echo.
    echo     🔧 正在自动修复...
    mysql -u root -p123456 < sql\fix_lend_tables.sql 2>nul
    if %errorlevel%==0 (
        echo     ✅ 数据库修复成功！
    ) else (
        echo     ❌ 自动修复失败，请手动执行:
        echo        cd D:\桌面\实训\demo
        echo        mysql -u root -p123456 ^< sql\fix_lend_tables.sql
    )
)
echo.

echo ==========================================
echo            检查结果汇总
echo ==========================================
echo.
echo 下一步操作：
echo   1. 如果上述服务都显示 ✅，可以开始使用系统
echo   2. 如果有 ❌ 或 ⚠️，请按照提示启动对应服务
echo   3. 完成后按顺序执行：
echo      a. 完成借款人认证（前端提交 → 后台审核）
echo      b. 完成银行卡绑定
echo      c. 测试借款、充值等功能
echo.
echo 详细操作指南请查看：完整修复方案.md
echo.
pause