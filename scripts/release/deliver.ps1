param([string]$OutputRoot = '', [string]$PreviousApk = '', [string]$ApkPath = 'app/build/outputs/apk/release/app-release.apk')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$meta = Get-Content -Raw -LiteralPath 'ui/src/meta.ts'
$version = [regex]::Match($meta, "APP_VERSION\s*=\s*'([^']+)'").Groups[1].Value
if (-not $version) { throw '未找到版本号' }
if (-not $OutputRoot) { $OutputRoot = Join-Path $projectRoot 'releases' }
$destination = Join-Path $OutputRoot "Dolphin-Schedule-$version"
if (Test-Path -LiteralPath $destination) { throw '交付目录已存在，请选择新目录，避免混入旧产物' }
if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) { throw '请先构建签名 release APK' }
& (Join-Path $PSScriptRoot 'check-release.ps1') -ApkPath $ApkPath -PreviousApk $PreviousApk
$apkName = "dolphin-schedule-$version.apk"
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$outputApk = Join-Path $destination $apkName
Copy-Item -LiteralPath $ApkPath -Destination $outputApk
$hash = (Get-FileHash -LiteralPath $outputApk -Algorithm SHA256).Hash.ToLower()
[System.IO.File]::WriteAllText((Join-Path $destination 'SHA256SUMS.txt'), "$hash  $apkName`n", [System.Text.UTF8Encoding]::new($false))
Get-ChildItem -LiteralPath $destination -File | Select-Object Name,Length
