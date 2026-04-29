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
        'proxy-service',
        'agent-service'
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
    'identity-service' {
        if (-not $env:SERVER_PORT) { $env:SERVER_PORT = '8090' }
        # identity-service JWT 서명 키를 gateway 검증 키와 맞춘다.
        # .env 에 JWT_SECRET 이 비어 있고 GATEWAY_JWT_SECRET 만 있는 경우를 대비한다.
        if ((-not $env:JWT_SECRET) -and $env:GATEWAY_JWT_SECRET) {
            $env:JWT_SECRET = $env:GATEWAY_JWT_SECRET
        }
    }
    'usage-service' { if (-not $env:USAGE_SERVICE_PORT) { $env:USAGE_SERVICE_PORT = '8092' } }
    'team-service' { if (-not $env:TEAM_SERVICE_PORT) { $env:TEAM_SERVICE_PORT = '8094' } }
    'billing-service' { if (-not $env:BILLING_SERVICE_PORT) { $env:BILLING_SERVICE_PORT = '8095' } }
    'agent-service' { if (-not $env:AI_AGENT_SERVICE_PORT) { $env:AI_AGENT_SERVICE_PORT = '8096' } }
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
    if ($Service -eq 'agent-service' -and -not (Test-Path -LiteralPath $gwBat) -and -not (Test-Path -LiteralPath $gwSh)) {
        $sharedWrapper = Join-Path -Path (Join-Path -Path $RepoRoot -ChildPath 'services') -ChildPath 'team-service/gradlew.bat'
        if (-not (Test-Path -LiteralPath $sharedWrapper)) {
            Write-Error "Shared Gradle wrapper not found: $sharedWrapper"
            exit 1
        }
        & $sharedWrapper '-p' $ServiceDir @gradleArgsAll
    } elseif (Test-Path -LiteralPath $gwBat) {
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
