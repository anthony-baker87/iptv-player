param([switch]$SkipChecks, [switch]$Release)
$ErrorActionPreference = 'Stop'
$toolRoot = Join-Path $env:LOCALAPPDATA 'IPTVDesktopBuild'
if (-not $env:JAVA_HOME) {
    $localJdk = Get-ChildItem (Join-Path $toolRoot 'jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
}
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    throw 'Set JAVA_HOME to a JDK 17 installation (Android Studio includes a compatible JDK).'
}
if (-not $env:ANDROID_HOME) {
    $localSdk = Join-Path $toolRoot 'android-sdk'
    if (Test-Path $localSdk) { $env:ANDROID_HOME = $localSdk }
    elseif (Test-Path (Join-Path $env:LOCALAPPDATA 'Android/Sdk')) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
}
if (-not $env:ANDROID_HOME) { throw 'Install Android SDK platform 35/build-tools 35.0.0, and set ANDROID_HOME.' }
$localProperties = Join-Path $PSScriptRoot 'local.properties'
[IO.File]::WriteAllText($localProperties, ('sdk.dir=' + $env:ANDROID_HOME.Replace('\','/') + "`n"))
Push-Location $PSScriptRoot
try {
    if ($Release -and -not $env:FIRETV_KEYSTORE_PATH) {
        $env:FIRETV_KEYSTORE_PATH = Join-Path $env:USERPROFILE '.android/debug.keystore'
        if (-not (Test-Path -LiteralPath $env:FIRETV_KEYSTORE_PATH)) { throw 'The original Fire TV signing key is required for a release build.' }
        $env:FIRETV_STORE_PASSWORD = 'android'
        $env:FIRETV_KEY_ALIAS = 'androiddebugkey'
        $env:FIRETV_KEY_PASSWORD = 'android'
    }
    $variant = if ($Release) { 'Release' } else { 'Debug' }
    $tasks = @((':app:assemble' + $variant), '--console=plain')
    if (-not $SkipChecks) { $tasks = @((':app:assemble' + $variant), ':app:testDebugUnitTest', ':app:lintDebug', '--console=plain') }
    & (Join-Path $PSScriptRoot 'gradlew.bat') @tasks
    if ($LASTEXITCODE -ne 0) { throw 'Fire TV build/checks failed.' }
    $releaseDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) 'release'
    New-Item -ItemType Directory -Force -Path $releaseDirectory | Out-Null
    $version = if ($env:FIRETV_VERSION_NAME) { $env:FIRETV_VERSION_NAME } else { '1.1.0' }
    $outputApk = Join-Path $releaseDirectory ('IPTV-FireTV-' + $version + '.apk')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot ('app/build/outputs/apk/' + $variant.ToLowerInvariant() + '/app-' + $variant.ToLowerInvariant() + '.apk')) -Destination $outputApk -Force
    Get-Item -LiteralPath $outputApk | Select-Object FullName,Length
} finally { Pop-Location }
