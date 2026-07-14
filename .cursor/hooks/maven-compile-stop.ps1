# Runs mvn compile when Java was edited; auto-follows up with errors on failure.
$raw = [Console]::In.ReadToEnd()
$status = 'completed'
if (-not [string]::IsNullOrWhiteSpace($raw)) {
    try {
        $input = $raw | ConvertFrom-Json
        if ($null -ne $input.status) {
            $status = [string]$input.status
        }
    } catch {
        # default to completed
    }
}

if ($status -ne 'completed') {
    Write-Output '{}'
    exit 0
}

$flagPath = Join-Path $PSScriptRoot '.java-edited'
if (-not (Test-Path $flagPath)) {
    Write-Output '{}'
    exit 0
}
Remove-Item $flagPath -Force -ErrorAction SilentlyContinue

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvn) {
    $msg = 'Maven compile hook: mvn not found on PATH. Install Maven or add it to PATH.'
    @{ followup_message = $msg } | ConvertTo-Json -Compress
    exit 0
}

Push-Location $projectRoot
try {
    $output = & mvn compile -q -DskipTests '-Dskip.frontend=true' 2>&1
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($exitCode -eq 0) {
    Write-Output '{}'
    exit 0
}

$errorText = ($output | Out-String).Trim()
if ([string]::IsNullOrWhiteSpace($errorText)) {
    $errorText = "mvn compile exited with code $exitCode (no output captured)."
}
if ($errorText.Length -gt 3000) {
    $errorText = $errorText.Substring(0, 3000) + "`n... (truncated)"
}

$message = @"
Maven compile failed after your Java edits. Fix these errors. Do NOT run mvn compile yourself — the hook handles compilation.

$errorText
"@.Trim()

@{ followup_message = $message } | ConvertTo-Json -Compress
exit 0
