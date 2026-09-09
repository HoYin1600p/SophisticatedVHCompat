[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $repositoryRoot '.codex\mod-publish.json'
$config = Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json
$gameVersionIds = @($config.curseforge.gameVersionIds | ForEach-Object { [string]$_ })
$requiredTypeIds = @(
    $config.curseforge.requiredGameVersionTypes |
        ForEach-Object { [string]$_.id }
)

& (Join-Path $PSScriptRoot 'Test-CurseForgeGameVersions.ps1') `
    -GameVersionIds $gameVersionIds `
    -RequiredGameVersionTypeIds $requiredTypeIds
exit $LASTEXITCODE
