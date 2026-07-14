# Marks that a Java source file was edited so the stop hook knows to compile.
$raw = [Console]::In.ReadToEnd()
if ([string]::IsNullOrWhiteSpace($raw)) {
    Write-Output '{}'
    exit 0
}

try {
    $input = $raw | ConvertFrom-Json
} catch {
    Write-Output '{}'
    exit 0
}

$filePath = [string]$input.file_path
if ($filePath -match '[/\\]src[/\\](main|test)[/\\]java[/\\].*\.java$') {
    $flagPath = Join-Path $PSScriptRoot '.java-edited'
    Set-Content -Path $flagPath -Value (Get-Date -Format o) -NoNewline
}

Write-Output '{}'
exit 0
