$ErrorActionPreference = 'Stop'

[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
chcp 65001 > $null

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$jarPath = Join-Path $repoRoot 'target\code-agent-java-0.1.0-SNAPSHOT.jar'
$configPath = Join-Path $repoRoot 'config\local.env'

if (!(Test-Path $jarPath)) {
    Write-Host "Jar not found: $jarPath" -ForegroundColor Red
    Write-Host 'Run: mvn -s local-settings.xml -DskipTests package' -ForegroundColor Yellow
    exit 1
}

if (Test-Path $configPath) {
    Get-Content $configPath | ForEach-Object {
        $line = $_.Trim()
        if (!$line -or $line.StartsWith('#')) {
            return
        }
        $parts = $line.Split('=', 2)
        if ($parts.Length -ne 2) {
            return
        }
        [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
    }
} else {
    Write-Host 'Missing config/local.env. Copy config/config.example.env to config/local.env and fill your own values.' -ForegroundColor Yellow
}

Set-Location $repoRoot
if (-not $env:JAVA_TOOL_OPTIONS) {
    $env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
}
if ($args.Length -eq 0) {
    & java '-Dfile.encoding=UTF-8' '-jar' $jarPath 'chat' '--cwd' $repoRoot
} else {
    & java '-Dfile.encoding=UTF-8' '-jar' $jarPath @args
}
