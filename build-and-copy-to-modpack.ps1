# Build both Dead Air and RadioTowers and copy JARs to Forge test packs.
# Run from repo root (Dead Air) or any folder; uses fixed paths below.

$ErrorActionPreference = "Stop"
$forgePacks = @(
    "$env:USERPROFILE\curseforge\minecraft\Instances\C.Ideas\mods",
    "$env:USERPROFILE\curseforge\minecraft\Instances\ESS Lastlight (post-apocalyptic modpack - 1.20.1 Forge)\mods"
)
$deadAirDir = "c:\Users\Ksivi\IdeaProjects\Dead Air"
$radiotowersDir = "C:\Users\Ksivi\MCreatorWorkspaces\radioos"

function Copy-ModJar([System.IO.FileInfo]$jar, [string]$modIdPrefix) {
    if (-not $jar) { return }
    foreach ($mods in $forgePacks) {
        if (-not (Test-Path $mods)) {
            Write-Host "Skip (missing): $mods"
            continue
        }
        Get-ChildItem $mods -Filter "$modIdPrefix-*.jar" -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -ne $jar.Name -and $_.Name -notlike "${modIdPrefix}_*" } |
            Remove-Item -Force -ErrorAction SilentlyContinue
        Copy-Item $jar.FullName -Destination (Join-Path $mods $jar.Name) -Force
        Write-Host "Copied $($jar.Name) -> $mods"
    }
}

Write-Host "Building Dead Air..."
Set-Location $deadAirDir
& .\gradlew.bat build --no-daemon -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
# Dead Air build.gradle already copies to both packs; re-copy ensures latest basename.
$daJar = Get-ChildItem "$deadAirDir\build\libs\dead_air-*.jar" |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
Copy-ModJar $daJar "dead_air"

Write-Host "Building RadioTowers..."
Set-Location $radiotowersDir
& .\gradlew.bat build --no-daemon -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$rtJar = Get-ChildItem "$radiotowersDir\build\libs\radiotowers-*.jar" |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
Copy-ModJar $rtJar "radiotowers"

Write-Host "Done. Forge packs: C.Ideas + ESS Lastlight"
Set-Location $deadAirDir
