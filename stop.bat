@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================
echo   初中英语单词听写 - 停止中...
echo ========================================
echo.

echo [1/3] 停止 Redis 服务 (端口 6379)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":6379" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
)

echo [2/3] 停止 Edge TTS 语音服务 (端口 5050)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":5050" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
)

echo [3/3] 停止 Spring Boot 应用 (端口 8080)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a >nul 2>&1
)

echo.
echo ========================================
echo   所有服务已停止
echo ========================================
timeout /t 3 >nul
