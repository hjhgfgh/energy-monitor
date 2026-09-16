# 停止 Nacos
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts\nacos-stop.ps1
#
# 按命令行特征精确匹配 nacos 进程，避免误杀 em-server / kafka 等其他 Java 程序。
# 注意：Nacos 停止后 gRPC 端口 9848/9849 需要几秒才完全释放，
# 若不等待就立刻重启，会因端口占用而启动失败。

$targets = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like "*nacos*" }

if (-not $targets) {
    Write-Host "Nacos 未在运行" -ForegroundColor Yellow
    exit 0
}

foreach ($p in $targets) {
    Write-Host ("正在停止 Nacos 进程 PID=" + $p.ProcessId) -ForegroundColor Cyan
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}

# 等待端口释放
$deadline = (Get-Date).AddSeconds(20)
$free = $false
while ((Get-Date) -lt $deadline) {
    $busy = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -in 8848, 9848, 9849 }
    if (-not $busy) { $free = $true; break }
    Start-Sleep -Seconds 1
}

if ($free) {
    Write-Host "Nacos 已停止，端口已释放" -ForegroundColor Green
} else {
    Write-Host "仍有端口被占用，请手动检查" -ForegroundColor Red
    exit 1
}
