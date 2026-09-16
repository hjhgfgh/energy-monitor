# 启动全部微服务
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1
#
# 依赖：Nacos(8848)、Kafka(9092)、MySQL(3306)、Redis(6379) 需已就绪
#
# 端口分配：
#   em-gateway       8000   网关（Nacos Console 占了 8080，故网关改用 8000）
#   em-device-access 9000/TCP + 8081/HTTP
#   em-data-process  8082
#   em-web-api       8083

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------
# 关键：清除宿主环境注入的 SERVER__PORT / SERVER__HOST
#
# Spring Boot 的 relaxed binding 会把 SERVER__PORT 解析成 server.port，
# 覆盖 application.yml 中的配置，导致每个服务都去绑 51797（宿主占用），
# 全部报 "Port 51797 was already in use" 启动失败。
# 每个新起的 java 进程都会继承当前进程的环境变量，所以在这里清一次即可。
# ---------------------------------------------------------------
[System.Environment]::SetEnvironmentVariable('SERVER__PORT', $null, 'Process')
[System.Environment]::SetEnvironmentVariable('SERVER__HOST', $null, 'Process')

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$LogDir = Join-Path $ProjectRoot "logs"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

$services = @(
    @{ Name = "em-device-access"; Jar = "em-device-access\target\em-device-access-1.0.0-SNAPSHOT.jar"; Port = "9000/TCP + 8081" },
    @{ Name = "em-data-process";  Jar = "em-data-process\target\em-data-process-1.0.0-SNAPSHOT.jar";   Port = "8082" },
    @{ Name = "em-web-api";       Jar = "em-web-api\target\em-web-api-1.0.0-SNAPSHOT.jar";             Port = "8083" },
    @{ Name = "em-gateway";       Jar = "em-gateway\target\em-gateway-1.0.0-SNAPSHOT.jar";             Port = "8000" }
)

foreach ($svc in $services) {
    $jarPath = Join-Path $ProjectRoot $svc.Jar
    if (-not (Test-Path $jarPath)) {
        Write-Host ("跳过 " + $svc.Name + "：找不到 " + $svc.Jar + "（请先执行 mvn clean package）") -ForegroundColor Yellow
        continue
    }

    $logFile = Join-Path $LogDir ($svc.Name + ".log")
    Write-Host ("启动 " + $svc.Name + "  端口 " + $svc.Port) -ForegroundColor Cyan

    Start-Process -FilePath "java" `
        -ArgumentList @("-jar", $jarPath) `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError (Join-Path $LogDir ($svc.Name + ".err.log")) `
        -WindowStyle Hidden

    # 逐个拉起，给前一个留出注册到 Nacos 的时间，避免同时启动造成瞬时资源争抢
    Start-Sleep -Seconds 12
}

Write-Host ""
Write-Host "全部服务已拉起，日志目录：$LogDir" -ForegroundColor Green
Write-Host "验证："
Write-Host "  网关鉴权拦截  curl http://localhost:8000/api/devices"
Write-Host "  登录获取令牌  curl -X POST http://localhost:8000/api/auth/login -H ""Content-Type: application/json"" -d ""{""""username"""":""""admin"""",""""password"""":""""admin123""""}"""
Write-Host "  Nacos 控制台  http://127.0.0.1:8080/nacos/"
