@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo ========================================
echo   初中英语单词听写 - 启动中...
echo ========================================
echo.

echo [1/3] 启动 Redis 服务 (端口 6379)...
start "Redis" /D "D:\Redis-x64-5.0.14.1" redis-server.exe

echo [2/3] 启动 Edge TTS 语音服务 (端口 5050)...
start "" /B python scripts\tts_server.py

echo [3/3] 启动 Spring Boot 应用 (端口 8080)...
echo.
echo 启动完成后访问: http://localhost:8080/dictation.html
echo ========================================
echo.

call mvn spring-boot:run
