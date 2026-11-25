# Starts the ERENO backend (api/) and the ERENO UI (app/, port 5173).
# Usage, from the repository root:
#   ./dev.ps1                  backend on 8080, rebuilt only when its sources changed
#   ./dev.ps1 -ApiPort 8081    use another port when 8080 is taken (e.g. by XAMPP/Apache)
#   ./dev.ps1 -Rebuild         force a backend rebuild
# Ctrl+C stops both.
param(
    [int]$ApiPort = $(if ($env:ERENO_API_PORT) { [int]$env:ERENO_API_PORT } else { 8080 }),
    [switch]$Rebuild
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$api = Join-Path $root 'api'
$app = Join-Path $root 'app'
$jar = Join-Path $api 'target\ERENO-1.0-SNAPSHOT-uber.jar'

function Test-JarStale {
    if (-not (Test-Path $jar)) { return $true }
    $jarTime = (Get-Item $jar).LastWriteTime
    $newest = Get-ChildItem -Path (Join-Path $api 'src\main'), (Join-Path $api 'pom.xml') -Recurse -File |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    return $newest.LastWriteTime -gt $jarTime
}

$inUse = Get-NetTCPConnection -LocalPort $ApiPort -State Listen -ErrorAction SilentlyContinue
if ($inUse) {
    $owner = (Get-Process -Id $inUse[0].OwningProcess -ErrorAction SilentlyContinue).ProcessName
    throw "Port $ApiPort is already in use by '$owner'. Pick another one: ./dev.ps1 -ApiPort 8081"
}

if ($Rebuild -or (Test-JarStale)) {
    Write-Host '==> Building the backend (mvn package, tests skipped)...' -ForegroundColor Cyan
    Push-Location $api
    try {
        $mvn = if (Get-Command mvn -ErrorAction SilentlyContinue) { 'mvn' } else { '.\mvnw.cmd' }
        & $mvn -q -B -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "Backend build failed (exit $LASTEXITCODE)" }
    } finally { Pop-Location }
}

if (-not (Test-Path (Join-Path $app 'node_modules'))) {
    Write-Host '==> Installing frontend dependencies (npm install)...' -ForegroundColor Cyan
    Push-Location $app
    try { npm install; if ($LASTEXITCODE -ne 0) { throw 'npm install failed' } } finally { Pop-Location }
}

Write-Host "==> Starting the backend on http://localhost:$ApiPort ..." -ForegroundColor Cyan
$backend = Start-Process -FilePath 'java' -ArgumentList "-Dereno.api.port=$ApiPort", '-jar', "`"$jar`"", 'serve' `
    -WorkingDirectory $api -NoNewWindow -PassThru

try {
    # Wait until the API answers, so a backend that fails to start does not leave a UI with no server behind it.
    $ready = $false
    for ($i = 0; $i -lt 60 -and -not $backend.HasExited; $i++) {
        try {
            Invoke-RestMethod -Uri "http://127.0.0.1:$ApiPort/api/health" -TimeoutSec 2 | Out-Null
            $ready = $true
            break
        } catch { Start-Sleep -Milliseconds 500 }
    }
    if (-not $ready) { throw 'The backend did not start; see the messages above.' }

    Write-Host '==> Starting the UI on http://localhost:5173 (Ctrl+C stops both)' -ForegroundColor Cyan
    $env:VITE_API_URL = "http://localhost:$ApiPort"
    Push-Location $app
    npm run dev
} finally {
    Pop-Location -ErrorAction SilentlyContinue
    if ($backend -and -not $backend.HasExited) {
        Write-Host '==> Stopping the backend...' -ForegroundColor Cyan
        Stop-Process -Id $backend.Id -Force
    }
}
