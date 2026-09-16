param(
    [string]$Url = "http://127.0.0.1:8090/api/devices/latest-data",
    [int]$Threads = 20,
    [int]$Seconds = 15,
    [string]$OutFile = "C:\Users\hjhgf\WorkBuddy\2026-09-16-05-41-22\_bench_gw.txt"
)

$ErrorActionPreference = "Stop"

# 登录拿 token
$login = Invoke-WebRequest -Uri "http://127.0.0.1:8090/api/auth/login" -Method Post `
    -Body '{"username":"admin","password":"admin123"}' -ContentType "application/json" `
    -UseBasicParsing -TimeoutSec 15
$token = ([System.Text.Encoding]::UTF8.GetString($login.RawContentStream.ToArray()) | ConvertFrom-Json).data.token
if (-not $token) { Write-Output "LOGIN-FAILED"; exit 1 }

Add-Type -TypeDefinition ([System.IO.File]::ReadAllText("$PSScriptRoot\EmBench.cs")) -Language CSharp

[EmBench.Bench]::Run($Url, $token, $Threads, $Seconds)

$lats = [EmBench.Bench]::Latencies
$lats.Sort()
$n = $lats.Count
function Pct($q) { if ($n -gt 0) { [math]::Round($lats[[int][math]::Floor($n * $q)], 1) } else { 0 } }

$total = [EmBench.Bench]::Ok + [EmBench.Bench]::Blocked + [EmBench.Bench]::Errors
$result = [ordered]@{
    url            = $Url
    threads        = $Threads
    duration_sec   = $Seconds
    total_requests = $total
    http_200       = [EmBench.Bench]::Ok
    http_429       = [EmBench.Bench]::Blocked
    other_errors   = [EmBench.Bench]::Errors
    rps_total      = [math]::Round($total / $Seconds, 1)
    rps_ok         = [math]::Round([EmBench.Bench]::Ok / $Seconds, 1)
    p50_ms         = (Pct 0.50)
    p95_ms         = (Pct 0.95)
    p99_ms         = (Pct 0.99)
}
$result | ConvertTo-Json | Out-File $OutFile -Encoding utf8
