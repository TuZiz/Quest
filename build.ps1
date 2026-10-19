param(
    [string]$ProjectsRoot = (Split-Path $PSScriptRoot -Parent),
    [string]$Jdk = 'C:/Program Files/Zulu/zulu-25',
    [string]$PluginsDir = 'C:/Users/32633/Desktop/leaf/plugins',
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $Jdk
$env:Path = "$Jdk/bin;$env:Path"
Set-Location $PSScriptRoot
python tools/prepare_compat.py $ProjectsRoot
if ($LASTEXITCODE -ne 0) { throw 'Preparing compatibility sources failed' }
$mavenOptions = @('-Dkotlin.compiler.daemon=false')
if ($Offline) { $mavenOptions += '-o' }
foreach ($project in @('DreamSkyblock', 'Genlite')) {
    & mvn @mavenOptions "-Dplugins.dir=$PluginsDir" -f ".build/dependencies/$project/pom.xml" package
    if ($LASTEXITCODE -ne 0) { throw "$project build failed" }
}
& mvn @mavenOptions "-Dplugins.dir=$PluginsDir" package
if ($LASTEXITCODE -ne 0) { throw 'Quest build failed' }
python tools/package_release.py
if ($LASTEXITCODE -ne 0) { throw 'Release packaging failed' }
