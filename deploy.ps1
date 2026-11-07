param(
    [Parameter(Position = 0)]
    [string]$Command = "up",
    [switch]$NoSeed
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSCommandPath
Set-Location $Root
$EnvFile = Join-Path $Root ".env"
$script:AdminPasswordNew = ""

function Show-Usage {
    Write-Host "./deploy.ps1              启动全栈（首次自动生成 .env / 管理员 / 演示数据）"
    Write-Host "./deploy.ps1 down         停止（保留数据）"
    Write-Host "./deploy.ps1 logs         跟踪后端日志"
    Write-Host "./deploy.ps1 status       查看容器状态"
    Write-Host "./deploy.ps1 seed         重新导入 44 个演示商品"
    Write-Host "./deploy.ps1 reset        停止并删除所有数据卷（需输入 yes）"
    Write-Host ""
    Write-Host "—— 联合 rag_helper（演示环境）——"
    Write-Host "./deploy.ps1 joint        启动 ItsMyGo + rag_helper 联合栈（8088）"
    Write-Host "./deploy.ps1 joint-down   停止两套（保留数据）"
    Write-Host "./deploy.ps1 joint-logs   跟踪 rag_helper agent 日志"
    Write-Host "./deploy.ps1 joint-status 查看两套容器状态"
    Write-Host ""
    Write-Host "rag_helper 目录：RAG_HELPER_DIR 环境变量 / 仓库根 .rag-helper-dir 文件 / 默认 ../rag_helper。"
}

function Test-Docker {
    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Desktop 未启动，请先启动它。"
    }
}

function New-Hex([int]$Bytes = 24) {
    $buffer = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($buffer)
    $rng.Dispose()
    ($buffer | ForEach-Object { $_.ToString("x2") }) -join ""
}

function Get-EnvValue([string]$Key) {
    if (-not (Test-Path $EnvFile)) { return "" }
    $line = Get-Content $EnvFile -Encoding UTF8 |
        Where-Object { $_ -match "^$Key=" } |
        Select-Object -Last 1
    if (-not $line) { return "" }
    return (($line -split "=", 2)[1]).Trim()
}

function Set-EnvValue([string]$Key, [string]$Value) {
    $lines = @()
    if (Test-Path $EnvFile) { $lines = @(Get-Content $EnvFile -Encoding UTF8) }
    $found = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^$Key=") {
            $lines[$i] = "$Key=$Value"
            $found = $true
        }
    }
    if (-not $found) { $lines += "$Key=$Value" }
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($EnvFile, $lines, $utf8)
}

function Ensure-Env {
    if (-not (Test-Path $EnvFile)) {
        Copy-Item ".env.example" $EnvFile
        Write-Host "已生成 .env（密码和密钥会自动生成，不需要手填）。"
    }

    if (-not (Get-EnvValue "JWT_SECRET")) { Set-EnvValue "JWT_SECRET" (New-Hex 48) }
    if (-not (Get-EnvValue "DB_USERNAME")) { Set-EnvValue "DB_USERNAME" "itsmygo" }
    if (-not (Get-EnvValue "DB_PASSWORD")) { Set-EnvValue "DB_PASSWORD" (New-Hex 24) }
    if (-not (Get-EnvValue "DB_NAME")) { Set-EnvValue "DB_NAME" "its_mygo" }
    if (-not (Get-EnvValue "MYSQL_ROOT_PASSWORD")) { Set-EnvValue "MYSQL_ROOT_PASSWORD" (New-Hex 24) }
    if (-not (Get-EnvValue "RABBITMQ_USERNAME")) { Set-EnvValue "RABBITMQ_USERNAME" "itsmygo" }
    if (-not (Get-EnvValue "RABBITMQ_PASSWORD")) { Set-EnvValue "RABBITMQ_PASSWORD" (New-Hex 24) }
    if (-not (Get-EnvValue "MINIO_ACCESS_KEY")) { Set-EnvValue "MINIO_ACCESS_KEY" ("itsmygo" + (New-Hex 4)) }
    if (-not (Get-EnvValue "MINIO_SECRET_KEY")) { Set-EnvValue "MINIO_SECRET_KEY" (New-Hex 24) }

    # 图片与前端同源反代，URL 不带主机名；旧部署若残留绝对地址，
    # 升级时手工改成 "/" 并迁移数据库中的历史 URL（见 docs/deployment.md）
    if (-not (Get-EnvValue "MINIO_PUBLIC_ENDPOINT")) {
        Set-EnvValue "MINIO_PUBLIC_ENDPOINT" "/"
    }

    if (-not (Get-EnvValue "BOOTSTRAP_ADMIN_USERNAME")) { Set-EnvValue "BOOTSTRAP_ADMIN_USERNAME" "admin" }
    if (-not (Get-EnvValue "BOOTSTRAP_ADMIN_PASSWORD")) {
        $script:AdminPasswordNew = "Admin" + (New-Hex 8)
        Set-EnvValue "BOOTSTRAP_ADMIN_PASSWORD" $script:AdminPasswordNew
    }
}

function Wait-Backend {
    $port = Get-EnvValue "BACKEND_PORT"
    if (-not $port) { $port = "8080" }
    Write-Host "等待后端就绪..."
    for ($i = 0; $i -lt 90; $i++) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$port/api/health" -TimeoutSec 3
            if ($response.StatusCode -eq 200) { return }
        } catch {
            # 后端还没起来，继续等
        }
        Start-Sleep -Seconds 2
    }
    throw "后端启动超时。查看日志：./deploy.ps1 logs"
}

function Import-SeedIfEmpty {
    if ($NoSeed) { return }
    $db = Get-EnvValue "DB_NAME"
    if (-not $db) { $db = "its_mygo" }
    $out = docker compose exec -T mysql sh -c "mysql -uroot -p`"`$MYSQL_ROOT_PASSWORD`" --default-character-set=utf8mb4 -N -e 'select count(*) from $db.products'" 2>$null
    $count = ($out | Out-String).Trim()
    if ($count -eq "0") {
        Write-Host "首次启动：导入 44 个演示商品..."
        docker compose --profile seed run --rm seed
    }
}

function Show-Summary {
    $frontendPort = Get-EnvValue "FRONTEND_PORT"
    if (-not $frontendPort) { $frontendPort = "8088" }
    Write-Host ""
    Write-Host "ItsMyGo 已启动：http://localhost:$frontendPort"
    Write-Host "管理员：$(Get-EnvValue 'BOOTSTRAP_ADMIN_USERNAME')"
    if ($script:AdminPasswordNew) {
        Write-Host "初始密码：$($script:AdminPasswordNew)（已写入 .env，请自行保存）"
    } else {
        Write-Host "密码见 .env 的 BOOTSTRAP_ADMIN_PASSWORD"
    }
    Write-Host "停止：./deploy.ps1 down    日志：./deploy.ps1 logs"
}

# ── 联合 rag_helper（演示环境）──────────────────────────────────
# 联合入口已收归本仓库：rag_helper 仓的 deploy.ps1 只做转发。

function Find-RagHelper {
    $dir = ""
    if ($env:RAG_HELPER_DIR) {
        $dir = $env:RAG_HELPER_DIR
    } elseif (Test-Path (Join-Path $Root ".rag-helper-dir")) {
        $dir = (Get-Content (Join-Path $Root ".rag-helper-dir") -Raw).Trim()
    } elseif (Test-Path (Join-Path (Split-Path $Root -Parent) "rag_helper")) {
        $dir = Join-Path (Split-Path $Root -Parent) "rag_helper"
    }
    if (-not $dir -or -not (Test-Path (Join-Path $dir "docker-compose.yml"))) {
        throw "找不到 rag_helper（需要含 docker-compose.yml 的目录）。请设置 RAG_HELPER_DIR 或写 .rag-helper-dir。"
    }
    return (Resolve-Path $dir).Path
}

function Get-RagEnv([string]$Dir, [string]$Key) {
    $file = Join-Path $Dir ".env"
    if (-not (Test-Path $file)) { return "" }
    $line = Get-Content $file -Encoding UTF8 | Where-Object { $_ -match "^$Key=" } | Select-Object -Last 1
    if (-not $line) { return "" }
    return (($line -split "=", 2)[1]).Trim()
}

function Set-RagEnv([string]$Dir, [string]$Key, [string]$Value) {
    $file = Join-Path $Dir ".env"
    $lines = @()
    if (Test-Path $file) { $lines = @(Get-Content $file -Encoding UTF8) }
    $found = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match "^$Key=") { $lines[$i] = "$Key=$Value"; $found = $true }
    }
    if (-not $found) { $lines += "$Key=$Value" }
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($file, $lines, $utf8)
}

function Ensure-RagEnv([string]$Dir) {
    $file = Join-Path $Dir ".env"
    if (-not (Test-Path $file)) {
        Copy-Item (Join-Path $Dir ".env.example") $file
        Write-Host "已生成 rag_helper/.env。"
    }
    $missing = @()
    foreach ($key in @("OPENAI_API_KEY", "DEEPSEEK_API_KEY", "EMBEDDING_API_KEY")) {
        $v = Get-RagEnv $Dir $key
        if (-not $v -or $v -eq "sk-xxx") { $missing += $key }
    }
    if ($missing.Count -gt 0) {
        throw "rag_helper 还差模型密钥：$($missing -join ', ')，请编辑 $file"
    }
    if (-not (Get-RagEnv $Dir "AGENT_GRPC_KEY")) {
        Set-RagEnv $Dir "AGENT_GRPC_KEY" (New-Hex 32)
        Write-Host "已为 rag_helper 生成 AGENT_GRPC_KEY。"
    }
}

function Sync-AgentKeys([string]$Dir) {
    $key = Get-RagEnv $Dir "AGENT_GRPC_KEY"
    Set-EnvValue "AGENT_GRPC_KEY" $key
    Set-EnvValue "AGENT_IMPORT_KEY" $key
}

function Wait-Agent([string]$Dir) {
    $port = Get-RagEnv $Dir "RAG_HTTP_PORT"
    if (-not $port) { $port = "8002" }
    Write-Host "等待 agent 就绪..."
    for ($i = 0; $i -lt 90; $i++) {
        try {
            $r = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$port/health" -TimeoutSec 3
            if ($r.StatusCode -eq 200) { return }
        } catch {
            # agent 还没起来，继续等
        }
        Start-Sleep -Seconds 2
    }
    throw "agent 启动超时。查看日志：./deploy.ps1 joint-logs"
}

function Show-JointSummary {
    $frontendPort = Get-EnvValue "FRONTEND_PORT"
    if (-not $frontendPort) { $frontendPort = "8088" }
    Write-Host ""
    Write-Host "联合部署已启动（演示环境）："
    Write-Host "  ItsMyGo   http://localhost:$frontendPort"
    Write-Host "  客服能力  （知识库导入 / 聊天 / 评测都在平台页面内，Java 代理调用 rag_helper）"
    Write-Host "  管理员：$(Get-EnvValue 'BOOTSTRAP_ADMIN_USERNAME')"
    if ($script:AdminPasswordNew) {
        Write-Host "  初始密码：$($script:AdminPasswordNew)（已写入 .env，请自行保存）"
    } else {
        Write-Host "  密码见 .env 的 BOOTSTRAP_ADMIN_PASSWORD"
    }
    Write-Host "停止两套：./deploy.ps1 joint-down    日志：./deploy.ps1 joint-logs"
}

switch ($Command.ToLower()) {
    "up" {
        Test-Docker
        Ensure-Env
        docker compose up -d --build
        Wait-Backend
        Import-SeedIfEmpty
        Show-Summary
    }
    "joint" {
        Test-Docker
        Ensure-Env
        $rag = Find-RagHelper
        Ensure-RagEnv $rag
        Sync-AgentKeys $rag
        $env:RAG_AGENT_GRPC_TARGET = "rag-agent:8003"
        $env:RAG_AGENT_IMPORT_URL = "http://rag-import:8001"

        Write-Host "==> 启动 ItsMyGo（8088）"
        docker compose up -d --build
        Wait-Backend
        Import-SeedIfEmpty

        Write-Host "==> 启动 rag_helper（接入 its-my-go-net）"
        Push-Location $rag
        try {
            docker compose -f docker-compose.yml -f docker-compose.itsmygo.yml up -d --build
            Wait-Agent $rag
            docker compose up -d --force-recreate agent-key-init
            docker compose wait agent-key-init
        } finally {
            Pop-Location
        }
        Show-JointSummary
    }
    "joint-down" {
        Test-Docker
        $rag = Find-RagHelper
        Write-Host "==> 停止 rag_helper"
        Push-Location $rag
        try { docker compose -f docker-compose.yml -f docker-compose.itsmygo.yml down } finally { Pop-Location }
        Write-Host "==> 停止 ItsMyGo"
        docker compose down
        Write-Host "已停止两套（数据保留）。"
    }
    "joint-logs" {
        Test-Docker
        $rag = Find-RagHelper
        Push-Location $rag
        try { docker compose logs -f agent-grpc } finally { Pop-Location }
    }
    "joint-status" {
        Test-Docker
        $rag = Find-RagHelper
        Write-Host "==> ItsMyGo"
        docker compose ps
        Write-Host ""
        Write-Host "==> rag_helper"
        Push-Location $rag
        try { docker compose ps } finally { Pop-Location }
    }
    "down" {
        Test-Docker
        docker compose down
        Write-Host "已停止（数据保留）。"
    }
    "logs" { Test-Docker; docker compose logs -f backend }
    "status" { Test-Docker; docker compose ps }
    "seed" {
        Test-Docker
        Ensure-Env
        docker compose --profile seed run --rm seed
    }
    "reset" {
        Test-Docker
        $answer = Read-Host "会删除所有数据卷，输入 yes 继续"
        if ($answer -ne "yes") { Write-Host "已取消。"; exit 1 }
        docker compose down -v
        Write-Host "已删除容器与数据卷。"
    }
    default { Show-Usage }
}
