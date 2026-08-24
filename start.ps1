$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backend = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'

if (-not (Get-Command javac.exe -ErrorAction SilentlyContinue)) {
    $jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft', 'C:\Program Files\Java' -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -First 1
    if ($jdk) {
        $env:JAVA_HOME = $jdk.FullName
        $env:Path = "$($jdk.FullName)\bin;$env:Path"
    }
}

if (-not (Get-Command javac.exe -ErrorAction SilentlyContinue)) {
    throw 'A full JDK is required. Install JDK 17 or 21 and set JAVA_HOME before starting StockPulse.'
}

$envFile = Join-Path $root '.env'
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*[^#][^=]*=' } | ForEach-Object {
        $name, $value = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim())
    }
}

$strategy = if ($env:STOCKPULSE_STRATEGY) { $env:STOCKPULSE_STRATEGY } else { 'rules' }

$backendProcess = Start-Process -FilePath 'mvn.cmd' -ArgumentList "spring-boot:run", "-Dspring-boot.run.arguments=--stockpulse.strategy=$strategy" -WorkingDirectory $backend -PassThru
$frontendProcess = Start-Process -FilePath 'npm.cmd' -ArgumentList 'run', 'dev', '--', '--host', '0.0.0.0' -WorkingDirectory $frontend -PassThru

Write-Host 'StockPulse backend: http://localhost:8080'
Write-Host 'StockPulse frontend: http://localhost:5173'
Write-Host 'Press Ctrl+C to stop both services.'

try {
    while (-not $backendProcess.HasExited -and -not $frontendProcess.HasExited) {
        Wait-Event -Timeout 1 | Out-Null
        $backendProcess.Refresh()
        $frontendProcess.Refresh()
    }
} finally {
    if (-not $backendProcess.HasExited) { Stop-Process -Id $backendProcess.Id -Force }
    if (-not $frontendProcess.HasExited) { Stop-Process -Id $frontendProcess.Id -Force }
}
