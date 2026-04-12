# Load repo-root .env, apply local defaults, then run ./gradlew bootRun for a service.
# Usage (from repo root):
#   .\scripts\bootrun.ps1 identity-service
#   .\scripts\bootrun.ps1 usage-service -- --no-daemon
# Arguments after the service name are passed to Gradle.

[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0)]
    [ValidateSet(
        'identity-service',
        'usage-service',
        'team-service',
        'billing-service',
        'api-gateway-service',
        'proxy-service'
    )]
    [string] $Service,

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs
)

$RepoRoot = Split-Path -Parent $PSScriptRoot
$EnvPath = Join-Path $RepoRoot '.env'
if (-not (Test-Path -LiteralPath $EnvPath)) {
    Write-Error "Missing $EnvPath — copy .env.example to .env and adjust values."
    exit 1
}

. (Join-Path $PSScriptRoot 'import-dotenv.ps1')
Import-DotEnv -Path $EnvPath

# Per-service defaults only (SERVER_PORT would override server.port for every Spring app).
switch ($Service) {
    'identity-service' { if (-not $env:SERVER_PORT) { $env:SERVER_PORT = '8090' } }
    'usage-service' { if (-not $env:USAGE_SERVICE_PORT) { $env:USAGE_SERVICE_PORT = '8092' } }
    'team-service' { if (-not $env:TEAM_SERVICE_PORT) { $env:TEAM_SERVICE_PORT = '8094' } }
    'billing-service' { if (-not $env:BILLING_SERVICE_PORT) { $env:BILLING_SERVICE_PORT = '8095' } }
    Default { }
}

$ServiceDir = Join-Path -Path (Join-Path -Path $RepoRoot -ChildPath 'services') -ChildPath $Service
if (-not (Test-Path -LiteralPath $ServiceDir)) {
    Write-Error "Service directory not found: $ServiceDir"
    exit 1
}

Push-Location $ServiceDir
try {
    $gradleArgsAll = @('bootRun')
    if ($GradleArgs -and $GradleArgs.Count -gt 0) {
        $gradleArgsAll += $GradleArgs
    }
    $gwBat = Join-Path $ServiceDir 'gradlew.bat'
    $gwSh = Join-Path $ServiceDir 'gradlew'
    if (Test-Path -LiteralPath $gwBat) {
        & $gwBat @gradleArgsAll
    } elseif (Test-Path -LiteralPath $gwSh) {
        chmod +x $gwSh 2>$null
        & $gwSh @gradleArgsAll
    } else {
        Write-Error "gradlew / gradlew.bat not found under $ServiceDir"
        exit 1
    }
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
