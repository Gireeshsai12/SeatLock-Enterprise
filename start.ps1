$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Test-Path '.env')) {
    $Generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $Entries = foreach ($Name in 'POSTGRES_PASSWORD','RABBITMQ_PASSWORD','JWT_SECRET','ADMIN_PASSWORD') {
            $Bytes = New-Object byte[] 32
            $Generator.GetBytes($Bytes)
            "$Name=$([BitConverter]::ToString($Bytes).Replace('-','').ToLowerInvariant())"
        }
        [IO.File]::WriteAllLines((Join-Path $PSScriptRoot '.env.tmp'), [string[]]$Entries, [Text.Encoding]::ASCII)
        Move-Item .env.tmp .env
    } finally {
        $Generator.Dispose()
    }
}
docker compose up --build -d --wait
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$PortLine = Get-Content '.env' | Where-Object { $_ -match '^WEB_PORT=\d+$' } | Select-Object -First 1
$Port = if ($PortLine) { $PortLine.Split('=')[1] } else { '8080' }
Write-Host "SeatLock is available at http://localhost:${Port}"
Write-Host 'Admin email: admin@seatlock.local. Read ADMIN_PASSWORD from .env.'
