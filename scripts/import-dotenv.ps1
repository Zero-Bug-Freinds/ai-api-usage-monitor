# Dotenv-style loader for PowerShell (process environment only).
# Usage: . ./import-dotenv.ps1; Import-DotEnv -Path "C:\path\to\.env"

function Import-DotEnv {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string] $Path
    )
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Env file not found: $Path"
    }
    $raw = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    if ($null -eq $raw) { return }
    if ($raw.StartsWith([char]0xFEFF)) {
        $raw = $raw.Substring(1)
    }
    foreach ($line in $raw -split "`r?`n") {
        $t = $line.TrimEnd()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $eq = $t.IndexOf('=')
        if ($eq -lt 1) { continue }
        $key = $t.Substring(0, $eq).Trim()
        if ($key -eq '') { continue }
        $val = $t.Substring($eq + 1).TrimStart()
        if ($val.Length -ge 2) {
            $dq = $val.StartsWith('"') -and $val.EndsWith('"')
            $sq = $val.StartsWith("'") -and $val.EndsWith("'")
            if ($dq -or $sq) {
                $val = $val.Substring(1, $val.Length - 2)
            }
        }
        Set-Item -Path "Env:$key" -Value $val
    }
}
