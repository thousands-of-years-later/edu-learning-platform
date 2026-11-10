# 本地启动开发环境（端口避让本机 MySQL 80 服务）
# 用法：在项目根目录运行  .\deploy\start-local.ps1
#   本机 3306 已被 MySQL80 服务占用，容器改用 3307
#   后端 http://localhost:8080  数据库 localhost:3307
$ErrorActionPreference = "Stop"
$env:MYSQL_HOST_PORT = "3307"
$env:REDIS_HOST_PORT = "6379"
$env:BACKEND_HOST_PORT = "8080"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

$composeArgs = @("-p", "edu-local", "-f", "deploy/docker-compose.yml", "-f", "deploy/docker-compose.local.yml")

Write-Host "[1/3] 启动 MySQL（首次会用 deploy/init 自动建表）..."
docker-compose @composeArgs up -d mysql

Write-Host "[2/3] 等待 MySQL 就绪..."
$ready = $false
for ($i = 0; $i -lt 40; $i++) {
    $log = docker logs edu-mysql 2>&1 | Out-String
    if ($log -match "ready for connections") { $ready = $true; break }
    Start-Sleep -Seconds 3
}
if (-not $ready) {
    Write-Host "[警告] MySQL 60 秒内未输出 ready，请查看 docker logs edu-mysql"
} else {
    Write-Host "  [OK] MySQL 已就绪"
}

Write-Host "[3/3] 启动 Redis 与后端..."
docker-compose @composeArgs up -d

Start-Sleep -Seconds 5
docker ps --filter "name=edu-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

Write-Host ""
Write-Host "后端:   http://localhost:8080"
Write-Host "数据库: localhost:3307  root / 12345678  learning_platform"
Write-Host "如后端日志报 Table doesn't exist，执行: docker restart edu-backend"
