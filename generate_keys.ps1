param(
    [Parameter(Mandatory=$true)]
    [string]$BackendUrl,
    [int]$Count = 100,
    [string]$KeyType = "lifetime"
)

$headers = @{"X-Client-Version" = "1.0.0"}
$allKeys = @()
$batchSize = 100

for ($i = 0; $i -lt $Count; $i += $batchSize) {
    $batch = [Math]::Min($batchSize, $Count - $i)
    $body = @{type = $KeyType; count = $batch} | ConvertTo-Json

    try {
        $result = Invoke-RestMethod -Uri "$BackendUrl/dev/keys/create" `
            -Method Post -Body $body -ContentType "application/json" `
            -Headers $headers -UseBasicParsing
        if ($result.success) {
            $keys = $result.data.keys
            foreach ($k in $keys) {
                $allKeys += [PSCustomObject]@{
                    Key = $k
                    Type = $KeyType
                    Created = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
                }
            }
            Write-Host "Generated $($allKeys.Count) / $Count keys..."
        }
    }
    catch {
        Write-Error "Error: $_"
    }
}

$csvPath = "nightshift_keys_$(Get-Date -Format 'yyyyMMdd_HHmmss').csv"
$allKeys | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8
Write-Host "Done! Generated $($allKeys.Count) keys -> $csvPath"
