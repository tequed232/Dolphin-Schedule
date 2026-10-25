param([string[]]$Task = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Push-Location -LiteralPath $projectRoot
try { & '.\gradlew.bat' @Task --console=plain; exit $LASTEXITCODE } finally { Pop-Location }
