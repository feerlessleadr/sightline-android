<#
.SYNOPSIS
  Logs in to a Blue Iris server and saves sample API responses to a folder OUTSIDE the repo,
  so the app's JSON models can be written against real data.

.DESCRIPTION
  Run it yourself. It asks for the password with a hidden prompt, never writes it anywhere,
  and strips the session token from the saved files. Samples contain camera names, so keep
  them out of the repo (the default output is under %TEMP%).

.EXAMPLE
  .\Sample-BlueIris.ps1 -BaseUrl https://blueiris.example.com -User test
#>
param(
  [Parameter(Mandatory)][string]$BaseUrl,
  [Parameter(Mandatory)][string]$User,
  [string]$OutDir = (Join-Path $env:TEMP 'sightline-samples')
)

$ErrorActionPreference = 'Stop'
if ($BaseUrl -notmatch '^https://') { throw 'HTTPS only. Use an https:// address.' }
$BaseUrl = $BaseUrl.TrimEnd('/')
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Get-Md5Hex([string]$text) {
  $md5 = [System.Security.Cryptography.MD5]::Create()
  $bytes = $md5.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($text))
  -join ($bytes | ForEach-Object { $_.ToString('x2') })
}

function Invoke-Bi([hashtable]$body) {
  $json = $body | ConvertTo-Json -Depth 6 -Compress
  Invoke-RestMethod -Uri "$BaseUrl/json" -Method Post -Body $json -ContentType 'text/plain' -TimeoutSec 20
}

function Save-Sample([string]$name, $obj) {
  $text = $obj | ConvertTo-Json -Depth 12
  $text = [regex]::Replace($text, '"session"\s*:\s*"[^"]*"', '"session": "REDACTED"')
  Set-Content -Path (Join-Path $OutDir "$name.json") -Value $text -Encoding UTF8
}

$secure = Read-Host -Prompt "Password for $User" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }

$challenge = Invoke-Bi @{ cmd = 'login' }
$session = $challenge.session
$resp = Get-Md5Hex "${User}:${session}:${plain}"
$plain = $null
$login = Invoke-Bi @{ cmd = 'login'; session = $session; response = $resp }
Save-Sample 'login' $login
if ($login.result -ne 'success') { throw "Login failed: $($login.data.reason)" }
Write-Host 'Login OK.'

$now = [int][double]::Parse((Get-Date -UFormat %s))
$calls = [ordered]@{
  'camlist'   = @{ cmd = 'camlist' }
  'status'    = @{ cmd = 'status' }
  'alertlist' = @{ cmd = 'alertlist'; camera = 'index'; startdate = $now - 86400; enddate = $now + 60; view = 'alerts' }
  'log'       = @{ cmd = 'log' }
}
foreach ($name in $calls.Keys) {
  $body = $calls[$name]; $body.session = $session
  try {
    $r = Invoke-Bi $body
    Save-Sample $name $r
    Write-Host ("{0,-10} result={1}" -f $name, $r.result)
  } catch { Write-Host ("{0,-10} ERROR {1}" -f $name, $_.Exception.Message) }
}

# Does a cookie-only session work for pictures? (no session in the URL)
try {
  $cams = (Get-Content (Join-Path $OutDir 'camlist.json') -Raw | ConvertFrom-Json).data
  $cam = ($cams | Where-Object { $_.optionValue -and $_.optionValue -ne 'index' } | Select-Object -First 1).optionValue
  if ($cam) {
    $ws = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $uri = [Uri]$BaseUrl
    $ws.Cookies.Add($uri, (New-Object System.Net.Cookie('session', $session, '/')))
    $img = Invoke-WebRequest -Uri "$BaseUrl/image/$([uri]::EscapeDataString($cam))?w=320&q=50" -WebSession $ws -UseBasicParsing -TimeoutSec 20
    Write-Host ("cookie-only /image/: HTTP {0}, {1}, {2} bytes" -f $img.StatusCode, $img.Headers['Content-Type'], $img.RawContentLength)
  }
} catch { Write-Host ("cookie-only /image/: FAILED {0}" -f $_.Exception.Message) }

# Milestone 2 probes: do /thumbs/ and /video/ also accept the cookie alone? What does the live stream start with?
# Only status codes, content types and a few header bytes are printed here; short video captures follow below.
function New-CookieRequest([string]$url) {
  $req = [System.Net.HttpWebRequest]::Create($url)
  $req.AllowAutoRedirect = $false
  $req.Timeout = 15000
  $req.ReadWriteTimeout = 15000
  $req.CookieContainer = New-Object System.Net.CookieContainer
  $req.CookieContainer.Add([Uri]$BaseUrl, (New-Object System.Net.Cookie('session', $session, '/')))
  $req
}

try {
  $alerts = (Get-Content (Join-Path $OutDir 'alertlist.json') -Raw | ConvertFrom-Json).data
  $first = $alerts | Where-Object { $_.path } | Select-Object -First 1
  if ($first) {
    $req = New-CookieRequest "$BaseUrl/thumbs/$($first.path)"
    $resp = $req.GetResponse()
    Write-Host ("cookie-only /thumbs/: HTTP {0}, {1}" -f [int]$resp.StatusCode, $resp.ContentType)
    $resp.Close()
  } else { Write-Host 'cookie-only /thumbs/: skipped (no alerts in the last 24 h)' }
} catch [System.Net.WebException] {
  $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 'no response' }
  Write-Host ("cookie-only /thumbs/: FAILED HTTP {0}" -f $code)
} catch { Write-Host ("cookie-only /thumbs/: FAILED {0}" -f $_.Exception.Message) }

try {
  if ($cam) {
    $req = New-CookieRequest "$BaseUrl/video/$([uri]::EscapeDataString($cam))/2.0"
    $resp = $req.GetResponse()
    Write-Host ("cookie-only /video/: HTTP {0}, {1}" -f [int]$resp.StatusCode, $resp.ContentType)
    $stream = $resp.GetResponseStream()
    $buf = New-Object byte[] 65536
    $got = 0
    while ($got -lt $buf.Length) {
      $n = $stream.Read($buf, $got, $buf.Length - $got)
      if ($n -le 0) { break }
      $got += $n
    }
    $head = ($buf[0..([Math]::Min(31, $got - 1))] | ForEach-Object { $_.ToString('x2') }) -join ' '
    $ascii = -join ($buf[0..([Math]::Min(15, $got - 1))] | ForEach-Object { if ($_ -ge 32 -and $_ -lt 127) { [char]$_ } else { '.' } })
    $starts = 0
    for ($i = 0; $i -lt $got - 3; $i++) {
      if ($buf[$i] -eq 0 -and $buf[$i+1] -eq 0 -and $buf[$i+2] -eq 0 -and $buf[$i+3] -eq 1) { $starts++ }
    }
    Write-Host ("  read {0} bytes; first 32: {1}" -f $got, $head)
    Write-Host ("  first 16 as text: {0}" -f $ascii)
    Write-Host ("  00 00 00 01 start codes in that data: {0}" -f $starts)
    $req.Abort()
  }
} catch [System.Net.WebException] {
  $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 'no response' }
  Write-Host ("cookie-only /video/: FAILED HTTP {0}" -f $code)
} catch { Write-Host ("cookie-only /video/: FAILED {0}" -f $_.Exception.Message) }

# Short captures of the live streams (the first ~2 MB), saved to the sample folder so the parser and decoder can be
# tested against real bytes. THEY CONTAIN CAMERA PICTURES: keep them out of the repo, delete them when done.
function Save-VideoCapture([string]$id, [string]$file, [string]$query) {
  $req = New-CookieRequest "$BaseUrl/video/$([uri]::EscapeDataString($id))/2.0?$query"
  $resp = $req.GetResponse()
  $lines = @("HTTP $([int]$resp.StatusCode)", "Content-Type: $($resp.ContentType)")
  foreach ($k in $resp.Headers.AllKeys) { if ($k -match '^X-') { $lines += "${k}: $($resp.Headers[$k])" } }
  Set-Content -Path (Join-Path $OutDir "$file-headers.txt") -Value $lines -Encoding UTF8
  $stream = $resp.GetResponseStream()
  $buf = New-Object byte[] 2000000
  $got = 0
  while ($got -lt $buf.Length) {
    $n = $stream.Read($buf, $got, $buf.Length - $got)
    if ($n -le 0) { break }
    $got += $n
  }
  $req.Abort()
  [System.IO.File]::WriteAllBytes((Join-Path $OutDir "$file.bin"), $buf[0..($got - 1)])
  Write-Host ("captured {0}: {1} bytes" -f $file, $got)
}
try {
  if ($cam) { Save-VideoCapture $cam 'video-camera' 'audio=0&stream=0&w=1280&h=720&q=35&kbps=1024&extend=2' }
  Save-VideoCapture 'index' 'video-group' 'audio=0&stream=0&w=1280&h=720&q=35&kbps=1024&extend=2'
} catch { Write-Host ("video capture: FAILED {0}" -f $_.Exception.Message) }

try { Invoke-Bi @{ cmd = 'logout'; session = $session } | Out-Null } catch {}
Write-Host "Saved samples to $OutDir (session token redacted). Do not commit them."
