# Imports activation keys from keys.txt into the running backend.
# Parses sections by header, calls /dev/keys/create per key with the right `days`.

$ErrorActionPreference = "Stop"
$keysFile = Join-Path $PSScriptRoot "keys.txt"
$backend = "http://127.0.0.1:8080"

if (-not (Test-Path $keysFile)) {
    Write-Host "Keys file not found: $keysFile" -ForegroundColor Red
    exit 1
}

$lines = Get-Content $keysFile -Encoding UTF8
$currentDays = $null
$imported = 0
$skipped = 0
$failed = 0

foreach ($raw in $lines) {
    $line = $raw.Trim()
    if ($line -eq "") { continue }

    # Section header: "200 keys × 15 days" or "Personal (15 days): KEYVALUE"
    if ($line -match "(\d+)\s*keys.*?(\d+)\s*days") {
        $currentDays = [int]$matches[2]
        Write-Host "`n--- Section: $currentDays days ---" -ForegroundColor Cyan
        continue
    }
    if ($line -match "Personal\s*\((\d+)\s*days?\)\s*:\s*([A-Z0-9]+)") {
        $days = [int]$matches[1]
        $value = $matches[2]
        $body = @{ keyValue = $value; days = $days; count = 1 } | ConvertTo-Json -Compress
        try {
            $r = Invoke-RestMethod -Uri "$backend/dev/keys/create" -Method Post -Body $body -ContentType "application/json" -Headers @{"X-Client-Version"="1.0.0"} -TimeoutSec 10
            if ($r.success) { $imported++; Write-Host "  + $value ($days d) [Personal]" -ForegroundColor Green }
            else { $failed++; Write-Host "  ! $value : $($r.error.message)" -ForegroundColor Yellow }
        } catch {
            $failed++
            Write-Host "  ! $value : $_" -ForegroundColor Red
        }
        continue
    }

    # Skip non-key lines (separators, header lines).
    if ($line -match "^[=\-]") { continue }
    if ($line -match "^NightShift|^Generated") { continue }

    # Pure key line.
    if ($line -match "^[A-Z0-9]{16}$" -and $currentDays -ne $null) {
        $body = @{ keyValue = $line; days = $currentDays; count = 1 } | ConvertTo-Json -Compress
        try {
            $r = Invoke-RestMethod -Uri "$backend/dev/keys/create" -Method Post -Body $body -ContentType "application/json" -Headers @{"X-Client-Version"="1.0.0"} -TimeoutSec 10
            if ($r.success) { $imported++ }
            else { $skipped++ }
        } catch {
            # Likely a duplicate (already imported)
            $skipped++
        }
        if (($imported + $skipped) % 50 -eq 0) {
            Write-Host "  ... $imported imported, $skipped skipped" -ForegroundColor DarkGray
        }
    }
}

Write-Host "`nDone. Imported: $imported, Skipped: $skipped, Failed: $failed" -ForegroundColor Cyan
