# Minimal Chrome DevTools helper (headless Chrome must be running with --remote-debugging-port). Dot-source, then Connect.
$script:id = 0
function Connect($port = 9343) {
  $tabs = Invoke-RestMethod "http://127.0.0.1:$port/json"
  $script:ws = New-Object Net.WebSockets.ClientWebSocket
  $script:ws.ConnectAsync([uri](($tabs | Where-Object type -eq 'page' | Select-Object -First 1).webSocketDebuggerUrl), [Threading.CancellationToken]::None).Wait()
}
function Cdp($m, $pr = @{}) {
  $script:id++
  $j = @{ id = $script:id; method = $m; params = $pr } | ConvertTo-Json -Depth 8 -Compress
  $b = [Text.Encoding]::UTF8.GetBytes($j)
  $script:ws.SendAsync([ArraySegment[byte]]$b, 'Text', $true, [Threading.CancellationToken]::None).Wait()
  $deadline = [DateTime]::Now.AddSeconds(25)
  while ([DateTime]::Now -lt $deadline) {
    $sb = New-Object Text.StringBuilder; $buf = New-Object byte[] 4194304
    do {
      $t = $script:ws.ReceiveAsync([ArraySegment[byte]]$buf, [Threading.CancellationToken]::None)
      if (-not $t.Wait(20000)) { return $null }
      $r = $t.Result; [void]$sb.Append([Text.Encoding]::UTF8.GetString($buf, 0, $r.Count))
    } until ($r.EndOfMessage)
    $msg = $sb.ToString() | ConvertFrom-Json
    if ($msg.id -eq $script:id) { return $msg.result }
  }
  return $null
}
function E($x) { $r = Cdp 'Runtime.evaluate' @{ expression = $x; awaitPromise = $true; returnByValue = $true }; if ($r) { $r.result.value } }
function Size($w, $h) { [void](Cdp 'Emulation.setDeviceMetricsOverride' @{ width = $w; height = $h; deviceScaleFactor = 1; mobile = ($w -lt 600) }) }
function Go($url, $wait = 3000) { [void](Cdp 'Page.navigate' @{ url = $url }); Start-Sleep -Milliseconds $wait }
function Shot($name, $dir) {
  $r = Cdp 'Page.captureScreenshot' @{ format = 'png' }
  if ($r) { [IO.File]::WriteAllBytes((Join-Path $dir "$name.png"), [Convert]::FromBase64String($r.data)); "saved $name" } else { "FAILED $name" }
}
