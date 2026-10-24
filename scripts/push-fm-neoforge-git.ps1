# Init, commit, tag, push NeoForge expansion branches to ESS GitHub.
$ErrorActionPreference = "Continue"
$Idea = "C:\Users\Ksivi\IdeaProjects"

$packs = @(
  @{ Dir = "Dead Air After Hours FM - 1.21.1 NeoForge"; Repo = "Dead-Air-After-Hours-FM"; Ver = "1.1.1"; Name = "After Hours FM" },
  @{ Dir = "Dead Air - Beat Blocks - 1.21.1 NeoForge"; Repo = "Dead-Air-Beat-Blocks"; Ver = "1.1.1"; Name = "Block Beats FM" },
  @{ Dir = "Dead Air - Broken Youth Radio - 1.21.1 NeoForge"; Repo = "Dead-Air-Broken-Youth-Radio"; Ver = "1.1.1"; Name = "Broken Youth Radio" },
  @{ Dir = "Dead Air - Frontline FM - 1.21.1 NeoForge"; Repo = "Dead-Air-Frontline-FM"; Ver = "1.1.1"; Name = "Frontline FM" },
  @{ Dir = "Dead Air - Iron Rain FM - 1.21.1 NeoForge"; Repo = "Dead-Air-Iron-Rain-FM"; Ver = "1.1.1"; Name = "Iron Rain FM" },
  @{ Dir = "Dead Air - Wayfarer Radio - 1.21.1 NeoForge"; Repo = "Dead-Air-Wayfarer-Radio"; Ver = "1.1.1"; Name = "Wayfarer Radio" },
  @{ Dir = "Dead Air Zero Gravity - 1.21.1 NeoForge"; Repo = "Dead-Air-Zero-Gravity"; Ver = "1.0.1"; Name = "Zero Gravity" },
  @{ Dir = "Dead Air - Pip-Boy Radio Conversion - 1.21.1 NeoForge"; Repo = "Dead-Air-Pip-Boy-Radio-Conversion"; Ver = "1.0.0"; Name = "Pip-Boy Radio Conversion" }
)

$gitignore = Get-Content -Raw "$Idea\Dead Air - 1.21.1 NeoForge\.gitignore"
$utf8 = New-Object System.Text.UTF8Encoding $false

foreach ($p in $packs) {
  $path = Join-Path $Idea $p.Dir
  $tag = "v$($p.Ver)-neoforge"
  Write-Host "`n======== $($p.Name) -> $($p.Repo) $tag ========" -ForegroundColor Cyan
  if (-not (Test-Path $path)) { throw "Missing $path" }
  Set-Location $path

  if (-not (Test-Path .git)) {
    git init -b neoforge-1.21.1
    git remote add origin "https://github.com/ESS-Extra-Special-Studio/$($p.Repo).git"
  } else {
    git checkout -B neoforge-1.21.1
  }

  [System.IO.File]::WriteAllText((Join-Path $path ".gitignore"), $gitignore, $utf8)
  # Pip-Boy: ignore local compile-only libs
  if ($p.Repo -eq "Dead-Air-Pip-Boy-Radio-Conversion") {
    Add-Content -Path .gitignore -Value "`nlibs/`n" -Encoding utf8
  }

  git add -A
  $status = git status --porcelain
  if ($status) {
    $msg = @"
Release $($p.Name) $($p.Ver) for NeoForge 1.21.1.

Loader port of the Forge soundtrack/companion build; same version and content.
"@
    git commit -m $msg
  } else {
    Write-Host "Nothing to commit"
  }

  # Tag (recreate local if re-run)
  git tag -d $tag 2>$null | Out-Null
  if ($LASTEXITCODE -ne 0) { $global:LASTEXITCODE = 0 }
  git tag $tag
  if ($LASTEXITCODE -ne 0) { throw "tag failed for $tag" }

  git push -u origin neoforge-1.21.1
  if ($LASTEXITCODE -ne 0) { throw "branch push failed for $($p.Repo)" }
  git push origin $tag
  if ($LASTEXITCODE -ne 0) { throw "tag push failed for $tag" }
  Write-Host "Pushed $($p.Repo) branch neoforge-1.21.1 + $tag" -ForegroundColor Green
}

Write-Host "`nALL PUSHES DONE" -ForegroundColor Green
