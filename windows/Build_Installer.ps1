# STORM GBCA - Automated Production Windows & Installer Build Pipeline (33273a6b STORM INSTALLER Standard)
$ErrorActionPreference = "Stop"
[System.Diagnostics.Process]::GetCurrentProcess().PriorityClass = [System.Diagnostics.ProcessPriorityClass]::BelowNormal

$scriptDir = $PSScriptRoot
if (-not $scriptDir) { $scriptDir = "f:\STORM GBCA\windows" }
$repoRoot = Split-Path -Parent $scriptDir
$desktopProj = Join-Path $scriptDir "src\StormGbca.Desktop\StormGbca.Desktop.csproj"
$installerProjDir = Join-Path $scriptDir "installer"
$installerProj = Join-Path $installerProjDir "StormGbca.Installer.csproj"
$publishDir = Join-Path $scriptDir "src\StormGbca.Desktop\publish\win-x64"
$filesDir = Join-Path $repoRoot "Files"
$releasesDir = Join-Path $repoRoot "releases"

if (-not (Test-Path $filesDir)) { New-Item -ItemType Directory -Path $filesDir | Out-Null }
if (-not (Test-Path $releasesDir)) { New-Item -ItemType Directory -Path $releasesDir | Out-Null }

$appVersion = "1.8.0"
try {
    [xml]$appProjXml = Get-Content $desktopProj
    $verFromProj = ($appProjXml.Project.PropertyGroup | ForEach-Object { $_.Version } | Where-Object { $_ } | Select-Object -First 1)
    if ($verFromProj) { $appVersion = [string]$verFromProj }
} catch { }

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "   STORM GBCA $appVersion - STORM INSTALLER PIPELINE" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$setupExeName = "STORM_GBCA_${appVersion}_Setup.exe"
$setupExeFiles = Join-Path $filesDir $setupExeName
$setupExeReleases = Join-Path $releasesDir $setupExeName
$portableZipFiles = Join-Path $filesDir "STORM_GBCA_${appVersion}_Windows.zip"
$portableZipReleases = Join-Path $releasesDir "STORM_GBCA_${appVersion}_Windows.zip"

# Step 0: Close running instances
Write-Host "[0/5] Closing running STORM GBCA / StormInstaller instances..." -ForegroundColor Yellow
Get-Process "STORM GBCA", "StormGbca", "StormInstaller" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 500

# Step 1: Publish Windows Desktop App (Low-CPU -m:4)
Write-Host "[1/5] Publishing StormGbca.Desktop (win-x64 self-contained)..." -ForegroundColor Yellow
if (Test-Path $publishDir) { Remove-Item $publishDir -Recurse -Force -ErrorAction SilentlyContinue }
dotnet publish $desktopProj -c Release -r win-x64 --self-contained true -o $publishDir -m:4 /nr:false
if ($LASTEXITCODE -ne 0) { throw "dotnet publish StormGbca.Desktop failed (rc=$LASTEXITCODE)" }

$desktopExe = Join-Path $publishDir "STORM GBCA.exe"
if (-not (Test-Path $desktopExe)) {
    throw "Error: STORM GBCA.exe was not created in $publishDir!"
}

# Step 2: Locate STORM TEAM Master Certificate & signtool
Write-Host "[2/5] Signing desktop binaries with STORM TEAM Master Certificate..." -ForegroundColor Yellow
$signtool = (Get-ChildItem "C:\Program Files (x86)\Windows Kits\10\bin\*\x64\signtool.exe" -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName)
if (-not $signtool -or -not (Test-Path $signtool)) {
    $signtool = "C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64\signtool.exe"
}

# Pick the exact trusted STORM TEAM cert (matching STORM SWITCH BOX)
$cert = $null
$trustedThumb = $null
try {
    $refExe = "F:\STORM SWITCH BOX\Files\STORM_SWITCH_BOX_8.2.6_Setup.exe"
    if (Test-Path $refExe) {
        $refSig = Get-AuthenticodeSignature $refExe
        if ($refSig.SignerCertificate) { $trustedThumb = $refSig.SignerCertificate.Thumbprint }
    }
} catch { }

foreach ($loc in @('CurrentUser', 'LocalMachine')) {
    if ($cert) { break }
    try {
        $store = [System.Security.Cryptography.X509Certificates.X509Store]::new('My', $loc)
        $store.Open('ReadOnly')
        if ($trustedThumb) {
            $cert = $store.Certificates | Where-Object { $_.HasPrivateKey -and $_.Thumbprint -eq $trustedThumb } | Select-Object -First 1
        }
        if (-not $cert) {
            $cert = $store.Certificates | Where-Object { $_.HasPrivateKey -and $_.Subject -like "*CN=STORM TEAM*" } | Select-Object -First 1
        }
        $store.Close()
    } catch { }
}
if (-not $cert) {
    throw "CRITICAL ERROR: STORM TEAM Master Certificate (CN=STORM TEAM) not found in certificate store!"
}
$certThumb = $cert.Thumbprint
Write-Host "  -> Master Certificate: $($cert.Subject) [$certThumb]" -ForegroundColor Green

# Export public CER (never PFX) into Files and installer folder for embedding
$cerFiles = Join-Path $filesDir "STORM_Certificate.cer"
$cerInstaller = Join-Path $installerProjDir "STORM_Certificate.cer"
[System.IO.File]::WriteAllBytes($cerFiles, $cert.Export([System.Security.Cryptography.X509Certificates.X509ContentType]::Cert))
Copy-Item $cerFiles $cerInstaller -Force
Copy-Item $cerFiles (Join-Path $publishDir "STORM_Certificate.cer") -Force

function Invoke-StormSign([string]$FilePath) {
    $tsUrls = @(
        "http://timestamp.digicert.com",
        "http://timestamp.sectigo.com",
        "http://tsa.starfieldtech.com",
        "http://timestamp.entrust.net/TSS/RFC3161sha2TS"
    )
    $signed = $false
    foreach ($ts in $tsUrls) {
        for ($attempt = 1; $attempt -le 2; $attempt++) {
            if (Test-Path $signtool) {
                & $signtool sign /fd SHA256 /tr $ts /td SHA256 /d "STORM GBCA" /du "https://github.com/ReiKatari/STORM_GBCA" /sha1 $certThumb $FilePath
                if ($LASTEXITCODE -eq 0) { $signed = $true; break }
            } else {
                $res = Set-AuthenticodeSignature -FilePath $FilePath -Certificate $cert -HashAlgorithm SHA256 -TimestampServer $ts
                if ($res.Status -eq [System.Management.Automation.SignatureStatus]::Valid) { $signed = $true; break }
            }
            Start-Sleep -Milliseconds 600
        }
        if ($signed) { break }
    }
    if (-not $signed) {
        Write-Warning "Timestamp signing failed for $FilePath, signing without timestamp as fallback..."
        if (Test-Path $signtool) {
            & $signtool sign /fd SHA256 /d "STORM GBCA" /du "https://github.com/ReiKatari/STORM_GBCA" /sha1 $certThumb $FilePath
        } else {
            Set-AuthenticodeSignature -FilePath $FilePath -Certificate $cert -HashAlgorithm SHA256 | Out-Null
        }
    }
}

Get-ChildItem $publishDir -Recurse | Unblock-File -ErrorAction SilentlyContinue
Invoke-StormSign $desktopExe
if (Test-Path (Join-Path $publishDir "STORM GBCA.dll")) { Invoke-StormSign (Join-Path $publishDir "STORM GBCA.dll") }
if (Test-Path (Join-Path $publishDir "storm_gbca_core.dll")) { Invoke-StormSign (Join-Path $publishDir "storm_gbca_core.dll") }

# Step 3: Package Portable ZIP into Files/ and releases/
Write-Host "[3/5] Packaging Portable ZIP..." -ForegroundColor Yellow
if (Test-Path $portableZipFiles) { Remove-Item $portableZipFiles -Force }
$sevenZip = @("F:\STORM SWITCH BOX\tools\7z.exe", "C:\Program Files\7-Zip\7z.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($sevenZip) {
    & $sevenZip a -tzip -mx=7 -mmt=2 $portableZipFiles "$publishDir\*"
    if ($LASTEXITCODE -ne 0) { throw "7z packaging failed (rc=$LASTEXITCODE)" }
} else {
    Compress-Archive -Path "$publishDir\*" -DestinationPath $portableZipFiles -Force
}
Copy-Item $portableZipFiles $portableZipReleases -Force

# Step 4: Publish Single-File WinForms StormInstaller (33273a6b Cyber Dark UI)
Write-Host "[4/5] Building StormInstaller (Cyber Dark 840x580 UI, 11 languages, 9 themes)..." -ForegroundColor Yellow
if (Test-Path (Join-Path $installerProjDir "bin")) { Remove-Item (Join-Path $installerProjDir "bin") -Recurse -Force -ErrorAction SilentlyContinue }
if (Test-Path (Join-Path $installerProjDir "obj")) { Remove-Item (Join-Path $installerProjDir "obj") -Recurse -Force -ErrorAction SilentlyContinue }

$env:STORM_CERT_PATH = $cerFiles
dotnet publish $installerProj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:EnableCompressionInSingleFile=true -m:4 /nr:false
if ($LASTEXITCODE -ne 0) { throw "dotnet publish StormGbca.Installer failed (rc=$LASTEXITCODE)" }

$publishedInstaller = Join-Path $installerProjDir "bin\Release\net10.0-windows\win-x64\publish\StormInstaller.exe"
if (-not (Test-Path $publishedInstaller)) {
    throw "Error: StormInstaller.exe was not created at $publishedInstaller!"
}

Copy-Item $publishedInstaller $setupExeFiles -Force
Copy-Item $publishedInstaller $setupExeReleases -Force

# Step 5: Sign Installers & Verify Signature + Clean Temp CER from installer source folder
Write-Host "[5/5] Signing Setup.exe & verifying Authenticode signature..." -ForegroundColor Yellow
Invoke-StormSign $setupExeFiles
Invoke-StormSign $setupExeReleases
Unblock-File $setupExeFiles -ErrorAction SilentlyContinue
Unblock-File $setupExeReleases -ErrorAction SilentlyContinue
if (Test-Path $cerInstaller) { Remove-Item $cerInstaller -Force -ErrorAction SilentlyContinue }

$sig = Get-AuthenticodeSignature $setupExeFiles
Write-Host "  -> Signature Status: $($sig.Status) | Signer: $($sig.SignerCertificate.Subject)" -ForegroundColor Green
Write-Host "  -> Installer (Files):    $setupExeFiles ($((Get-Item $setupExeFiles).Length) bytes)" -ForegroundColor Green
Write-Host "  -> Installer (Releases): $setupExeReleases" -ForegroundColor Green
Write-Host "  -> Portable ZIP:         $portableZipFiles ($((Get-Item $portableZipFiles).Length) bytes)" -ForegroundColor Green
if ($sig.Status -ne [System.Management.Automation.SignatureStatus]::Valid) {
    throw "Signature verification failed: $($sig.Status)"
}
