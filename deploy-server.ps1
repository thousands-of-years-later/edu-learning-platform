# 本地一键部署：打包源码 -> 传服务器 -> 远程重建 backend
# 用法：在项目根目录运行  .\deploy-server.ps1
$ErrorActionPreference = "Stop"
$ssh = "C:\Windows\System32\OpenSSH\ssh.exe"
$scp = "C:\Windows\System32\OpenSSH\scp.exe"
$server = "root@43.142.9.214"
$port = "22022"
$remoteDir = "/www/wwwroot/edu-smart-learning"
$pkg = Join-Path $env:TEMP "edu-deploy.tgz"

Write-Host "[1/3] 打包源码..."
tar --exclude=target --exclude=.git --exclude=deploy/.env --exclude=deploy/init -czf $pkg .

Write-Host "[2/3] 上传到服务器..."
& $scp -P $port $pkg "${server}:/tmp/edu-deploy.tgz"

Write-Host "[3/3] 远程重建 backend..."
& $ssh -p $port $server "cd $remoteDir && tar -xzf /tmp/edu-deploy.tgz && cd deploy && docker compose up -d --build backend && rm -f /tmp/edu-deploy.tgz && echo DEPLOY_DONE"

Write-Host "完成。"
