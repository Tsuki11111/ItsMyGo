# ItsMyGo 全栈一键启动（PowerShell）
# 用法：.\scripts\docker-up.ps1
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "已生成 .env（compose 有安全默认值，可直接启动）。"
}

docker compose up -d --build

$FrontendPort = if ($env:FRONTEND_PORT) { $env:FRONTEND_PORT } else { "8088" }
$BackendPort = if ($env:BACKEND_PORT) { $env:BACKEND_PORT } else { "8080" }
$MinioConsolePort = if ($env:MINIO_CONSOLE_PORT) { $env:MINIO_CONSOLE_PORT } else { "9001" }
$RabbitConsolePort = if ($env:RABBITMQ_CONSOLE_PORT) { $env:RABBITMQ_CONSOLE_PORT } else { "15672" }

Write-Host ""
Write-Host "ItsMyGo 已启动："
Write-Host "  前端    http://localhost:$FrontendPort"
Write-Host "  后端    http://localhost:$BackendPort/api/health"
Write-Host "  MinIO   http://localhost:$MinioConsolePort"
Write-Host "  RabbitMQ 管理台 http://localhost:$RabbitConsolePort"
Write-Host ""
Write-Host "首次使用还没有管理员：在 .env 配 BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD 后执行"
Write-Host "  docker compose up -d backend"
Write-Host ""
Write-Host "可选导入演示数据（44 个商品，等 backend 建完表后执行）："
Write-Host "  docker compose --profile seed run --rm seed"
