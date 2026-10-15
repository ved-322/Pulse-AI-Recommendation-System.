# Downloads freely-licensed product photos from Wikimedia Commons into frontend/img/<itemId>.jpg
# and writes docs/IMAGE_CREDITS.md. Item ids follow the order of database/seed.sql (1..48).
# Books (7-12) and movies (13-18) are skipped: their cover art is copyrighted, so the UI keeps its illustrated fallback.
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$root = Split-Path $PSScriptRoot -Parent
$out = Join-Path $root 'frontend\img'
New-Item -ItemType Directory -Force $out | Out-Null
$ua = @{ 'User-Agent' = 'PulseRecsysStudentProject/1.0 (educational; contact: project team)' }

# id | search query | regex the file title must match
$items = @(
  @(1,'Sony WH-1000XM5','XM5|1000X'), @(2,'Apple Watch Series 10','Apple Watch'), @(3,'Keychron K2 keyboard','Keychron'),
  @(4,'Logitech Brio webcam','Brio|Logitech'), @(5,'Samsung T7 Portable SSD','T7|SSD'), @(6,'Amazon Echo Dot','Echo Dot'),
  @(19,'denim trucker jacket Levi''s','denim|jacket|Levi'), @(20,'Nike Pegasus running shoe','Pegasus|Nike'),
  @(21,'Herschel Little America backpack','Herschel'), @(22,'Carhartt Detroit jacket','Carhartt'),
  @(23,'Ray-Ban Wayfarer sunglasses','Wayfarer|Ray-Ban'), @(24,'linen shirt','linen|shirt'),
  @(25,'iRobot Roomba j7','Roomba'), @(26,'Herman Miller Aeron chair','Aeron'), @(27,'Hario V60 dripper','V60|Hario'),
  @(28,'aroma diffuser Muji','diffuser'), @(29,'Lodge cast iron skillet','skillet|cast.iron'),
  @(30,'BenQ ScreenBar monitor light','ScreenBar|BenQ'), @(31,'Bowflex SelectTech dumbbells','Bowflex|dumbbell'),
  @(32,'yoga mat','yoga'), @(33,'Hoka trail running shoe','Hoka|Speedgoat'), @(34,'TRX suspension trainer','TRX|suspension'),
  @(35,'Hydro Flask bottle','Hydro'), @(36,'foam roller','foam roller'), @(37,'coffee beans bag Blue Bottle','Blue Bottle|coffee'),
  @(38,'matcha powder','matcha'), @(39,'Godiva chocolate','Godiva|chocolate'), @(40,'olive oil bottle','olive'),
  @(41,'granola','granola'), @(42,'Cholula hot sauce','Cholula|hot sauce'), @(43,'Xbox Wireless Controller','Xbox'),
  @(44,'Catan board game','Catan'), @(45,'Meta Quest 3','Quest'), @(46,'Logitech G502 mouse','G502|Logitech'),
  @(47,'Analogue Pocket handheld','Analogue|Pocket'), @(48,'Monument Valley game','Monument')
)
$credits = @("# Image credits", "", "Product photos are from [Wikimedia Commons](https://commons.wikimedia.org) under the licences shown. Items marked *generic* use a photo of the product type where no free photo of the exact model exists.", "", "| Item id | File | Author | Licence | Source |", "|---|---|---|---|---|")
$ok = 0
foreach ($it in $items) {
  $id = $it[0]; $q = $it[1]; $must = $it[2]
  $url = 'https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit=12&gsrsearch=' +
         [uri]::EscapeDataString($q) + '&prop=imageinfo&iiprop=url|extmetadata|size|mime&iiurlwidth=640'
  $r = $null
  for ($try = 1; $try -le 5 -and -not $r; $try++) {
    try { $r = Invoke-RestMethod $url -Headers $ua }
    catch { Write-Host "[$id] retry $try ($($_.Exception.Message))"; Start-Sleep -Seconds (8 * $try) }
  }
  if (-not $r) { Write-Host "[$id] search failed"; continue }
  if (-not $r.query) { Write-Host "[$id] no results for '$q'"; continue }
  $cands = $r.query.pages.PSObject.Properties.Value | Sort-Object index | Where-Object {
    $ii = $_.imageinfo[0]; $_.title -match $must -and $_.title -notmatch 'motherboard|teardown|PCB|chip|box|logo|diagram|screenshot|box art|poster' `
      -and $ii.mime -in 'image/jpeg','image/png' -and $ii.width -ge 500 -and $ii.thumburl }
  $pick = $cands | Select-Object -First 1
  if (-not $pick) { Write-Host "[$id] no usable match for '$q'"; continue }
  $ii = $pick.imageinfo[0]
  $saved = $false
  for ($try = 1; $try -le 4 -and -not $saved; $try++) {
    try { Invoke-WebRequest $ii.thumburl -Headers $ua -OutFile (Join-Path $out "$id.jpg") -UseBasicParsing; $saved = $true }
    catch { Start-Sleep -Seconds (6 * $try) }
  }
  if (-not $saved) { Write-Host "[$id] download failed"; continue }
  $meta = $ii.extmetadata
  $artist = ($meta.Artist.value -replace '<[^>]+>', '').Trim(); if (-not $artist) { $artist = 'Unknown' }
  $lic = $meta.LicenseShortName.value
  $credits += "| $id | ``$id.jpg`` | $($artist -replace '\|','/') | $lic | [$($pick.title)]($($ii.descriptionurl)) |"
  Write-Host "[$id] $($pick.title)"
  $ok++
  Start-Sleep -Seconds 3
}
Set-Content (Join-Path $root 'docs\IMAGE_CREDITS.md') $credits -Encoding UTF8
Write-Host "Downloaded $ok images."
