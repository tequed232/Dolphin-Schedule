param([string]$OutputRoot = '', [string]$PreviousApk = '', [string]$ApkPath = 'app/build/outputs/apk/release/app-release.apk')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
$meta = Get-Content -Raw -Encoding UTF8 -LiteralPath 'ui/src/meta.ts'
$version = [regex]::Match($meta, "APP_VERSION\s*=\s*'([^']+)'").Groups[1].Value
if (-not $version) { throw 'Application version was not found' }
if (-not $OutputRoot) { $OutputRoot = Join-Path $projectRoot 'releases' }
$destination = Join-Path $OutputRoot "Dolphin-Schedule-$version"
if (Test-Path -LiteralPath $destination) { throw 'Delivery directory already exists; choose a new output directory' }
if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) { throw 'Build a signed release APK first' }
& (Join-Path $PSScriptRoot 'check-release.ps1') -ApkPath $ApkPath -PreviousApk $PreviousApk
$apkName = "dolphin-schedule-$version.apk"
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$outputApk = Join-Path $destination $apkName
Copy-Item -LiteralPath $ApkPath -Destination $outputApk
$algorithm = [System.Security.Cryptography.SHA256]::Create()
$stream = [System.IO.File]::OpenRead($outputApk)
try { $hash = [System.BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
finally { $stream.Dispose(); $algorithm.Dispose() }
[System.IO.File]::WriteAllText((Join-Path $destination 'SHA256SUMS.txt'), "$hash  $apkName`n", [System.Text.UTF8Encoding]::new($false))
Get-ChildItem -LiteralPath $destination -File | Select-Object Name,Length
