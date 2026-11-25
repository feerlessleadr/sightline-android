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

try { Invoke-Bi @{ cmd = 'logout'; session = $session } | Out-Null } catch {}
Write-Host "Saved samples to $OutDir (session token redacted). Do not commit them."
