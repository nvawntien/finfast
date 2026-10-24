#Requires -Version 5.1
<#
.SYNOPSIS
Runs the repository's full Maven verification using Docker Desktop's Linux engine.
.DESCRIPTION
Uses the existing pinned Dockerfile build stage, then runs Maven verify with
Testcontainers access. No host Java/Make, developer .env, source bind mount or
persistent Compose database is used. The Docker socket grants daemon control;
run only against trusted repository code. Reports and the JAR stay in a new
temporary directory. Only this invocation's labelled runner/image are removed.
#>
[CmdletBinding()]
param([string]$ReportDirectory)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$dockerCommand = (Get-Command docker -ErrorAction Stop).Source
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$runId = [Guid]::NewGuid().ToString('N')
$imageName = "finfast-backend-check:$runId"
$runnerName = "finfast-backend-check-$runId"
$labelKey = 'com.finfast.backend-check'
if (-not $ReportDirectory) {
    $ReportDirectory = Join-Path ([IO.Path]::GetTempPath()) $runnerName
}
$ReportDirectory = [IO.Path]::GetFullPath($ReportDirectory)
if (Test-Path -LiteralPath $ReportDirectory) {
    throw "ReportDirectory already exists; choose a new directory: $ReportDirectory"
}

function Invoke-Docker {
    param([string[]]$DockerArguments)
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $result = & $dockerCommand @DockerArguments 2>&1
        $commandExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($commandExit -ne 0) {
        throw "Docker command failed (exit $commandExit): $($result | Out-String)"
    }
    return $result
}

function Invoke-DockerLogged {
    param([string[]]$DockerArguments, [string]$LogPath)
    $previousPreference = $ErrorActionPreference
    try {
        # Docker writes normal build progress to stderr; PS 5.1 must not treat
        # those lines as terminating PowerShell errors. Check the native exit.
        $ErrorActionPreference = 'Continue'
        & $dockerCommand @DockerArguments 2>&1 | Out-File -LiteralPath $LogPath -Encoding utf8
        $commandExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    return $commandExit
}

function Remove-OwnedResource {
    param([string]$Kind, [string]$Name)
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $labels = & $dockerCommand $Kind inspect --format '{{json .Config.Labels}}' $Name 2>$null
        $inspectExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($inspectExit -ne 0) { return }
    $owner = ($labels | ConvertFrom-Json).PSObject.Properties[$labelKey]
    if (-not $owner -or $owner.Value -ne $runId) { throw "Refusing to remove unowned resource: $Name" }
    if ($Kind -eq 'container') {
        Invoke-Docker @('container', 'rm', '--force', $Name) | Out-Null
    } else {
        Invoke-Docker @('image', 'rm', $Name) | Out-Null
    }
}

if ((Invoke-Docker @('info', '--format', '{{.OSType}}')) -ne 'linux') {
    throw 'A running Docker Linux engine is required (Docker Desktop on Windows).'
}
New-Item -ItemType Directory -Path $ReportDirectory | Out-Null
Write-Host "Verification evidence: $ReportDirectory"
$buildLog = Join-Path $ReportDirectory 'build.log'
$testLog = Join-Path $ReportDirectory 'verify.log'

try {
    $buildExit = Invoke-DockerLogged @('build', '--progress', 'plain', '--target', 'build',
        '--label', "$labelKey=$runId", '--tag', $imageName,
        (Join-Path $repositoryRoot 'backend')) $buildLog
    if ($buildExit -ne 0) { throw "Image build failed (exit $buildExit); see $buildLog" }

    Invoke-Docker @('create', '--name', $runnerName, '--label', "$labelKey=$runId",
        '--mount', 'type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock',
        '--env', 'TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal',
        $imageName, './mvnw', '--batch-mode', '--no-transfer-progress', 'verify') | Out-Null
    $attachExit = Invoke-DockerLogged @('start', '--attach', $runnerName) $testLog
    $state = (Invoke-Docker @('inspect', '--format', '{{json .State}}', $runnerName)) | ConvertFrom-Json
    if ($state.Running) { throw "Runner still running after attach; see $testLog" }

    # Retain failure reports too. They come from the runner, never a host DB.
    Invoke-Docker @('cp', "${runnerName}:/workspace/target/surefire-reports", $ReportDirectory) | Out-Null
    if ($attachExit -ne 0 -or $state.ExitCode -ne 0) {
        throw "Maven verification failed (attach $attachExit, Maven $($state.ExitCode)); see $testLog"
    }
    Invoke-Docker @('cp', "${runnerName}:/workspace/target/finfast-backend.jar", $ReportDirectory) | Out-Null
    $reports = @(Get-ChildItem -LiteralPath (Join-Path $ReportDirectory 'surefire-reports') -Filter 'TEST-*.xml')
    if ($reports.Count -eq 0) { throw 'No Surefire XML reports were produced.' }
    $totals = @{ Tests = 0; Failures = 0; Errors = 0; Skipped = 0 }
    foreach ($report in $reports) {
        [xml]$suite = Get-Content -LiteralPath $report.FullName -Raw
        $totals.Tests += [int]$suite.testsuite.tests
        $totals.Failures += [int]$suite.testsuite.failures
        $totals.Errors += [int]$suite.testsuite.errors
        $totals.Skipped += [int]$suite.testsuite.skipped
    }
    if ($totals.Tests -eq 0 -or $totals.Failures -ne 0 -or $totals.Errors -ne 0 -or $totals.Skipped -ne 0) {
        throw "Full-suite evidence is incomplete: $($totals | ConvertTo-Json -Compress)"
    }
    $totals | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $ReportDirectory 'summary.json') -Encoding utf8
    Write-Host "PASS: $($totals.Tests) tests; zero failures, errors or skips. Executable JAR and reports retained."
} finally {
    Remove-OwnedResource 'container' $runnerName
    Remove-OwnedResource 'image' $imageName
}
