param(
    [string]$ApkPath = 'app\build\outputs\apk\release\app-release.apk',
    [string]$PreviousApk = '',
    [string]$SdkRoot = $(if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME })
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $projectRoot
if (-not $SdkRoot) { throw 'Set ANDROID_HOME / ANDROID_SDK_ROOT, or pass -SdkRoot' }
$identity = Get-Content -Raw -Encoding UTF8 -LiteralPath 'release-identity.json' | ConvertFrom-Json
$buildTools = Join-Path $SdkRoot 'build-tools\36.0.0'
function Read-ApkIdentity([string]$path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "APK not found: $path" }
    $badging = & (Join-Path $buildTools 'aapt.exe') dump badging $path
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read APK identity' }
    $package = [regex]::Match(($badging -join "`n"), "package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'")
    if (-not $package.Success) { throw 'APK identity is incomplete' }
    $certificate = & (Join-Path $buildTools 'apksigner.bat') verify --print-certs $path
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
    $signers = [regex]::Matches(($certificate -join "`n"), 'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})')
    if ($signers.Count -ne 1) { throw 'Expected exactly one release signer' }
    return [pscustomobject]@{applicationId=$package.Groups[1].Value;versionCode=[int]$package.Groups[2].Value;versionName=$package.Groups[3].Value;signerSha256=$signers[0].Groups[1].Value.ToLower()}
}
$current = Read-ApkIdentity $ApkPath
if ($current.applicationId -ne $identity.applicationId -or $current.signerSha256 -ne $identity.signerSha256) { throw 'APK package or signer does not match the pinned release identity' }
$version = [regex]::Match((Get-Content -Raw -Encoding UTF8 -LiteralPath 'gradle.properties'), '(?m)^dolphinVersion=(\d+\.\d+\.\d+)\s*$').Groups[1].Value
$parts = $version.Split('.') | ForEach-Object { [int]$_ }
if ($parts.Count -ne 3 -or $parts[1] -gt 99 -or $parts[2] -gt 99) { throw 'Version exceeds the supported versionCode range' }
$expectedCode = $parts[0]*10000 + $parts[1]*100 + $parts[2]
if ($current.versionName -ne $version -or $current.versionCode -ne $expectedCode) { throw 'APK version does not match source metadata' }
$previous = $null
if ($PreviousApk) {
    $previous = Read-ApkIdentity $PreviousApk
    if ($current.applicationId -ne $previous.applicationId -or $current.signerSha256 -ne $previous.signerSha256 -or $current.versionCode -le $previous.versionCode) { throw 'Upgrade requires the same package and signer with an increased versionCode' }
}
New-Item -ItemType Directory -Force -Path 'build\evidence' | Out-Null
$record = [ordered]@{current=$current;previous=$previous;identityPinned=$true;signatureVerified=$true;completed=$true}
$json = $record | ConvertTo-Json -Depth 5
[System.IO.File]::WriteAllText((Join-Path (Get-Location) "build\evidence\$version-release-identity.json"),$json,[System.Text.UTF8Encoding]::new($false))
"PASS release package, pinned signer and version identity: $($current.applicationId) $version ($expectedCode)"
