# Resize mod PNGs to <= 250 KiB for Modrinth (256 KiB limit).
param(
  [string[]]$Ids = @("after-hours-fm", "beat-blocks", "broken-youth-radio", "iron-rain-fm"),
  [string]$Dir = "C:\Users\Ksivi\IdeaProjects\ess-website\public\images\mods",
  [int]$MaxBytes = 250 * 1024
)

Add-Type -AssemblyName System.Drawing

function Save-PngBytes([System.Drawing.Bitmap]$bmp, [int]$MaxBytes) {
  $sizes = @(256, 192, 128, 96, 64)
  foreach ($size in $sizes) {
    $out = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($bmp, 0, 0, $size, $size)
    $g.Dispose()

    $ms = New-Object System.IO.MemoryStream
    $out.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $bytes = $ms.ToArray()
    $ms.Dispose()
    $out.Dispose()

    if ($bytes.Length -le $MaxBytes) {
      return @{ Bytes = $bytes; Size = $size }
    }
  }
  return $null
}

$logosDir = "C:\Users\Ksivi\Desktop\Extra Special Studio\Minecraft\logos"
New-Item -ItemType Directory -Force -Path $logosDir | Out-Null

foreach ($id in $Ids) {
  $path = Join-Path $Dir "$id.png"
  if (-not (Test-Path $path)) {
    Write-Warning "Missing $path"
    continue
  }

  $before = (Get-Item $path).Length
  $backup = "$path.bak"
  if (-not (Test-Path $backup)) {
    Copy-Item $path $backup
  }

  $img = [System.Drawing.Bitmap]::FromFile($path)
  $result = Save-PngBytes $img $MaxBytes
  $img.Dispose()

  if (-not $result) {
    Write-Warning "Could not shrink $id under $MaxBytes bytes"
    continue
  }

  $tmp = "$path.tmp"
  [System.IO.File]::WriteAllBytes($tmp, $result.Bytes)
  Move-Item -Force $tmp $path
  $after = (Get-Item $path).Length
  Write-Output "$id : $([math]::Round($before/1KB))KiB -> $([math]::Round($after/1KB))KiB (${($result.Size)}px)"

  $logoName = switch ($id) {
    "after-hours-fm" { "dead_air_after_hours_fm.png" }
    "beat-blocks" { "dead_air_beat_blocks.png" }
    "broken-youth-radio" { "dead_air_broken_youth_radio.png" }
    "iron-rain-fm" { "dead_air_iron_rain_fm.png" }
    default { "$id.png" }
  }
  Copy-Item $path (Join-Path $logosDir $logoName) -Force
}
