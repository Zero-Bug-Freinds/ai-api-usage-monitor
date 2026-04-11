# Quick check: billing-service direct + api-gateway -> billing for GET /api/v1/expenditure/api-keys.
#
# Step 1 (always): direct billing with X-User-Id + X-Gateway-Auth.
# Step 2: matches GATEWAY_DEV_MODE:
#   - true  : gateway with X-User-Id only (gateway adds X-Gateway-Auth upstream).
#   - false : gateway with JWT (same path as browser/BFF). Supply token via env — see below.
#
# Prereq: postgres-billing, billing-service; for step 2 also api-gateway-service.
# Usage (repo root): .\scripts\verify-expenditure-chain.ps1
#
# Optional .env (JWT path when GATEWAY_DEV_MODE=false):
#   EXPENDITURE_VERIFY_GATEWAY_JWT=<access token>   # preferred for CI/local
#   # or identity login (local smoke only — do not commit real passwords):
#   EXPENDITURE_VERIFY_LOGIN_EMAIL=you@example.com
#   EXPENDITURE_VERIFY_LOGIN_PASSWORD=********
#   IDENTITY_SERVICE_URL=http://127.0.0.1:8090      # or EXPENDITURE_VERIFY_IDENTITY_URL
#
# Gateway must validate JWT: GATEWAY_JWT_SECRET must match identity JWT signing key (JWT_SECRET).

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$EnvPath = Join-Path $RepoRoot '.env'
if (Test-Path -LiteralPath $EnvPath) {
    . (Join-Path $PSScriptRoot 'import-dotenv.ps1')
    Import-DotEnv -Path $EnvPath
}

$billingPort = if ($env:BILLING_SERVICE_PORT -and $env:BILLING_SERVICE_PORT.Trim() -ne '') {
    $env:BILLING_SERVICE_PORT.Trim()
} else {
    '8095'
}
$gwPort = if ($env:API_GATEWAY_PORT -and $env:API_GATEWAY_PORT.Trim() -ne '') {
    $env:API_GATEWAY_PORT.Trim()
} else {
    '8080'
}
$secret = if ($env:GATEWAY_SHARED_SECRET -and $env:GATEWAY_SHARED_SECRET.Trim() -ne '') {
    $env:GATEWAY_SHARED_SECRET.Trim()
} else {
    'local-dev-gateway-shared-secret-do-not-use-in-prod'
}

$gwDevRaw = $env:GATEWAY_DEV_MODE
$gatewayDevMode = ($null -eq $gwDevRaw -or $gwDevRaw.Trim() -eq '' -or $gwDevRaw.Trim().ToLowerInvariant() -in @('true', '1', 'yes'))

$userId = 'expenditure-chain-verify'
$path = '/api/v1/expenditure/api-keys'
$failed = $false

function Get-HttpCode {
    param(
        [string] $Url,
        [string[]] $HeaderArgs
    )
    $curlArgs = @('-s', '-o', 'NUL', '-w', '%{http_code}') + $HeaderArgs + @($Url)
    $code = & curl.exe @curlArgs 2>$null
    if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) {
        return '000'
    }
    if (-not $code) {
        return '000'
    }
    return [string]$code
}

function Get-ExpenditureVerifyJwt {
    $pre = $env:EXPENDITURE_VERIFY_GATEWAY_JWT
    if ($pre -and $pre.Trim() -ne '') {
        Write-Host "   Using EXPENDITURE_VERIFY_GATEWAY_JWT" -ForegroundColor DarkGray
        return $pre.Trim()
    }
    $email = $env:EXPENDITURE_VERIFY_LOGIN_EMAIL
    $pw = $env:EXPENDITURE_VERIFY_LOGIN_PASSWORD
    if (-not $email -or $email.Trim() -eq '' -or -not $pw) {
        return $null
    }
    $base = $env:EXPENDITURE_VERIFY_IDENTITY_URL
    if (-not $base -or $base.Trim() -eq '') {
        $base = $env:IDENTITY_SERVICE_URL
    }
    if (-not $base -or $base.Trim() -eq '') {
        $base = 'http://127.0.0.1:8090'
    }
    $base = $base.Trim().TrimEnd('/')
    $loginUrl = "${base}/api/auth/login"
    Write-Host "   Fetching JWT via POST $loginUrl (EXPENDITURE_VERIFY_LOGIN_*)" -ForegroundColor DarkGray
    try {
        $bodyObj = @{ email = $email.Trim(); password = $pw }
        $r = Invoke-RestMethod -Uri $loginUrl -Method Post -ContentType 'application/json' -Body ($bodyObj | ConvertTo-Json -Compress) -ErrorAction Stop
        if ($r.success -eq $true -and $null -ne $r.data -and $r.data.accessToken) {
            return [string]$r.data.accessToken
        }
    } catch {
        Write-Host "   WARN identity login failed: $_" -ForegroundColor Yellow
    }
    return $null
}

Write-Host "1) Direct billing-service :$billingPort$path"
$c1 = Get-HttpCode -Url "http://127.0.0.1:${billingPort}${path}" -HeaderArgs @(
    '-H', "X-User-Id: $userId",
    '-H', "X-Gateway-Auth: $secret"
)
if ($c1 -eq '200') {
    Write-Host "   OK HTTP $c1 (empty list [] is expected if no keys seen yet)" -ForegroundColor Green
} else {
    Write-Host "   FAIL HTTP $c1 — ensure billing is up and billing_db reachable (see docs/billing-service-overview §6.4)" -ForegroundColor Red
    $failed = $true
}

Write-Host "2) Via api-gateway :$gwPort$path"
if ($gatewayDevMode) {
    Write-Host "   (GATEWAY_DEV_MODE=true: X-User-Id only; gateway adds X-Gateway-Auth to billing)"
    $c2 = Get-HttpCode -Url "http://127.0.0.1:${gwPort}${path}" -HeaderArgs @('-H', "X-User-Id: $userId")
    if ($c2 -eq '200') {
        Write-Host "   OK HTTP $c2" -ForegroundColor Green
    } elseif ($c2 -eq '000') {
        Write-Host "   FAIL connection error — start api-gateway-service (port $gwPort) or fix GATEWAY_BILLING_URI" -ForegroundColor Red
        $failed = $true
    } elseif ($c2 -eq '502' -or $c2 -eq '503') {
        Write-Host "   FAIL HTTP $c2 — gateway cannot reach billing; align GATEWAY_BILLING_URI with BILLING_SERVICE_PORT (default 8095)" -ForegroundColor Red
        $failed = $true
    } else {
        Write-Host "   FAIL HTTP $c2 — check gateway logs (docs/contracts/gateway-proxy.md)" -ForegroundColor Red
        $failed = $true
    }
} else {
    Write-Host "   (GATEWAY_DEV_MODE=false: JWT required — same as billing web / BFF)"
    $jwt = Get-ExpenditureVerifyJwt
    if (-not $jwt) {
        Write-Host "   SKIPPED — set EXPENDITURE_VERIFY_GATEWAY_JWT or EXPENDITURE_VERIFY_LOGIN_EMAIL + EXPENDITURE_VERIFY_LOGIN_PASSWORD" -ForegroundColor Yellow
        Write-Host "            Ensure GATEWAY_JWT_SECRET matches identity JWT_SECRET (signing key)." -ForegroundColor Yellow
    } else {
        $c2 = Get-HttpCode -Url "http://127.0.0.1:${gwPort}${path}" -HeaderArgs @('-H', "Authorization: Bearer $jwt")
        if ($c2 -eq '200') {
            Write-Host "   OK HTTP $c2" -ForegroundColor Green
        } elseif ($c2 -eq '000') {
            Write-Host "   FAIL connection error — start api-gateway-service (port $gwPort)" -ForegroundColor Red
            $failed = $true
        } elseif ($c2 -eq '401' -or $c2 -eq '403') {
            Write-Host "   FAIL HTTP $c2 — JWT rejected (align GATEWAY_JWT_SECRET with identity JWT_SECRET)" -ForegroundColor Red
            $failed = $true
        } elseif ($c2 -eq '502' -or $c2 -eq '503') {
            Write-Host "   FAIL HTTP $c2 — gateway cannot reach billing" -ForegroundColor Red
            $failed = $true
        } else {
            Write-Host "   FAIL HTTP $c2 — check gateway and billing logs" -ForegroundColor Red
            $failed = $true
        }
    }
}

if ($failed) {
    exit 1
}
exit 0
