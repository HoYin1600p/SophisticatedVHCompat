[CmdletBinding(DefaultParameterSetName = 'Remote')]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string[]]$GameVersionIds,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string[]]$RequiredGameVersionTypeIds,

    [Parameter(ParameterSetName = 'Remote')]
    [uri]$CatalogUri = 'https://minecraft.curseforge.com/api/game/versions',

    [Parameter(ParameterSetName = 'Remote')]
    [ValidatePattern('^[A-Za-z_][A-Za-z0-9_]*$')]
    [string]$TokenEnvironmentVariable = 'CURSEFORGE_API_TOKEN',

    [Parameter(Mandatory = $true, ParameterSetName = 'Fixture')]
    [ValidateNotNullOrEmpty()]
    [string]$CatalogJson
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-SafeResult {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Result,

        [Parameter(Mandatory = $true)]
        [int]$ExitCode
    )

    Write-Output ($Result | ConvertTo-Json -Depth 6 -Compress)
    exit $ExitCode
}

function Get-ConfiguredToken {
    param([string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value) -and $env:OS -eq 'Windows_NT') {
        $value = [Environment]::GetEnvironmentVariable($Name, 'User')
    }
    return $value
}

$httpStatus = $null
$rawCatalog = $null

if ($PSCmdlet.ParameterSetName -eq 'Fixture') {
    $rawCatalog = $CatalogJson
}
else {
    $token = Get-ConfiguredToken -Name $TokenEnvironmentVariable
    if ([string]::IsNullOrWhiteSpace($token)) {
        Write-SafeResult -Result @{
            result = 'MISSING_CREDENTIAL'
            httpStatus = $null
            selected = @()
            missingIds = @()
            missingRequiredTypeIds = @()
        } -ExitCode 3
    }

    Add-Type -AssemblyName System.Net.Http
    $client = [System.Net.Http.HttpClient]::new()
    $response = $null
    try {
        $null = $client.DefaultRequestHeaders.Add('X-Api-Token', $token)
        $response = $client.GetAsync($CatalogUri).GetAwaiter().GetResult()
        $httpStatus = [int]$response.StatusCode
        if (-not $response.IsSuccessStatusCode) {
            Write-SafeResult -Result @{
                result = 'HTTP_ERROR'
                httpStatus = $httpStatus
                selected = @()
                missingIds = @()
                missingRequiredTypeIds = @()
            } -ExitCode 2
        }
        $rawCatalog = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    }
    catch {
        Write-SafeResult -Result @{
            result = 'REQUEST_FAILED'
            httpStatus = $httpStatus
            selected = @()
            missingIds = @()
            missingRequiredTypeIds = @()
        } -ExitCode 2
    }
    finally {
        if ($null -ne $response) {
            $response.Dispose()
        }
        $client.Dispose()
        $token = $null
    }
}

try {
    $catalog = @($rawCatalog | ConvertFrom-Json)
}
catch {
    Write-SafeResult -Result @{
        result = 'INVALID_CATALOG_JSON'
        httpStatus = $httpStatus
        selected = @()
        missingIds = @()
        missingRequiredTypeIds = @()
    } -ExitCode 2
}

$plannedIds = @($GameVersionIds | ForEach-Object { ([string]$_) -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Sort-Object -Unique)
$requiredTypeIds = @($RequiredGameVersionTypeIds | ForEach-Object { ([string]$_) -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Sort-Object -Unique)
$selected = @()
$missingIds = @()
$duplicateIds = @()

foreach ($id in $plannedIds) {
    $matches = @($catalog | Where-Object { [string]$_.id -eq $id })
    if ($matches.Count -eq 0) {
        $missingIds += $id
        continue
    }
    if ($matches.Count -ne 1) {
        $duplicateIds += $id
        continue
    }

    $entry = $matches[0]
    $selected += [ordered]@{
        id = [string]$entry.id
        name = [string]$entry.name
        gameVersionTypeID = [string]$entry.gameVersionTypeID
    }
}

$selectedTypeIds = @($selected | ForEach-Object { [string]$_.gameVersionTypeID } | Sort-Object -Unique)
$missingRequiredTypeIds = @($requiredTypeIds | Where-Object { $_ -notin $selectedTypeIds })
$valid = $missingIds.Count -eq 0 -and $duplicateIds.Count -eq 0 -and $missingRequiredTypeIds.Count -eq 0

Write-SafeResult -Result @{
    result = $(if ($valid) { 'PASS' } else { 'FAIL' })
    httpStatus = $httpStatus
    selected = $selected
    missingIds = $missingIds
    duplicateIds = $duplicateIds
    missingRequiredTypeIds = $missingRequiredTypeIds
} -ExitCode $(if ($valid) { 0 } else { 4 })
