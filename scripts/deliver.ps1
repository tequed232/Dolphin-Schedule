param([string]$OutputRoot = 'D:\Desktop', [string]$PreviousApk = '')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$projectRoot = (Get-Location).Path
$version = [regex]::Match((Get-Content -Raw -LiteralPath 'web\src\meta.ts'), "APP_VERSION\s*=\s*'([^']+)'").Groups[1].Value
if (-not $version) { throw '未找到版本号' }
$changes = @(& git status --porcelain)
if ($LASTEXITCODE -ne 0 -or $changes.Count -gt 0) { throw '请先提交本轮源码与文档；源码压缩包只从已提交的 Git 版本生成' }
$destination = Join-Path $OutputRoot "Dolphin-Calendar-$version"
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$apk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw '请先构建签名 release APK' }
& (Join-Path $PSScriptRoot 'check-release.ps1') -ApkPath $apk -PreviousApk $PreviousApk
Copy-Item -LiteralPath $apk -Destination (Join-Path $destination "dolphin-calendar-$version.apk") -Force
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
function New-FilteredArchive([string]$archivePath, [string]$basePath, [array]$files) {
    $stream = [System.IO.File]::Open($archivePath, [System.IO.FileMode]::Create)
    $archive = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($item in $files) {
            $rootWithSeparator = $basePath.TrimEnd('\','/') + [System.IO.Path]::DirectorySeparatorChar
            if (-not $item.FullName.StartsWith($rootWithSeparator, [System.StringComparison]::OrdinalIgnoreCase)) { throw "源文件超出项目目录：$($item.FullName)" }
            $relative = $item.FullName.Substring($rootWithSeparator.Length).Replace('\','/')
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $item.FullName, $relative, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally { $archive.Dispose(); $stream.Dispose() }
}
$webBase = Join-Path $projectRoot 'web\dist'
New-FilteredArchive (Join-Path $destination "dolphin-calendar-$version-web.zip") $webBase (Get-ChildItem -LiteralPath $webBase -File -Recurse)
$sourceZip = Join-Path $destination "dolphin-calendar-$version-sourcecode.zip"
& git archive --format=zip "--output=$sourceZip" HEAD
if ($LASTEXITCODE -ne 0) { throw '生成已提交源码压缩包失败' }
Copy-Item -LiteralPath 'README.md' -Destination (Join-Path $destination 'README.md') -Force
$hashes = Get-ChildItem -LiteralPath $destination -File | Where-Object Extension -In '.apk','.zip' | Get-FileHash -Algorithm SHA256 | ForEach-Object { "$($_.Hash.ToLower())  $([System.IO.Path]::GetFileName($_.Path))" }
[System.IO.File]::WriteAllLines((Join-Path $destination 'SHA256SUMS.txt'), $hashes, [System.Text.UTF8Encoding]::new($false))
Get-ChildItem -LiteralPath $destination -File | Select-Object Name,Length
