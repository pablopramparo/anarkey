$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$sourceDir = Join-Path $projectRoot 'design-assets'
$resourceDir = Join-Path $projectRoot 'app/src/main/res/drawable-nodpi'
New-Item -ItemType Directory -Force -Path $resourceDir | Out-Null
foreach ($asset in @(
    @{ Name = 'anarkey_ico'; Size = '512x512'; Output = 'brand_app_icon' },
    @{ Name = 'anarkey_iso'; Size = '256x256'; Output = 'brand_iso' },
    @{ Name = 'anarkey_logo'; Size = '1568x384'; Output = 'brand_logo' },
    @{ Name = 'anarkey_logo_slogan'; Size = '1568x384'; Output = 'brand_logo_slogan' }
)) {
    & magick (Join-Path $sourceDir ($asset.Name + '.png')) -resize $asset.Size (Join-Path $resourceDir ($asset.Output + '.png'))
    if ($LASTEXITCODE -ne 0) { throw "Asset conversion failed: $($asset.Name)" }
}
# Android small notification icons use an alpha silhouette, not a full-color bitmap.
$maskPath = Join-Path $projectRoot '.tools/brand_notification_alpha.png'
New-Item -ItemType Directory -Force -Path (Split-Path $maskPath -Parent) | Out-Null
& magick (Join-Path $sourceDir 'anarkey_iso.png') -resize 72x72 -alpha extract -threshold 50% -background black -gravity center -extent 96x96 $maskPath
if ($LASTEXITCODE -ne 0) { throw 'Notification alpha conversion failed' }
& magick -size 96x96 xc:white $maskPath -alpha off -compose CopyOpacity -composite (Join-Path $resourceDir 'brand_notification.png')
if ($LASTEXITCODE -ne 0) { throw 'Notification icon conversion failed' }
