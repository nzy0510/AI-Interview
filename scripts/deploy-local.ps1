[CmdletBinding()]
param(
    [switch]$ExposeDataServices,
    [ValidateRange(1, 65535)]
    [int]$RedisHostPort = 16379,
    [ValidateRange(1, 65535)]
    [int]$QdrantHostPort = 16333
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$isWindowsHost = $env:OS -eq 'Windows_NT'
$dockerContextArgs = if ($isWindowsHost) { @('--context', 'desktop-linux') } else { @() }
$composeArgs = @('compose', '-f', 'docker-compose.example.yml')

function Get-LocalEnvironmentValues {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw 'Missing .env. Copy .env.example to .env and replace every required placeholder before deploying.'
    }

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -notmatch '^\s*([^#][^=]*)=(.*)$') {
            continue
        }

        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$name] = $value
    }
    return $values
}

function Get-EffectiveEnvironmentValue {
    param(
        [string]$Name,
        [hashtable]$FileValues
    )

    $processValues = [Environment]::GetEnvironmentVariables('Process')
    if ($processValues.Contains($Name)) {
        return [string]$processValues[$Name]
    }
    return [string]$FileValues[$Name]
}

function Assert-LocalSecretsConfigured {
    param([hashtable]$FileValues)

    $requirements = @(
        @{ Name = 'DB_PASSWORD'; MinLength = 6 },
        @{ Name = 'MYSQL_ROOT_PASSWORD'; MinLength = 4 },
        @{ Name = 'APP_LLM_CONFIG_ENCRYPTION_KEY'; MinLength = 32 },
        @{ Name = 'JWT_SIGN_KEY'; MinLength = 32 },
        @{ Name = 'APP_ANALYTICS_HASH_SALT'; MinLength = 16 }
    )
    $placeholderPrefixes = @('your_', 'replace_', 'change_me_')

    foreach ($requirement in $requirements) {
        $value = Get-EffectiveEnvironmentValue -Name $requirement.Name -FileValues $FileValues
        $isPlaceholder = $false
        foreach ($prefix in $placeholderPrefixes) {
            if ($value.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
                $isPlaceholder = $true
                break
            }
        }

        if ([string]::IsNullOrWhiteSpace($value) -or $isPlaceholder -or $value.Length -lt $requirement.MinLength) {
            throw "$($requirement.Name) is missing, still uses an example value, or is too short (minimum $($requirement.MinLength) characters)."
        }
    }
}

$hadDockerHost = Test-Path Env:DOCKER_HOST
$previousDockerHost = $env:DOCKER_HOST
$hadRedisHostPort = Test-Path Env:REDIS_HOST_PORT
$previousRedisHostPort = $env:REDIS_HOST_PORT
$hadQdrantHostPort = Test-Path Env:QDRANT_HOST_PORT
$previousQdrantHostPort = $env:QDRANT_HOST_PORT

Push-Location $projectRoot
try {
    if ($isWindowsHost) {
        Remove-Item Env:DOCKER_HOST -ErrorAction SilentlyContinue
    }

    $fileValues = Get-LocalEnvironmentValues -Path (Join-Path $projectRoot '.env')
    Assert-LocalSecretsConfigured -FileValues $fileValues

    if ($ExposeDataServices) {
        if ($RedisHostPort -eq $QdrantHostPort) {
            throw 'RedisHostPort and QdrantHostPort must be different when data services are exposed.'
        }
        $env:REDIS_HOST_PORT = [string]$RedisHostPort
        $env:QDRANT_HOST_PORT = [string]$QdrantHostPort
        $composeArgs += @('-f', 'docker-compose.dev-tools.yml')
    }

    & docker @dockerContextArgs info --format 'Docker Server {{.ServerVersion}}'
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Desktop Linux engine is unavailable.'
    }

    & docker @dockerContextArgs @composeArgs config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Compose configuration is invalid.'
    }

    $buildSucceeded = $false
    for ($attempt = 1; $attempt -le 2; $attempt++) {
        Write-Host "Building images (attempt $attempt/2)..."
        & docker @dockerContextArgs @composeArgs build
        if ($LASTEXITCODE -eq 0) {
            $buildSucceeded = $true
            break
        }

        if ($attempt -lt 2) {
            Write-Warning 'Image build failed; retrying once with the preserved BuildKit caches.'
            & docker @dockerContextArgs info --format 'Docker Server {{.ServerVersion}}'
            if ($LASTEXITCODE -ne 0) {
                throw 'Docker Desktop Linux engine became unavailable after the failed build. Restart Docker Desktop, then run this script again.'
            }
        }
    }

    if (-not $buildSucceeded) {
        throw 'Docker image build failed twice.'
    }

    & docker @dockerContextArgs @composeArgs up -d --no-build
    if ($LASTEXITCODE -ne 0) {
        & docker @dockerContextArgs @composeArgs ps --all
        & docker @dockerContextArgs @composeArgs logs --tail 100
        throw 'Docker Compose failed to start the services.'
    }

    $ready = $false
    for ($probe = 1; $probe -le 60; $probe++) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1/api/user/auth-config' -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                $ready = $true
                break
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }

    & docker @dockerContextArgs @composeArgs ps
    if (-not $ready) {
        & docker @dockerContextArgs @composeArgs logs --tail 100 backend frontend
        throw 'Services started, but the frontend API readiness check did not return HTTP 200.'
    }

    Write-Host 'InterWise local deployment is ready at http://127.0.0.1'
} finally {
    Pop-Location

    if ($hadDockerHost) { $env:DOCKER_HOST = $previousDockerHost } else { Remove-Item Env:DOCKER_HOST -ErrorAction SilentlyContinue }
    if ($hadRedisHostPort) { $env:REDIS_HOST_PORT = $previousRedisHostPort } else { Remove-Item Env:REDIS_HOST_PORT -ErrorAction SilentlyContinue }
    if ($hadQdrantHostPort) { $env:QDRANT_HOST_PORT = $previousQdrantHostPort } else { Remove-Item Env:QDRANT_HOST_PORT -ErrorAction SilentlyContinue }
}
