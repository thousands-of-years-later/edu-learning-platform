# 一键发布：提交 -> 推到服务器裸仓库 -> 立即触发部署
# 用法：
#   .\deploy\release.ps1                    只推不部署（等 cron 凌晨跑）
#   .\deploy\release.ps1 -Message "修复评论接口"
#   .\deploy\release.ps1 -Deploy            推送并立刻部署
#   .\deploy\release.ps1 -Message "改动" -Deploy -GitHub    同时备份到 GitHub
param(
    [string]$Message = "",
    [switch]$Deploy,
    [switch]$GitHub
)

$ErrorActionPreference = "Stop"

# ---- 环境参数 ----
$ServerHost = "43.142.9.214"
$ServerPort = "22022"
$RemoteDir  = "/www/wwwroot/edu-smart-learning"
$SshExe     = "C:\Windows\System32\OpenSSH\ssh.exe"

# git 可能不在 PATH 里，按常见位置找
$GitExe = $null
foreach ($candidate in @("git", "D:\Git\cmd\git.exe", "C:\Program Files\Git\cmd\git.exe")) {
    try {
        $cmd = Get-Command $candidate -ErrorAction Stop
        $GitExe = if ($cmd.Source) { $cmd.Source } else { $candidate }
        break
    } catch { }
}
if (-not $GitExe) { throw "找不到 git，请把 git 加入 PATH 或修改脚本里的路径" }

$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $RepoRoot

function Invoke-Git {
    param([string[]]$GitArgs)
    & $GitExe @GitArgs
    if ($LASTEXITCODE -ne 0) { throw "git 执行失败: git $($GitArgs -join ' ')" }
}

# ---- 1. 检查并提交 ----
$status = (& $GitExe status --porcelain) -join ""
if ($status) {
    if (-not $Message) { $Message = "deploy: $(Get-Date -Format 'yyyy-MM-dd HH:mm')" }
    Write-Host "[1/4] 提交改动：$Message"
    Invoke-Git -GitArgs @("add", "-A")
    Invoke-Git -GitArgs @("commit", "-m", $Message)
} else {
    Write-Host "[1/4] 工作区干净，跳过提交"
}

# ---- 2. 推到服务器裸仓库 ----
$head = (& $GitExe rev-parse --short HEAD).Trim()
Write-Host "[2/4] 推送 $head 到服务器裸仓库"
Invoke-Git -GitArgs @("push", "server", "deploy")

# ---- 3. 备份到 GitHub（可选） ----
if ($GitHub) {
    Write-Host "[3/4] 备份到 GitHub"
    & $GitExe -c http.proxy= -c https.proxy= push origin deploy
    if ($LASTEXITCODE -ne 0) { Write-Warning "GitHub 推送失败（网络问题），不影响服务器部署" }
} else {
    Write-Host "[3/4] 跳过 GitHub 备份（加 -GitHub 可开启）"
}

# ---- 4. 触发部署 ----
if ($Deploy) {
    Write-Host "[4/4] 触发服务器部署..."
    & $SshExe -p $ServerPort -o BatchMode=yes -o ConnectTimeout=8 "root@$ServerHost" "bash $RemoteDir/deploy/auto-deploy.sh; echo; echo '--- 最近日志 ---'; tail -12 /var/log/edu-deploy.log"
    if ($LASTEXITCODE -ne 0) { throw "远程部署失败，请查看 /var/log/edu-deploy.log" }
} else {
    Write-Host "[4/4] 已推送，未部署。cron 会在 0:00-8:50 之间自动部署；要立刻发布请加 -Deploy"
}

