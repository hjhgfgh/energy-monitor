# 启动 Nacos 3.0.3（单机模式 + MySQL 存储）
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts\nacos-start.ps1
#
# 控制台：http://127.0.0.1:8080/nacos/   默认账号 nacos / 需首次设置密码
# Server API：http://127.0.0.1:8848/nacos

$ErrorActionPreference = 'Stop'

$NacosHome = "C:\Users\hjhgf\nacos\nacos"
$JavaHome  = "C:\Users\hjhgf\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.19.10-hotspot"

# ---------------------------------------------------------------
# 关键：清除宿主环境注入的 SERVER__PORT / SERVER__HOST
#
# Spring Boot 的 relaxed binding 会把 SERVER__PORT 解析成 server.port，
# 导致 Nacos Console（Spring Boot 应用）去绑宿主占用的端口并启动失败：
#   Tomcat initialized with port 51797
#   Port 51797 is already in use
# 表现为「莫名其妙的端口冲突」，实际与端口本身无关。
# ---------------------------------------------------------------
[System.Environment]::SetEnvironmentVariable('SERVER__PORT', $null, 'Process')
[System.Environment]::SetEnvironmentVariable('SERVER__HOST', $null, 'Process')

if (-not (Test-Path $NacosHome)) {
    Write-Host "找不到 Nacos 目录: $NacosHome" -ForegroundColor Red
    exit 1
}

# 清理残留进程，否则 gRPC 端口 9848/9849 被占用会导致启动失败
$stale = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like "*nacos*" }
if ($stale) {
    Write-Host ("清理残留 Nacos 进程: " + (@($stale).Count) + " 个") -ForegroundColor Yellow
    foreach ($p in $stale) { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 4
}

$env:JAVA_HOME = $JavaHome
Set-Location "$NacosHome\bin"

Write-Host "正在启动 Nacos（standalone + MySQL）..." -ForegroundColor Cyan
Write-Host "  Server API : http://127.0.0.1:8848/nacos"
Write-Host "  Console    : http://127.0.0.1:8080/nacos/"
Write-Host "  gRPC       : 9848 / 9849"
Write-Host ""

& .\startup.cmd -m standalone
