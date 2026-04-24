# One-shot build & package script. Reads top-to-bottom; every step corresponds
# to a section in README.md.
#
# Usage (from repo root):
#   powershell -ExecutionPolicy Bypass -File .\package.ps1

$ErrorActionPreference = 'Stop'

$root        = Split-Path -Parent $MyInvocation.MyCommand.Path
$webDir      = Join-Path $root 'web'
$serverDir   = Join-Path $root 'server'
$resources   = Join-Path $serverDir 'src\main\resources'
$staticDir   = Join-Path $resources 'static'
$dataDir     = Join-Path $resources 'data'
$releaseDir  = Join-Path $root 'release'
$appVersion  = '0.0.1'

# ---- Step 1: Build the React frontend -------------------------------------
Write-Host "==> [1/5] npm run build (produces web\dist)" -ForegroundColor Cyan
Push-Location $webDir
try {
    npm install --no-audit --no-fund
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "Frontend build failed." }
} finally {
    Pop-Location
}

# ---- Step 2: Copy frontend dist + GeoJSON into Spring Boot resources ------
Write-Host "==> [2/5] Copy web\dist and data\full.geojson into server resources" -ForegroundColor Cyan
if (Test-Path $staticDir) { Remove-Item $staticDir -Recurse -Force }
New-Item -ItemType Directory -Path $staticDir | Out-Null
Copy-Item (Join-Path $webDir 'dist\*') $staticDir -Recurse

if (-not (Test-Path $dataDir)) { New-Item -ItemType Directory -Path $dataDir | Out-Null }
Copy-Item (Join-Path $root 'data\full.geojson') (Join-Path $dataDir 'full.geojson') -Force

# ---- Step 3: Build the Spring Boot fat jar --------------------------------
Write-Host "==> [3/5] mvn clean package (produces server\target\*.jar)" -ForegroundColor Cyan
Push-Location $serverDir
try {
    & (Join-Path $serverDir 'mvnw.cmd') clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }
} finally {
    Pop-Location
}

$jar = Get-ChildItem (Join-Path $serverDir 'target') -Filter '*.jar' |
       Where-Object { $_.Name -notmatch 'sources|javadoc|original' } |
       Select-Object -First 1
if (-not $jar) { throw "No jar found under server\target." }

# ---- Step 4: jpackage — embed JRE, emit native launcher -------------------
Write-Host "==> [4/5] jpackage (embeds trimmed JRE, emits Pathfinder.exe)" -ForegroundColor Cyan
if (Test-Path $releaseDir) { Remove-Item $releaseDir -Recurse -Force }
$staging = Join-Path $releaseDir 'staging'
New-Item -ItemType Directory -Path $staging | Out-Null
Copy-Item $jar.FullName (Join-Path $staging 'pathfinder.jar')

jpackage `
    --type app-image `
    --name Pathfinder `
    --app-version $appVersion `
    --input $staging `
    --main-jar pathfinder.jar `
    --dest $releaseDir `
    --java-options "-Dspring.profiles.active=prod" `
    --java-options "-Xmx1g"
if ($LASTEXITCODE -ne 0) { throw "jpackage failed." }
Remove-Item $staging -Recurse -Force

# ---- Step 5: Zip for delivery ---------------------------------------------
Write-Host "==> [5/5] Zip release\Pathfinder -> Pathfinder-$appVersion.zip" -ForegroundColor Cyan
$zipPath = Join-Path $releaseDir "Pathfinder-$appVersion.zip"
Compress-Archive -Path (Join-Path $releaseDir 'Pathfinder') -DestinationPath $zipPath -Force

$zipSize = '{0:N1} MB' -f ((Get-Item $zipPath).Length / 1MB)
Write-Host ""
Write-Host "==> Done." -ForegroundColor Green
Write-Host "    Deliverable: $zipPath ($zipSize)"
Write-Host "    Run locally: $(Join-Path $releaseDir 'Pathfinder\Pathfinder.exe')"
