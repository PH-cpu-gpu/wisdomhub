<#
.SYNOPSIS
    将 docker/nacos/config/*.example 批量导入本地 Nacos（namespace: wisdomhub）。

.DESCRIPTION
    登录 Nacos 3 OpenAPI 后，逐个发布配置。DataId 由文件名去掉 .example 得到。
    导入前请先把模板中的 change-me / your-xxx 占位符替换为实际值。

.EXAMPLE
    $env:NACOS_PASSWORD = 'your-password'
    pwsh -File docker/nacos/import.ps1

.EXAMPLE
    pwsh -File docker/nacos/import.ps1 -ServerAddr 127.0.0.1:8848 -Namespace wisdomhub -Password 'your-password'
#>
param(
    [string]$ServerAddr = '127.0.0.1:8848',
    [string]$Namespace  = 'wisdomhub',
    [string]$Group      = 'DEFAULT_GROUP',
    [string]$Username   = 'nacos',
    [string]$Password   = $env:NACOS_PASSWORD
)

$ErrorActionPreference = 'Stop'

if (-not $Password) {
    throw '未提供 Nacos 密码：请设置环境变量 NACOS_PASSWORD，或使用 -Password 参数。'
}

$baseUrl = "http://$ServerAddr/nacos"
$configDir = Join-Path $PSScriptRoot 'config'
if (-not (Test-Path $configDir)) { throw "配置目录不存在：$configDir" }

# 1. 登录
$login = Invoke-RestMethod -Uri "$baseUrl/v3/auth/user/login" -Method Post `
    -Body @{ username = $Username; password = $Password } -TimeoutSec 10
if (-not $login.accessToken) { throw '登录失败：未返回 accessToken。' }
$headers = @{ accessToken = $login.accessToken }
Write-Output "登录成功：$($login.username)（globalAdmin=$($login.globalAdmin)）"

# 2. 逐个发布
$files = Get-ChildItem $configDir -Filter '*.example' | Sort-Object Name
$ok = 0
foreach ($f in $files) {
    $dataId = $f.Name.Substring(0, $f.Name.Length - '.example'.Length)
    $content = [IO.File]::ReadAllText($f.FullName, [Text.UTF8Encoding]::new($false))
    $body = @{
        dataId      = $dataId
        groupName   = $Group
        namespaceId = $Namespace
        type        = 'yaml'
        content     = $content
    }
    try {
        Invoke-RestMethod -Uri "$baseUrl/v3/admin/cs/config" -Method Post -Headers $headers -Body $body -TimeoutSec 10 | Out-Null
        Write-Output "  已发布 $dataId"
        $ok++
    } catch {
        Write-Output "  失败   $dataId -> $($_.Exception.Message)"
    }
}
Write-Output "完成：$ok / $($files.Count) 个配置已导入到命名空间 $Namespace。"
