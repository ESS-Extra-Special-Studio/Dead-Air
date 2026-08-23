#Requires -Version 5.1
<#
.SYNOPSIS
  Rename uk.creatopia(.unbound) packages to uk.co.extraspecialstudio and set ARR license.
#>
$ErrorActionPreference = "Stop"

$IdeaRoot = "c:\Users\Ksivi\IdeaProjects"
$ExcludeNames = @(
  "Dead Air"  # processed separately only if listed; we include it
)

# Skip non-mod / junk folders by requiring gradle.properties with mod_id
$Projects = Get-ChildItem $IdeaRoot -Directory | Where-Object {
  $gp = Join-Path $_.FullName "gradle.properties"
  (Test-Path $gp) -and ((Get-Content $gp -Raw) -match '(?m)^\s*mod_id\s*=')
}

function Should-SkipPath([string]$path) {
  $norm = $path -replace '/', '\'
  foreach ($skip in @('\backup-', '\bin\', '_extract', 'decompiled', 'zombiecraft_', '\build\', '\.gradle\', 'agent-tools', 'agent-transcripts')) {
    if ($norm -like "*$skip*") { return $true }
  }
  return $false
}

function Update-TextFile([string]$filePath) {
  if (Should-SkipPath $filePath) { return $false }
  $ext = [IO.Path]::GetExtension($filePath).ToLowerInvariant()
  $ok = @('.java', '.kt', '.gradle', '.kts', '.toml', '.json', '.properties', '.md', '.txt', '.xml', '.mf', '.cfg', '.accesswidener')
  if ($ok -notcontains $ext -and (Split-Path $filePath -Leaf) -notin @('build.gradle', 'settings.gradle', 'gradle.properties')) {
    # still allow files without extension that are known
    if ($ext -ne '' -and $ext -ne '.ps1') { return $false }
  }
  # Skip binary-ish and huge assets
  if ($ext -in @('.jar', '.ogg', '.png', '.jpg', '.webp', '.class', '.dll', '.exe')) { return $false }

  try {
    $bytes = [IO.File]::ReadAllBytes($filePath)
    # skip if looks binary
    if ($bytes.Length -gt 0 -and ($bytes | Select-Object -First 800 | Where-Object { $_ -eq 0 }).Count -gt 0) { return $false }
    $content = [Text.Encoding]::UTF8.GetString($bytes)
  } catch { return $false }

  $orig = $content
  # Order matters: unbound first, then remaining creatopia
  $content = $content -replace 'uk\.creatopia\.unbound', 'uk.co.extraspecialstudio'
  $content = $content -replace 'uk\.creatopia', 'uk.co.extraspecialstudio'
  $content = $content -replace 'https?://unbound\.creatopia\.uk/?', 'https://extraspecialstudio.co.uk'
  $content = $content -replace 'unbound\.creatopia\.uk', 'extraspecialstudio.co.uk'

  if ($content -ne $orig) {
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [IO.File]::WriteAllText($filePath, $content, $utf8NoBom)
    return $true
  }
  return $false
}

function Update-GradleProperties([string]$projectDir) {
  $gp = Join-Path $projectDir "gradle.properties"
  if (-not (Test-Path $gp)) { return }
  $lines = Get-Content $gp
  $out = foreach ($line in $lines) {
    if ($line -match '^\s*mod_license\s*=') {
      'mod_license=All Rights Reserved'
    } elseif ($line -match '^\s*mod_group_id\s*=') {
      'mod_group_id=uk.co.extraspecialstudio'
    } elseif ($line -match '^\s*group\s*=\s*uk\.creatopia') {
      ($line -replace 'uk\.creatopia\.unbound', 'uk.co.extraspecialstudio' -replace 'uk\.creatopia', 'uk.co.extraspecialstudio')
    } else {
      # still apply general creatopia replacements in comments/URLs
      $line -replace 'uk\.creatopia\.unbound', 'uk.co.extraspecialstudio' -replace 'uk\.creatopia', 'uk.co.extraspecialstudio' -replace 'unbound\.creatopia\.uk', 'extraspecialstudio.co.uk'
    }
  }
  $utf8NoBom = New-Object System.Text.UTF8Encoding $false
  [IO.File]::WriteAllLines($gp, $out, $utf8NoBom)
}

function Merge-Move([string]$srcPath, [string]$destPath) {
  if (-not (Test-Path $srcPath)) { return }
  if (-not (Test-Path $destPath)) {
    $parent = Split-Path $destPath -Parent
    if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
    Move-Item $srcPath -Destination $destPath
    return
  }
  if (-not (Get-Item $srcPath).PSIsContainer) {
    Move-Item $srcPath -Destination $destPath -Force
    return
  }
  Get-ChildItem $srcPath -Force | ForEach-Object {
    Merge-Move $_.FullName (Join-Path $destPath $_.Name)
  }
  Remove-Item $srcPath -Recurse -Force -ErrorAction SilentlyContinue
}

function Move-JavaTree([string]$projectDir) {
  $moved = 0
  foreach ($rel in @("src\main\java", "src\test\java", "src\generated\java")) {
    $javaRoot = Join-Path $projectDir $rel
    if (-not (Test-Path $javaRoot)) { continue }

    $creatopia = Join-Path $javaRoot "uk\creatopia"
    if (-not (Test-Path $creatopia)) { continue }

    $destRoot = Join-Path $javaRoot "uk\co\extraspecialstudio"
    New-Item -ItemType Directory -Force -Path $destRoot | Out-Null

    $unbound = Join-Path $creatopia "unbound"
    if (Test-Path $unbound) {
      Get-ChildItem $unbound -Force | ForEach-Object {
        Merge-Move $_.FullName (Join-Path $destRoot $_.Name)
        $moved++
      }
      Remove-Item $unbound -Recurse -Force -ErrorAction SilentlyContinue
    }

    Get-ChildItem $creatopia -Force -ErrorAction SilentlyContinue | ForEach-Object {
      Merge-Move $_.FullName (Join-Path $destRoot $_.Name)
      $moved++
    }

    Remove-Item $creatopia -Recurse -Force -ErrorAction SilentlyContinue
  }
  return $moved
}

function Process-Project([string]$projectDir) {
  $name = Split-Path $projectDir -Leaf
  Write-Host "=== $name ===" -ForegroundColor Cyan

  Update-GradleProperties $projectDir

  # Move Java tree before rewriting paths that reference file locations
  $moved = Move-JavaTree $projectDir
  Write-Host "  moved package roots: $moved"

  # Rewrite text files under project (limited roots)
  $roots = @(
    (Join-Path $projectDir "src"),
    (Join-Path $projectDir "build.gradle"),
    (Join-Path $projectDir "settings.gradle"),
    (Join-Path $projectDir "gradle.properties")
  )
  # also top-level md that may have packages
  $changed = 0
  Get-ChildItem $projectDir -File -ErrorAction SilentlyContinue | Where-Object {
    $_.Extension -in @('.gradle', '.kts', '.md', '.properties', '.toml')
  } | ForEach-Object {
    if (Update-TextFile $_.FullName) { $changed++ }
  }

  if (Test-Path (Join-Path $projectDir "src")) {
    Get-ChildItem (Join-Path $projectDir "src") -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
      if (Should-SkipPath $_.FullName) { return }
      if (Update-TextFile $_.FullName) { $changed++ }
    }
  }

  # embedded-jlayer / subprojects
  Get-ChildItem $projectDir -Directory -ErrorAction SilentlyContinue | Where-Object {
    $_.Name -notin @('build', '.gradle', '.git', 'run', 'bin', 'node_modules') -and (Test-Path (Join-Path $_.FullName 'build.gradle'))
  } | ForEach-Object {
    $sub = $_.FullName
    if (Test-Path (Join-Path $sub "src")) {
      $null = Move-JavaTree $sub
      Get-ChildItem (Join-Path $sub "src") -Recurse -File | ForEach-Object {
        if (Update-TextFile $_.FullName) { $changed++ }
      }
    }
    Get-ChildItem $sub -File | Where-Object { $_.Extension -in @('.gradle', '.properties') } | ForEach-Object {
      if (Update-TextFile $_.FullName) { $changed++ }
    }
  }

  Write-Host "  text files updated: $changed"
}

# Process all
foreach ($p in $Projects) {
  Process-Project $p.FullName
}

Write-Host "`nDONE. Projects processed: $($Projects.Count)" -ForegroundColor Green
