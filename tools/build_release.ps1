param(
    [switch]$InitializeSigningKey,
    [string]$SigningDirectory = (Join-Path $env:USERPROFILE '.android/skywallpaper-signing')
)

$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskSigningRoot = [IO.Path]::GetFullPath($SigningDirectory)
if ($taskSigningRoot.Equals($taskRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $taskSigningRoot.StartsWith($taskRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Keep the signing directory outside the repository.'
}
if (-not $IsWindows) { throw 'This helper uses Windows DPAPI. Other platforms can use the documented signing environment variables.' }
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 21 before building.' }
$taskKeytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
$taskSdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$taskSigner = Join-Path $taskSdk 'build-tools/36.0.0/apksigner.bat'
if (-not (Test-Path -LiteralPath $taskKeytool) -or -not (Test-Path -LiteralPath $taskSigner)) { throw 'JDK keytool and Android build-tools 36.0.0 are required.' }
$taskKeystore = Join-Path $taskSigningRoot 'release.p12'
$taskPasswordFile = Join-Path $taskSigningRoot 'password.dpapi'
$taskAlias = 'skywallpaper-release'
Add-Type -AssemblyName System.Security.Cryptography.ProtectedData

if ($InitializeSigningKey) {
    if ((Test-Path -LiteralPath $taskKeystore) -or (Test-Path -LiteralPath $taskPasswordFile)) {
        throw 'Signing material already exists. Reuse or recover it; never replace it for an update.'
    }
    New-Item -ItemType Directory -Path $taskSigningRoot -Force | Out-Null
    $taskIdentity = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $taskAcl = [Security.AccessControl.DirectorySecurity]::new()
    $taskAcl.SetAccessRuleProtection($true, $false)
    foreach ($taskSid in @($taskIdentity, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $taskAcl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
            $taskSid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
    }
    Set-Acl -LiteralPath $taskSigningRoot -AclObject $taskAcl
    $taskRandom = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
    $taskPassword = [Convert]::ToBase64String($taskRandom)
    $taskProtected = [Security.Cryptography.ProtectedData]::Protect(
        [Text.Encoding]::UTF8.GetBytes($taskPassword), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    [IO.File]::WriteAllBytes($taskPasswordFile, $taskProtected)
} else {
    if (-not (Test-Path -LiteralPath $taskKeystore) -or -not (Test-Path -LiteralPath $taskPasswordFile)) {
        throw 'Signing material is missing. Use -InitializeSigningKey only for a new application, or restore the existing signing key.'
    }
    $taskPlain = [Security.Cryptography.ProtectedData]::Unprotect(
        [IO.File]::ReadAllBytes($taskPasswordFile), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    $taskPassword = [Text.Encoding]::UTF8.GetString($taskPlain)
    [Array]::Clear($taskPlain)
}

$taskEnvironment = @{}
foreach ($taskName in @('SKYWALLPAPER_KEYSTORE_FILE', 'SKYWALLPAPER_STORE_PASSWORD', 'SKYWALLPAPER_KEY_ALIAS', 'SKYWALLPAPER_KEY_PASSWORD')) {
    $taskEnvironment[$taskName] = [Environment]::GetEnvironmentVariable($taskName, 'Process')
}
try {
    $env:SKYWALLPAPER_KEYSTORE_FILE = $taskKeystore
    $env:SKYWALLPAPER_STORE_PASSWORD = $taskPassword
    $env:SKYWALLPAPER_KEY_ALIAS = $taskAlias
    $env:SKYWALLPAPER_KEY_PASSWORD = $taskPassword
    if ($InitializeSigningKey) {
        & $taskKeytool -genkeypair -keystore $taskKeystore -storetype PKCS12 -storepass:env SKYWALLPAPER_STORE_PASSWORD -keypass:env SKYWALLPAPER_KEY_PASSWORD -alias $taskAlias -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 -dname 'CN=Sky Wallpaper, O=RiversLee9312'
        if ($LASTEXITCODE -ne 0) { throw 'Signing key generation failed; recover the local signing material before retrying.' }
    }
    Push-Location $taskRoot
    try {
        & (Join-Path $taskRoot 'gradlew.bat') :app:assembleRelease :app:testDebugUnitTest :app:lintRelease --console=plain --no-configuration-cache --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Release build or verification failed.' }
    } finally { Pop-Location }
    $taskApk = Join-Path $taskRoot 'app/build/outputs/apk/release/app-release.apk'
    if (-not (Test-Path -LiteralPath $taskApk)) { throw 'The signed release APK was not generated.' }
    & $taskSigner verify --verbose --print-certs $taskApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $taskMetadata = Get-Content -LiteralPath (Join-Path (Split-Path -Parent $taskApk) 'output-metadata.json') -Raw | ConvertFrom-Json
    $taskVersion = $taskMetadata.elements[0].versionName
    $taskOutput = Join-Path $taskRoot "app/build/outputs/releases/v$taskVersion"
    New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
    $taskFilename = "SkyWallpaper-v$taskVersion.apk"
    $taskAsset = Join-Path $taskOutput $taskFilename
    Copy-Item -LiteralPath $taskApk -Destination $taskAsset -Force
    $taskChecksum = (Get-FileHash -LiteralPath $taskAsset -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText((Join-Path $taskOutput 'SHA256SUMS'), "$taskChecksum  $taskFilename`n", [Text.UTF8Encoding]::new($false))
    Write-Output "Release APK: $taskAsset"
    Write-Output "SHA-256: $taskChecksum"
} finally {
    foreach ($taskName in $taskEnvironment.Keys) { [Environment]::SetEnvironmentVariable($taskName, $taskEnvironment[$taskName], 'Process') }
    $taskPassword = $null
}
