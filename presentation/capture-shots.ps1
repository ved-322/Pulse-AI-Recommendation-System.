# Captures real screenshots of the running site (http://localhost:8080) with headless Chrome via the DevTools protocol.
# Output: presentation/shots/*.png   (used by presentation.html -> PDF)
param([string]$Base = 'http://localhost:8080')
$out = Join-Path $PSScriptRoot 'shots'; New-Item -ItemType Directory -Force $out | Out-Null
$chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
$profile = Join-Path $env:TEMP ('recsys-shots-' + [guid]::NewGuid().ToString('N'))
$proc = Start-Process $chrome -ArgumentList '--headless=new', '--remote-debugging-port=9340', "--user-data-dir=$profile", '--hide-scrollbars', 'about:blank' -PassThru
Start-Sleep 3
$tabs = Invoke-RestMethod 'http://127.0.0.1:9340/json'
$wsUrl = ($tabs | Where-Object type -eq 'page' | Select-Object -First 1).webSocketDebuggerUrl
$ws = New-Object Net.WebSockets.ClientWebSocket
$ws.ConnectAsync([uri]$wsUrl, [Threading.CancellationToken]::None).Wait()
$script:id = 0

function Cdp($method, $params = @{}) {
  $script:id++; Write-Host "  > $method"
  $json = @{ id = $script:id; method = $method; params = $params } | ConvertTo-Json -Depth 12 -Compress
  $bytes = [Text.Encoding]::UTF8.GetBytes($json)
  $ws.SendAsync([ArraySegment[byte]]$bytes, 'Text', $true, [Threading.CancellationToken]::None).Wait()
  $deadline = [DateTime]::Now.AddSeconds(30)
  while ([DateTime]::Now -lt $deadline) {
    $sb = New-Object Text.StringBuilder
    $buf = New-Object byte[] 4194304
    do {
      $t = $ws.ReceiveAsync([ArraySegment[byte]]$buf, [Threading.CancellationToken]::None)
      if (-not $t.Wait(25000)) { Write-Host "  TIMEOUT on $method"; return $null }
      $r = $t.Result
      [void]$sb.Append([Text.Encoding]::UTF8.GetString($buf, 0, $r.Count))
    } until ($r.EndOfMessage)
    $msg = $sb.ToString() | ConvertFrom-Json
    if ($msg.id -eq $script:id) { return $msg.result }
  }
  Write-Host "  TIMEOUT on $method"; return $null
}function Js($expr) {
  if ($expr -notlike 'fetch*') { $expr = "(function(){ $expr ; return 1 })()" }
  $r = Cdp 'Runtime.evaluate' @{ expression = $expr; awaitPromise = $true; returnByValue = $true }
  if ($r -and $r.exceptionDetails) { Write-Host ('  js error: ' + $r.exceptionDetails.exception.description) }
  if ($r) { $r.result.value }
}
function JsVal($expr) { $r = Cdp 'Runtime.evaluate' @{ expression = $expr; awaitPromise = $true; returnByValue = $true }; if ($r) { $r.result.value } }
function Size($w, $h) { [void](Cdp 'Emulation.setDeviceMetricsOverride' @{ width = $w; height = $h; deviceScaleFactor = 1; mobile = ($w -lt 600) }) }
function Go($url, $wait = 2500) {
  $stamp = [DateTime]::Now.Ticks
  if ($url -like '*/#*') { $u = $url.Replace('/#', "/?t=$stamp#") } else { $u = "${url}?t=$stamp" }
  [void](Cdp 'Page.navigate' @{ url = $u }); Start-Sleep -Milliseconds $wait
}
function Shot($name, $clipY = $null, $clipH = 900, $w = 1440) {
  $p = @{ format = 'png' }
  if ($null -ne $clipY) { $p.captureBeyondViewport = $true; $p.clip = @{ x = 0; y = [double]$clipY; width = [double]$w; height = [double]$clipH; scale = 1 } }
  $r = Cdp 'Page.captureScreenshot' $p
  if ($r) { [IO.File]::WriteAllBytes((Join-Path $out "$name.png"), [Convert]::FromBase64String($r.data)); Write-Host "saved $name" } else { Write-Host "FAILED $name" }
}
function Login($email, $pw) {
  Go "$Base/" 3500
  $s = Js "fetch('$Base/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:'$email',password:'$pw'})}).then(r=>r.status)"
  Write-Host "login $email -> $s"
}
[void](Cdp 'Page.enable')

# 1. login page (signed out)
Size 1440 860; Go "$Base/" 3000; Shot '01-login'

# user: set a clear taste profile through the real API, then capture (tall pages are cropped later in the slides)
Login 'alice@example.com' 'User@123'
[void](Js "fetch('$Base/api/preferences',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:'Alice Demo',categories:{'Phones & Tablets':5,'Laptops & PCs':4,'Audio':4,'Gaming':3,'Books':4,'Movies':3},tags:['smartphone','wireless','sci-fi','gaming']})}).then(r=>r.status)")
Size 1440 1000; Go "$Base/#/discover" 5000; Shot '02-discover-studio'
Size 1440 2300; Go "$Base/#/discover" 5000; Shot '03-discover-full'
Size 1440 1500; Go "$Base/#/explore" 3500
[void](Js "[...document.querySelectorAll('.chip')].find(c=>c.textContent.includes('Movies')).click()"); Start-Sleep 2500; Shot '04-explore-movies'
[void](Js "[...document.querySelectorAll('.chip')].find(c=>c.textContent.includes('Books')).click()"); Start-Sleep 2500; Shot '05-explore-books'
Size 1440 1100
[void](Js "[...document.querySelectorAll('.item-card')].find(c=>c.textContent.includes('Atomic Habits')).click()"); Start-Sleep 2800; Shot '06-product-reviews'
Size 390 844; Go "$Base/#/discover" 5000; Shot '07-mobile'

# admin
Login 'admin@recsys.com' 'Admin@123'
Size 1440 1500; Go "$Base/#/admin" 5000; Shot '08-admin-analytics'
Size 1440 900; Go "$Base/#/admin/settings" 3500; Shot '09-admin-settings'
Size 1440 900; Go "$Base/#/admin/users" 3500; Shot '10-admin-users'

$ws.Dispose(); Stop-Process $proc -Force -ErrorAction SilentlyContinue
Write-Host 'done'
