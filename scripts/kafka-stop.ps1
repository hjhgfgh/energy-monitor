# 停止 Kafka broker
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts\kafka-stop.ps1
#
# 按命令行特征精确匹配 kafka.Kafka 进程，避免误杀其他 Java 程序
# （比如同时跑着的 em-server / em-simulator）

$targets = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like "*kafka.Kafka*" }

if (-not $targets) {
    Write-Host "Kafka 未在运行" -ForegroundColor Yellow
    exit 0
}

foreach ($p in $targets) {
    Write-Host ("正在停止 Kafka 进程 PID=" + $p.ProcessId) -ForegroundColor Cyan
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}

Start-Sleep -Seconds 2
$still = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like "*kafka.Kafka*" }

if ($still) {
    Write-Host "Kafka 进程仍在运行，请手动检查" -ForegroundColor Red
    exit 1
}
Write-Host "Kafka 已停止" -ForegroundColor Green
