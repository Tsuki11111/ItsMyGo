# 停止 ItsMyGo 全栈（PowerShell，保留数据卷）
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
docker compose down
Write-Host "已停止。数据卷保留；要连数据一起清掉：docker compose down -v"
