# Fetches REAL movie posters (TMDB) and book covers (Open Library) plus their public ratings.
#   .\scripts\fetch-media.ps1 -TmdbKey <your free TMDB API key>            # movies + books
#   .\scripts\fetch-media.ps1 -TmdbKey <key> -Only movies                   # movies only
#   .\scripts\fetch-media.ps1 -Only books                                   # books only (no key needed)
# Writes frontend/img/<slug>.jpg, frontend/data/public.json (rating, votes, overview) and refreshes img/index.json.
# Attribution: "This product uses the TMDB API but is not endorsed or certified by TMDB." Book data: Open Library (openlibrary.org).
param([string]$TmdbKey = '', [ValidateSet('all', 'movies', 'books')][string]$Only = 'all')
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$root = Split-Path $PSScriptRoot -Parent
$img = Join-Path $root 'frontend\img'; $dataDir = Join-Path $root 'frontend\data'
New-Item -ItemType Directory -Force $img, $dataDir | Out-Null
$ua = @{ 'User-Agent' = 'PulseRecsysStudentProject/1.0 (educational)' }
$pubFile = Join-Path $dataDir 'public.json'
$public = @{}
if (Test-Path $pubFile) { (Get-Content $pubFile -Raw | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $public[$_.Name] = $_.Value } }

function Slug($name, $meta) { $s = ($name.ToLower() -replace '[^a-z0-9]+', '-').Trim('-'); if ($meta) { "$s-$meta" } else { $s } }
function Rows($file) {
  Get-Content (Join-Path $root "database\catalogue\$file") -Encoding UTF8 | Where-Object { $_ -and $_ -notmatch '^#' } | ForEach-Object {
    $f = $_.Split('|'); [pscustomobject]@{ Brand = $f[0]; Name = $f[1]; Meta = $f[4]; Slug = (Slug $f[1] $f[4]) } }
}
function Save($url, $slug) {
  for ($t = 1; $t -le 3; $t++) { try { Invoke-WebRequest $url -Headers $ua -OutFile (Join-Path $img "$slug.jpg") -UseBasicParsing; return $true } catch { Start-Sleep -Seconds (2 * $t) } }
  return $false
}

if ($Only -in 'all', 'movies') {
  if (-not $TmdbKey) { throw 'Movies need -TmdbKey (free at themoviedb.org/settings/api).' }
  foreach ($m in Rows '08-movies.tsv') {
    $q = [uri]::EscapeDataString($m.Name)
    $r = Invoke-RestMethod "https://api.themoviedb.org/3/search/movie?api_key=$TmdbKey&query=$q&year=$($m.Meta)" -Headers $ua
    $hit = $r.results | Where-Object poster_path | Select-Object -First 1
    if (-not $hit) { $r = Invoke-RestMethod "https://api.themoviedb.org/3/search/tv?api_key=$TmdbKey&query=$q" -Headers $ua; $hit = $r.results | Where-Object poster_path | Select-Object -First 1 }
    if (-not $hit) { Write-Host "[movie] no match: $($m.Name)"; continue }
    if (Save "https://image.tmdb.org/t/p/w500$($hit.poster_path)" $m.Slug) {
      $public[$m.Slug] = @{ source = 'TMDB'; rating = [math]::Round($hit.vote_average, 1); votes = $hit.vote_count; overview = $hit.overview }
      Write-Host "[movie] $($m.Name) -> $($hit.vote_average)"
    }
    Start-Sleep -Milliseconds 300
  }
}

if ($Only -in 'all', 'books') {
  foreach ($b in Rows '07-books.tsv') {
    $author = ($b.Brand -split '&')[0].Trim()
    $u = 'https://openlibrary.org/search.json?limit=1&fields=key,cover_i&title=' + [uri]::EscapeDataString($b.Name) + '&author=' + [uri]::EscapeDataString($author)
    try { $r = Invoke-RestMethod $u -Headers $ua } catch { Write-Host "[book] search failed: $($b.Name)"; continue }
    $doc = $r.docs | Select-Object -First 1
    if (-not $doc -or -not $doc.cover_i) { Write-Host "[book] no cover: $($b.Name)"; continue }
    if (Save "https://covers.openlibrary.org/b/id/$($doc.cover_i)-L.jpg" $b.Slug) {
      try {
        $rt = Invoke-RestMethod "https://openlibrary.org$($doc.key)/ratings.json" -Headers $ua
        if ($rt.summary.count -gt 0) { $public[$b.Slug] = @{ source = 'Open Library'; rating = [math]::Round($rt.summary.average, 1); votes = $rt.summary.count } }
      } catch { }
      Write-Host "[book] $($b.Name)"
    }
    Start-Sleep -Milliseconds 600
  }
}

($public | ConvertTo-Json -Depth 4) | Set-Content $pubFile -Encoding UTF8
$slugs = Get-ChildItem $img -Filter *.jpg | ForEach-Object BaseName | Sort-Object
(ConvertTo-Json @($slugs) -Compress) | Set-Content (Join-Path $img 'index.json') -Encoding ASCII
Write-Host "Done. $($slugs.Count) photos indexed."
