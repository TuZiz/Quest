param(
    [string]$ProjectsRoot = (Split-Path $PSScriptRoot -Parent),
    [string]$Jdk = 'C:/Program Files/Zulu/zulu-25',
    [string]$PluginsDir = 'C:/Users/32633/Desktop/leaf/plugins',
    [switch]$Offline,
    [string]$TabJar = ''
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $Jdk
$env:Path = "$Jdk/bin;$env:Path"
Set-Location $PSScriptRoot
if (!(Test-Path -LiteralPath '.build/dependencies/TAB/TAB-6.2.1.jar')) {
    if (!$TabJar) {
        $candidates = @(Get-ChildItem -LiteralPath $PluginsDir -Filter '*TAB*.jar' -File)
        if ($candidates.Count -ne 1) { throw 'Specify -TabJar pointing to the verified TAB 6.2.1 JAR' }
        $TabJar = $candidates[0].FullName
    }
    New-Item -ItemType Directory -Path '.build/dependencies/TAB' -Force | Out-Null
    Copy-Item -LiteralPath $TabJar -Destination '.build/dependencies/TAB/TAB-6.2.1.jar'
}
python tools/prepare_compat.py $ProjectsRoot
if ($LASTEXITCODE -ne 0) { throw 'Preparing compatibility sources failed' }
$mavenOptions = @('-Dkotlin.compiler.daemon=false')
if ($Offline) { $mavenOptions += '-o' }
foreach ($project in @('DreamSkyblock', 'Genlite')) {
    & mvn @mavenOptions "-Dplugins.dir=$PluginsDir" -f ".build/dependencies/$project/pom.xml" clean package
    if ($LASTEXITCODE -ne 0) { throw "$project build failed" }
}
& mvn @mavenOptions "-Dplugins.dir=$PluginsDir" package
if ($LASTEXITCODE -ne 0) { throw 'Quest build failed' }
python tools/package_release.py
if ($LASTEXITCODE -ne 0) { throw 'Release packaging failed' }
