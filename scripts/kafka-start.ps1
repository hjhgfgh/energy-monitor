# 启动 Kafka（KRaft 单节点模式，无需 ZooKeeper）
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts\kafka-start.ps1
#
# 说明：
#   Kafka 自带的 bin/windows/*.bat 在 Windows 上会因 classpath 过长
#   报 "The input line is too long."（cmd 命令行有 8191 字符上限，
#   而 Kafka 4.x 的 libs 目录有 100+ 个 jar）。
#   因此这里直接用 java + 通配符 classpath 启动，绕开该限制。

$ErrorActionPreference = 'Stop'

# Kafka 安装位置与 JDK（Kafka 脚本依赖 JAVA_HOME）
$KafkaHome = "C:\Users\hjhgf\kafka\kafka_2.13-4.3.1"
$JavaHome  = "C:\Users\hjhgf\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.19.10-hotspot"

if (-not (Test-Path $KafkaHome)) {
    Write-Host "找不到 Kafka 目录: $KafkaHome" -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = $JavaHome
Set-Location $KafkaHome

Write-Host "正在启动 Kafka broker (KRaft)..." -ForegroundColor Cyan
Write-Host "  broker   : localhost:9092"
Write-Host "  controller: localhost:9093"
Write-Host "  日志目录 : C:\Users\hjhgf\kafka\data"
Write-Host ""

java "-Dlog4j2.configurationFile=config/log4j2.yaml" -cp "libs/*" kafka.Kafka "config/server.properties"
