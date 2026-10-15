# Second pass for categories that still lack pictures. Free sources only, no API key:
#   Food & Drink      -> Open Food Facts   (openfoodfacts.org, CC BY-SA product photos)
#   Beauty & Wellness -> Open Beauty Facts (openbeautyfacts.org, CC BY-SA product photos)
#   everything else   -> Wikimedia Commons file search, accepted only when the file name matches brand + product
# Resumable (skips existing files). Appends to scripts/picture-log.tsv, refreshes frontend/img/index.json.
param([ValidateSet('all', 'food', 'beauty', 'commons')][string]$Only = 'all', [string]$CategoryFile = '', [switch]$Relaxed)
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$root = Split-Path $PSScriptRoot -Parent
$img = Join-Path $root 'frontend\img'
$ua = @{ 'User-Agent' = 'PulseRecsysStudentProject/1.0 (educational project)' }
$logFile = Join-Path $PSScriptRoot 'picture-log.tsv'

function Slug($name, $meta) { $s = ($name.ToLower() -replace '[^a-z0-9]+', '-').Trim('-'); if ($meta) { "$s-$meta" } else { $s } }
function Rows($file) {
  Get-Content (Join-Path $root "database\catalogue\$file") -Encoding UTF8 | Where-Object { $_ -and $_ -notmatch '^#' } | ForEach-Object {
    $f = $_.Split('|'); [pscustomobject]@{ Brand = $f[0]; Name = $f[1]; Meta = $f[4]; Slug = (Slug $f[1] $f[4]) } }
}
function Get-Json($url) {
  for ($t = 1; $t -le 4; $t++) {
    try { return Invoke-RestMethod $url -Headers $ua -TimeoutSec 40 } catch { if ($_.Exception.Message -match '404') { return $null }; Start-Sleep -Seconds (15 * $t) }
  }
  return $null
}
function Save($url, $slug) {
  for ($t = 1; $t -le 3; $t++) { try { Invoke-WebRequest $url -Headers $ua -OutFile (Join-Path $img "$slug.jpg") -UseBasicParsing -TimeoutSec 60; return $true } catch { Start-Sleep -Seconds (6 * $t) } }
  return $false
}
$stop = 'the', 'and', 'of', 'for', 'with', 'in', 'a', 'an', 'by', 'pro', 'plus', 'edition', 'new', 'gen', 'generation', 'set', 'kit', 'original', 'classic'
function Tokens($s) { ($s.ToLower() -replace '[^a-z0-9 ]+', ' ').Split(' ', [StringSplitOptions]::RemoveEmptyEntries) | Where-Object { $_ -notin $stop } }
$generic = 'headphones','earbuds','speaker','speakers','watch','camera','mouse','keyboard','laptop','jacket','shoes','shoe','bottle','mat','backpack','lens','tablet','phone','smartphone','console','controller','headset','microphone','monitor','light','lamp','chair','pan','skillet','cooker','mixer','kettle','tumbler','set','game','board','card','games','puzzle','guitar','ukulele','keyboard','telescope','pen','pencils','notebook','trainer','roller','shorts','tent','bag','wallet','sunglasses','shirt','jeans','sneakers','sandals','clog','coffee','tea','beans','powder','oil','sauce','chocolate','bar','cream','lotion','serum','cleanser','shaver','toothbrush','dryer','purifier','vacuum','robot','plug','bulb','display','hub','band','ring','tracker','stick','dumbbells','dumbbell','rower','racket','shuttlecocks','football','cricket','bat','goggles','filter','headlamp','pad','stove','system','tool','multi','insulated','wireless','portable','electric'
function Log($slug, $source, $page) { "$slug`t$source`t$page" | Add-Content $logFile -Encoding UTF8 }
function Missing($rows) { $rows | Where-Object { -not (Test-Path (Join-Path $img "$($_.Slug).jpg")) } }

# ---------- Open Food Facts / Open Beauty Facts ----------
function Facts($host_, $file, $label) {
  foreach ($p in Missing (Rows $file)) {
    $q = [uri]::EscapeDataString("$($p.Brand) $($p.Name)")
    $r = Get-Json "https://$host_/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1&page_size=6&fields=product_name,brands,image_front_url,code"
    $nameTok = @(Tokens $p.Name); $brandTok = @(Tokens $p.Brand) | Select-Object -First 1
    $hit = $r.products | Where-Object {
      $_.image_front_url -and $_.brands -and (($_.brands.ToLower() -replace '[^a-z0-9 ]', '') -like "*$brandTok*") -and
      (@(Tokens $_.product_name | Where-Object { $_ -in $nameTok }).Count -ge [math]::Max(1, [math]::Ceiling($nameTok.Count * 0.5))) } | Select-Object -First 1
    if ($hit -and (Save $hit.image_front_url $p.Slug)) { Log $p.Slug $label "https://$host_/product/$($hit.code)"; Write-Host "[$label] $($p.Brand) $($p.Name) <- $($hit.product_name)" }
    else { Write-Host "[$label] none: $($p.Brand) $($p.Name)" }
    Start-Sleep -Milliseconds 1200
  }
}
if ($Only -in 'all', 'food') { Facts 'world.openfoodfacts.org' '12-food-drink.tsv' 'Open Food Facts' }
if ($Only -in 'all', 'beauty') { Facts 'world.openbeautyfacts.org' '13-beauty-wellness.tsv' 'Open Beauty Facts' }

# ---------- Wikimedia Commons ----------
if ($Only -in 'all', 'commons') {
  $files = (Get-Content (Join-Path $root 'database\catalogue\index.txt')) | Where-Object { $_ -and $_ -notmatch 'books|movies' }
  if ($CategoryFile) { $files = $files | Where-Object { $_ -eq $CategoryFile } }
  foreach ($f in $files) {
    foreach ($p in Missing (Rows $f)) {
      $nameTok = @(Tokens $p.Name); $brandTok = @(Tokens $p.Brand) | Select-Object -First 1
      $q = "$($p.Brand) $($p.Name)"
      if ($Relaxed) {
        # drop generic nouns ("Headphones", "Speaker" ...) and require a model token (with a digit) to match when the name has one
        $nameTok = @($nameTok | Where-Object { $_ -notin $generic })
        if ($nameTok.Count -eq 0) { Write-Host "[commons] skip generic: $($p.Name)"; continue }
        $q = "$($p.Brand) $($nameTok -join ' ')"
      }
      $u = 'https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit=10&prop=imageinfo&iiprop=url|mime|size&iiurlwidth=640&gsrsearch=' + [uri]::EscapeDataString($q)
      $r = Get-Json $u
      $hit = $null
      if ($r -and $r.query) {
        $hit = $r.query.pages.PSObject.Properties.Value | Sort-Object index | Where-Object {
          $ii = $_.imageinfo[0]; $t = ($_.title -replace '^File:', '' -replace '\.[A-Za-z]+$', '')
          $tt = @(Tokens $t)
          $ii.mime -in 'image/jpeg', 'image/png' -and $ii.width -ge 450 -and $ii.thumburl -and
          $t -notmatch 'logo|diagram|screenshot|box art|teardown|motherboard|PCB|chip|schematic|icon|svg|map|chart|advert|poster|vector' -and
          $brandTok -and ($tt -contains $brandTok) -and (@($nameTok | Where-Object { $_ -in $tt }).Count -ge [math]::Max(1, [math]::Ceiling($nameTok.Count * 0.6))) } | Select-Object -First 1
      }
      if ($hit -and (Save $hit.imageinfo[0].thumburl $p.Slug)) { Log $p.Slug 'Wikimedia Commons' $hit.imageinfo[0].descriptionurl; Write-Host "[commons] $($p.Brand) $($p.Name) <- $($hit.title)" }
      else { Write-Host "[commons] none: $($p.Brand) $($p.Name)" }
      Start-Sleep -Milliseconds 3200
    }
  }
}

$slugs = Get-ChildItem $img -Filter *.jpg | ForEach-Object BaseName | Sort-Object
(ConvertTo-Json @($slugs) -Compress) | Set-Content (Join-Path $img 'index.json') -Encoding ASCII
Write-Host "Done. $($slugs.Count) pictures indexed."
