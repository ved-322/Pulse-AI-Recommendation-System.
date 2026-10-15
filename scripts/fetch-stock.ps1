# Fills every product that still has no picture with a free stock photo that matches its type.
# These are representative photos (e.g. headphones for a headphone model), NOT the exact model.
#   .\scripts\fetch-stock.ps1 -Provider pexels  -Key <PEXELS_KEY>   # free key: pexels.com/api  (200 requests/hour)
#   .\scripts\fetch-stock.ps1 -Provider pixabay -Key <PIXABAY_KEY>  # free key: pixabay.com/api/docs (fast, no hourly cap for this size)
# Resumable. Writes frontend/img/<slug>.jpg, refreshes img/index.json and appends credits to scripts/picture-log.tsv.
param([Parameter(Mandatory)][ValidateSet('pexels', 'pixabay')][string]$Provider, [Parameter(Mandatory)][string]$Key)
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$root = Split-Path $PSScriptRoot -Parent
$img = Join-Path $root 'frontend\img'
$logFile = Join-Path $PSScriptRoot 'picture-log.tsv'
$ua = @{ 'User-Agent' = 'PulseRecsysStudentProject/1.0 (educational project)' }
$delay = if ($Provider -eq 'pexels') { 19000 } else { 900 }     # pexels allows 200 requests per hour

function Slug($name, $meta) { $s = ($name.ToLower() -replace '[^a-z0-9]+', '-').Trim('-'); if ($meta) { "$s-$meta" } else { $s } }
function Search($q) {
  $e = [uri]::EscapeDataString($q)
  try {
    if ($Provider -eq 'pexels') {
      $r = Invoke-RestMethod "https://api.pexels.com/v1/search?query=$e&per_page=1&size=medium" -Headers @{ Authorization = $Key; 'User-Agent' = $ua['User-Agent'] } -TimeoutSec 40
      $h = $r.photos | Select-Object -First 1
      if ($h) { return @{ url = $h.src.medium; page = $h.url; who = $h.photographer } }
    } else {
      $r = Invoke-RestMethod "https://pixabay.com/api/?key=$Key&q=$e&image_type=photo&per_page=3&safesearch=true&order=popular" -Headers $ua -TimeoutSec 40
      $h = $r.hits | Select-Object -First 1
      if ($h) { return @{ url = $h.webformatURL; page = $h.pageURL; who = $h.user } }
    }
  } catch { Write-Host "  api error: $($_.Exception.Message)"; Start-Sleep -Seconds 20 }
  return $null
}
$generic = 'the', 'and', 'with', 'for', 'pro', 'plus', 'gen', 'generation', 'new', 'set', 'edition'
$files = Get-Content (Join-Path $root 'database\catalogue\index.txt') | Where-Object { $_ -and $_ -notmatch 'books|movies' }
$n = 0
foreach ($f in $files) {
  $cat = ((Get-Content (Join-Path $root "database\catalogue\$f") -Encoding UTF8 | Select-Object -First 1) -replace '^#category=|\|emoji=.*$', '')
  foreach ($line in (Get-Content (Join-Path $root "database\catalogue\$f") -Encoding UTF8 | Where-Object { $_ -and $_ -notmatch '^#' })) {
    $c = $line.Split('|'); $slug = Slug $c[1] $c[4]
    if (Test-Path (Join-Path $img "$slug.jpg")) { continue }
    $words = ($c[1] -replace '\(.*?\)', '' -replace '[^A-Za-z0-9 ]', ' ').Split(' ', [StringSplitOptions]::RemoveEmptyEntries) | Where-Object { $_.ToLower() -notin $generic }
    $type = ($words | Select-Object -Last 2) -join ' '
    $hit = Search "$($c[0]) $($c[1])"
    if (-not $hit) { Start-Sleep -Milliseconds $delay; $hit = Search $type }
    if (-not $hit) { Start-Sleep -Milliseconds $delay; $hit = Search "$($cat -replace '&', '') product" }
    if ($hit) {
      try { Invoke-WebRequest $hit.url -Headers $ua -OutFile (Join-Path $img "$slug.jpg") -UseBasicParsing -TimeoutSec 60
        "$slug`t$Provider stock photo by $($hit.who) (representative image)`t$($hit.page)" | Add-Content $logFile -Encoding UTF8; $n++; Write-Host "[$n] $($c[0]) $($c[1])" } catch { Write-Host "download failed: $slug" }
    } else { Write-Host "none: $($c[1])" }
    Start-Sleep -Milliseconds $delay
  }
}
$slugs = Get-ChildItem $img -Filter *.jpg | ForEach-Object BaseName | Sort-Object
(ConvertTo-Json @($slugs) -Compress) | Set-Content (Join-Path $img 'index.json') -Encoding ASCII
Write-Host "Done. Added $n pictures; $($slugs.Count) pictures indexed."
