[CmdletBinding()]
param(
    [switch]$ProxyLiveTraffic,
    [switch]$InjectSyntheticUsageRecorded = $true,
    [string]$UserId = "1",
    [string]$ApiKeyId = "4",
    [string]$Provider = "openai"
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$EnvPath = Join-Path $RepoRoot ".env"
if (Test-Path -LiteralPath $EnvPath) {
    . (Join-Path $PSScriptRoot "import-dotenv.ps1")
    Import-DotEnv -Path $EnvPath
}

function Get-OrDefault([string]$name, [string]$fallback) {
    $v = [Environment]::GetEnvironmentVariable($name)
    if ($null -eq $v -or $v.Trim() -eq "") { return $fallback }
    return $v.Trim()
}

$agentPort = Get-OrDefault "AGENT_SERVICE_PORT" "8097"
$agentWebPort = Get-OrDefault "AGENT_WEB_PORT" "3005"
$gatewayPort = Get-OrDefault "API_GATEWAY_PORT" "8080"
$rabbitMgmtPort = Get-OrDefault "RABBITMQ_MANAGEMENT_PORT" "15672"
$rabbitUser = Get-OrDefault "RABBITMQ_USER" "guest"
$rabbitPass = Get-OrDefault "RABBITMQ_PASSWORD" "guest"

function New-BasicAuthHeader([string]$user, [string]$pass) {
    $pair = "{0}:{1}" -f $user, $pass
    $b64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
    return @{ Authorization = "Basic $b64" }
}

function Get-JsonOrThrow([string]$url, [hashtable]$headers = @{}, [int]$timeoutSec = 20) {
    return Invoke-RestMethod -Uri $url -Method Get -Headers $headers -TimeoutSec $timeoutSec
}

function Show-TopEventTypes([array]$events) {
    if (-not $events -or $events.Count -eq 0) {
        Write-Host "  - debug events: empty"
        return
    }
    Write-Host "  - debug events (top):"
    $events |
        ForEach-Object { $_.eventType } |
        Group-Object |
        Sort-Object Count -Descending |
        Select-Object -First 10 |
        ForEach-Object { Write-Host ("    {0}: {1}" -f $_.Name, $_.Count) }
}

function Show-HealthSnapshot([string]$title) {
    Write-Host ""
    Write-Host "=== $title ===" -ForegroundColor Cyan
    $debug = Get-JsonOrThrow "http://localhost:$agentPort/api/v1/agents/debug/events?limit=50"
    $billing = Get-JsonOrThrow "http://localhost:$agentPort/api/v1/agents/billing-signals"
    $ctx = Get-JsonOrThrow "http://localhost:$agentWebPort/agent/api/v1/agents/available-context" @{ "x-user-id" = $UserId }

    Show-TopEventTypes @($debug)
    Write-Host ("  - billing-signals count: {0}" -f @($billing).Count)
    $targetBilling = @($billing | Where-Object { $_.apiKeyId -eq $ApiKeyId } | Select-Object -First 1)
    if ($targetBilling.Count -gt 0) {
        Write-Host ("  - billing key {0}: cost={1}, finalizedAt={2}" -f $ApiKeyId, $targetBilling[0].latestEstimatedCostUsd, $targetBilling[0].latestFinalizedAt)
    } else {
        Write-Host ("  - billing key {0}: not found" -f $ApiKeyId)
    }

    $key = @($ctx.data | Where-Object { [string]$_.keyId -eq $ApiKeyId } | Select-Object -First 1)
    if ($key.Count -gt 0) {
        Write-Host ("  - available-context key {0}: budgetUsagePercent={1}, currentSpendUsd={2}" -f $ApiKeyId, $key[0].budgetStats.budgetUsagePercent, $key[0].budgetStats.currentSpendUsd)
        Write-Host ("    providerStats: avgDailySpend={0}, avgDailyTokens={1}" -f $key[0].providerStats.averageDailySpendUsd, $key[0].providerStats.averageDailyTokenUsage)
    } else {
        Write-Host ("  - available-context key {0}: not found in personal data" -f $ApiKeyId)
    }
}

function Invoke-ProxyLiveTraffic {
    $body = @{
        model = "gpt-4o-mini"
        messages = @(
            @{
                role = "user"
                content = "Say hello in one short sentence."
            }
        )
        max_tokens = 16
        temperature = 0
    } | ConvertTo-Json -Depth 6 -Compress

    $url = "http://localhost:$gatewayPort/api/v1/ai/$Provider/v1/chat/completions"
    Write-Host ""
    Write-Host "Sending live proxy traffic: $url" -ForegroundColor Yellow
    try {
        $resp = Invoke-WebRequest -Uri $url -Method Post -ContentType "application/json" -Headers @{ "X-User-Id" = $UserId } -Body $body -TimeoutSec 60
        Write-Host ("  - proxy response HTTP {0}" -f $resp.StatusCode) -ForegroundColor Green
    } catch {
        Write-Host ("  - proxy request failed: {0}" -f $_.Exception.Message) -ForegroundColor Yellow
        Write-Host "  - continuing; you can still validate with synthetic message mode."
    }
}

function Publish-SyntheticUsageRecorded {
    $auth = New-BasicAuthHeader $rabbitUser $rabbitPass
    $eventId = [guid]::NewGuid().ToString()
    $corr = [guid]::NewGuid().ToString()
    $now = (Get-Date).ToUniversalTime().ToString("o")

    $payload = [ordered]@{
        eventId = $eventId
        occurredAt = $now
        correlationId = $corr
        userId = $UserId
        organizationId = ""
        teamId = ""
        apiKeyId = $ApiKeyId
        apiKeyFingerprint = "e2e-check"
        apiKeySource = "IDENTITY"
        provider = "OPENAI"
        model = "gpt-4o-mini"
        tokenUsage = [ordered]@{
            model = "gpt-4o-mini"
            promptTokens = 120
            completionTokens = 80
            totalTokens = 200
        }
        estimatedCost = 0
        requestPath = "/proxy/openai/v1/chat/completions"
        upstreamHost = "api.openai.com"
        streaming = $false
        requestSuccessful = $true
        upstreamStatusCode = 200
    } | ConvertTo-Json -Depth 10 -Compress

    $publishBody = [ordered]@{
        properties = @{
            content_type = "application/json"
            headers = @{}
        }
        routing_key = "usage.recorded"
        payload = $payload
        payload_encoding = "string"
    } | ConvertTo-Json -Depth 12 -Compress

    $url = "http://localhost:$rabbitMgmtPort/api/exchanges/%2F/usage.events/publish"
    $result = Invoke-RestMethod -Uri $url -Headers $auth -Method Post -ContentType "application/json" -Body $publishBody -TimeoutSec 20
    if (-not $result.routed) {
        throw "Synthetic publish returned routed=false"
    }
    Write-Host ("Published synthetic usage.recorded eventId={0}" -f $eventId) -ForegroundColor Green
}

Write-Host "Agent event pipeline check starting..." -ForegroundColor Cyan
Show-HealthSnapshot "Before traffic"

if ($ProxyLiveTraffic) {
    Invoke-ProxyLiveTraffic
}

if ($InjectSyntheticUsageRecorded) {
    Publish-SyntheticUsageRecorded
}

Start-Sleep -Seconds 3
Show-HealthSnapshot "After traffic"

Write-Host ""
Write-Host "Done. Expected event types include UsageCostFinalizedEvent and DailyCumulativeTokensUpdatedEvent." -ForegroundColor Cyan
