# Build both Dead Air and RadioTowers and copy JARs to the test modpack.
# Run from repo root (Dead Air) or any folder; uses fixed paths below.

$ErrorActionPreference = "Stop"
$modpackMods = "$env:USERPROFILE\curseforge\minecraft\Instances\C.Ideas\mods"
$deadAirDir = "c:\Users\Ksivi\IdeaProjects\Dead Air"
$radiotowersDir = "C:\Users\Ksivi\MCreatorWorkspaces\radioos"

Write-Host "Building Dead Air..."
Set-Location $deadAirDir
& .\gradlew.bat build --no-daemon -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
# Dead Air build task already copies; ensure latest is the one with correct version
$daJar = Get-ChildItem "$deadAirDir\build\libs\dead_air-*.jar" | Where-Object { $_.Name -notmatch 'sources' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($daJar) {
    Copy-Item $daJar.FullName -Destination (Join-Path $modpackMods $daJar.Name) -Force
    Write-Host "Copied $($daJar.Name) to modpack"
}

Write-Host "Building RadioTowers..."
Set-Location $radiotowersDir
& .\gradlew.bat build --no-daemon -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$rtJar = Get-ChildItem "$radiotowersDir\build\libs\radiotowers-*.jar" | Where-Object { $_.Name -notmatch 'sources' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($rtJar) {
    Copy-Item $rtJar.FullName -Destination (Join-Path $modpackMods $rtJar.Name) -Force
    Write-Host "Copied $($rtJar.Name) to modpack"
}

Write-Host "Done. Modpack mods: $modpackMods"
Set-Location $deadAirDir
