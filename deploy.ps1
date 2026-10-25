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

    if (-not (Get-EnvValue "MINIO_PUBLIC_ENDPOINT")) {
        $minioPort = Get-EnvValue "MINIO_PORT"
        if (-not $minioPort) { $minioPort = "9000" }
        Set-EnvValue "MINIO_PUBLIC_ENDPOINT" "http://localhost:$minioPort"
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

switch ($Command.ToLower()) {
    "up" {
        Test-Docker
        Ensure-Env
        docker compose up -d --build
        Wait-Backend
        Import-SeedIfEmpty
        Show-Summary
    }
    "down" {
        Test-Docker
        docker compose down
        Write-Host "已停止（数据保留）。"
    }
    "logs" { docker compose logs -f backend }
    "status" { docker compose ps }
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
