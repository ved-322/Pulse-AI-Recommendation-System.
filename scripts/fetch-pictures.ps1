# Downloads real pictures with NO API key:
#   books   -> Open Library covers (+ public reader ratings)
#   movies  -> Wikipedia film articles (lead image = poster)
#   others  -> Wikipedia article lead images, accepted only when the article title matches the brand / product name
# Resumable: existing files are skipped. Writes frontend/img/<slug>.jpg, img/index.json, data/public.json
# and scripts/picture-log.tsv (slug, source page) which docs/IMAGE_CREDITS.md is built from.
param([ValidateSet('all', 'books', 'movies', 'products')][string]$Only = 'all', [int]$Limit = 0)
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$root = Split-Path $PSScriptRoot -Parent
$img = Join-Path $root 'frontend\img'; $dataDir = Join-Path $root 'frontend\data'
New-Item -ItemType Directory -Force $img, $dataDir | Out-Null
$ua = @{ 'User-Agent' = 'PulseRecsysStudentProject/1.0 (educational project)' }
$logFile = Join-Path $PSScriptRoot 'picture-log.tsv'
$pubFile = Join-Path $dataDir 'public.json'
$public = @{}
if (Test-Path $pubFile) { (Get-Content $pubFile -Raw | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $public[$_.Name] = $_.Value } }
if (-not (Test-Path $logFile)) { "slug`tsource`tpage" | Set-Content $logFile -Encoding UTF8 }

function Slug($name, $meta) { $s = ($name.ToLower() -replace '[^a-z0-9]+', '-').Trim('-'); if ($meta) { "$s-$meta" } else { $s } }
function Rows($file) {
  Get-Content (Join-Path $root "database\catalogue\$file") -Encoding UTF8 | Where-Object { $_ -and $_ -notmatch '^#' } | ForEach-Object {
    $f = $_.Split('|'); [pscustomobject]@{ Brand = $f[0]; Name = $f[1]; Meta = $f[4]; Slug = (Slug $f[1] $f[4]) } }
}
function Get-Json($url) {
  for ($t = 1; $t -le 5; $t++) {
    try { return Invoke-RestMethod $url -Headers $ua } catch { if ($_.Exception.Message -match '404') { return $null }; Start-Sleep -Seconds (25 * $t) }
  }
  return $null
}
function Save($url, $slug) {
  for ($t = 1; $t -le 4; $t++) { try { Invoke-WebRequest $url -Headers $ua -OutFile (Join-Path $img "$slug.jpg") -UseBasicParsing; return $true } catch { Start-Sleep -Seconds (5 * $t) } }
  return $false
}
function Tokens($s) { ($s.ToLower() -replace '[^a-z0-9 ]+', ' ').Split(' ', [StringSplitOptions]::RemoveEmptyEntries) | Where-Object { $_ -notin 'the', 'and', 'of', 'for', 'with', 'in', 'a', 'an', 'by' } }
function Wiki-Search($q) {
  $u = 'https://en.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=0&gsrlimit=6&redirects=1&prop=pageimages&piprop=thumbnail&pilicense=any&pithumbsize=600&gsrsearch=' + [uri]::EscapeDataString($q)
  $r = Get-Json $u
  if (-not $r -or -not $r.query) { return @() }
  $r.query.pages.PSObject.Properties.Value | Sort-Object index
}
function Wiki-Titles($titles) {
  $u = 'https://en.wikipedia.org/w/api.php?action=query&format=json&redirects=1&prop=pageimages&piprop=thumbnail&pilicense=any&pithumbsize=600&titles=' + [uri]::EscapeDataString(($titles -join '|'))
  $r = Get-Json $u
  if (-not $r -or -not $r.query) { return @() }
  $order = @{}; for ($i = 0; $i -lt $titles.Count; $i++) { $order[$titles[$i]] = $i }
  $r.query.pages.PSObject.Properties.Value | Where-Object { $_.thumbnail } | Sort-Object { if ($order.ContainsKey($_.title)) { $order[$_.title] } else { 99 } }
}
function Test-FilmPage($page, $m, $want) {
  if (-not $page.thumbnail) { return $false }
  if ($page.title -match 'List of|Music of|accolades|soundtrack|box office|franchise|series|characters|Universe|trilogy') { return $false }
  if ($page.title -match '\(' -and $page.title -notmatch '\((\d{4} )?film\)') { return $false }
  # accept "Name", "Name (film)" or "Name (YYYY film)" only
  $base = ($page.title -replace '\s*\(.*\)$', '').ToLower() -replace '[^a-z0-9]', ''
  $name = ($m.Name).ToLower() -replace '[^a-z0-9]', ''
  if ($base -ne $name) { return $false }
  if ($page.title -match '\((\d{4}) film\)' -and $Matches[1] -ne $m.Meta) { return $false }
  return $true
}
function Test-ProductPage($page, $nameTok, $brandTok) {
  if (-not $page.thumbnail) { return $false }
  if ($page.thumbnail.source -notmatch '\.(jpe?g|png)') { return $false }
  if ($page.title -match 'List of|Comparison|History of') { return $false }
  $tt = @(Tokens $page.title)
  $overlap = @($nameTok | Where-Object { $_ -in $tt }).Count
  if ($brandTok -and ($tt -contains $brandTok) -and $overlap -ge [math]::Max(1, [math]::Ceiling($nameTok.Count * 0.5))) { return $true }
  return ($nameTok.Count -ge 3 -and $overlap -eq $nameTok.Count)
}
function Log($slug, $source, $page) { "$slug`t$source`t$page" | Add-Content $logFile -Encoding UTF8 }
function Take($rows) { if ($Limit -gt 0) { $rows | Select-Object -First $Limit } else { $rows } }

if ($Only -in 'all', 'books') {
  foreach ($b in Take (Rows '07-books.tsv')) {
    if (Test-Path (Join-Path $img "$($b.Slug).jpg")) { continue }
    $author = ($b.Brand -split '&')[0].Trim()
    $r = Get-Json ('https://openlibrary.org/search.json?limit=3&fields=key,title,cover_i&title=' + [uri]::EscapeDataString($b.Name) + '&author=' + [uri]::EscapeDataString($author))
    $doc = $r.docs | Where-Object cover_i | Select-Object -First 1
    if (-not $doc) { Write-Host "[book] none: $($b.Name)"; continue }
    if (Save "https://covers.openlibrary.org/b/id/$($doc.cover_i)-L.jpg" $b.Slug) {
      $rt = Get-Json "https://openlibrary.org$($doc.key)/ratings.json"
      if ($rt -and $rt.summary.count -gt 0) { $public[$b.Slug] = @{ source = 'Open Library'; rating = [math]::Round($rt.summary.average, 1); votes = $rt.summary.count } }
      Log $b.Slug 'Open Library' "https://openlibrary.org$($doc.key)"
      Write-Host "[book] $($b.Name)"
    }
    Start-Sleep -Milliseconds 700
  }
}

if ($Only -in 'all', 'movies') {
  foreach ($m in Take (Rows '08-movies.tsv')) {
    if (Test-Path (Join-Path $img "$($m.Slug).jpg")) { continue }
    $want = Tokens $m.Name
    $hit = Wiki-Titles @("$($m.Name) ($($m.Meta) film)", "$($m.Name) (film)", $m.Name) | Where-Object { Test-FilmPage $_ $m $want } | Select-Object -First 1
    if (-not $hit) { $hit = Wiki-Search "$($m.Name) $($m.Meta) film" | Where-Object { Test-FilmPage $_ $m $want } | Sort-Object { if ($_.title -match "\(($($m.Meta) )?film\)") { 0 } else { 1 } } | Select-Object -First 1 }
    if ($hit -and (Save $hit.thumbnail.source $m.Slug)) { Log $m.Slug 'Wikipedia (film poster, fair use)' "https://en.wikipedia.org/wiki/$($hit.title -replace ' ', '_')"; Write-Host "[movie] $($m.Name) <- $($hit.title)" }
    else { Write-Host "[movie] none: $($m.Name)" }
    Start-Sleep -Milliseconds 2500
  }
}

if ($Only -in 'all', 'products') {
  # Batched lookup: up to 40 exact article titles per request (far fewer API calls than searching one by one).
  $files = (Get-Content (Join-Path $root 'database\catalogue\index.txt')) | Where-Object { $_ -and $_ -notmatch 'books|movies' }
  $todo = @(); foreach ($f in $files) { foreach ($p in Take (Rows $f)) { if (-not (Test-Path (Join-Path $img "$($p.Slug).jpg"))) { $todo += $p } } }
  Write-Host "products to look up: $($todo.Count)"
  for ($k = 0; $k -lt $todo.Count; $k += 12) {
    $chunk = $todo[$k..([math]::Min($k + 11, $todo.Count - 1))]
    $cand = @{}                                   # requested title -> list of products
    foreach ($p in $chunk) { foreach ($t in @("$($p.Brand) $($p.Name)", $p.Name, "$($p.Name) ($($p.Brand))")) { $cand[$t] = $p } }
    $titles = @($cand.Keys)
    $u = 'https://en.wikipedia.org/w/api.php?action=query&format=json&redirects=1&prop=pageimages&piprop=thumbnail&pilicense=any&pithumbsize=600&titles=' + [uri]::EscapeDataString(($titles -join '|'))
    $r = Get-Json $u
    if (-not $r -or -not $r.query) { Write-Host '[batch] no response'; Start-Sleep -Seconds 20; continue }
    $norm = @{}; foreach ($n in @($r.query.normalized)) { if ($n) { $norm[$n.from] = $n.to } }
    $redir = @{}; foreach ($n in @($r.query.redirects)) { if ($n) { $redir[$n.from] = $n.to } }
    $byTitle = @{}; foreach ($pg in $r.query.pages.PSObject.Properties.Value) { $byTitle[$pg.title] = $pg }
    foreach ($p in $chunk) {
      $nameTok = @(Tokens $p.Name); $brandTok = @(Tokens $p.Brand) | Select-Object -First 1; $done = $false
      foreach ($t in @("$($p.Brand) $($p.Name)", $p.Name, "$($p.Name) ($($p.Brand))")) {
        $x = $t; if ($norm.ContainsKey($x)) { $x = $norm[$x] }; if ($redir.ContainsKey($x)) { $x = $redir[$x] }
        $pg = $byTitle[$x]
        if ($pg -and (Test-ProductPage $pg $nameTok $brandTok)) {
          if (Save $pg.thumbnail.source $p.Slug) { Log $p.Slug 'Wikipedia' "https://en.wikipedia.org/wiki/$($pg.title -replace ' ', '_')"; Write-Host "[product] $($p.Brand) $($p.Name) <- $($pg.title)"; $done = $true; Start-Sleep -Milliseconds 800; break }
        }
      }
      if (-not $done) { Write-Host "[product] none: $($p.Brand) $($p.Name)" }
    }
    Start-Sleep -Seconds 3
  }
}
($public | ConvertTo-Json -Depth 4) | Set-Content $pubFile -Encoding UTF8
$slugs = Get-ChildItem $img -Filter *.jpg | ForEach-Object BaseName | Sort-Object
(ConvertTo-Json @($slugs) -Compress) | Set-Content (Join-Path $img 'index.json') -Encoding ASCII
Write-Host "Done. $($slugs.Count) pictures indexed."
