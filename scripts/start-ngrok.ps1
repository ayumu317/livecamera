param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$BackendPort = "",
    [string]$Domain = "",
    [string]$TunnelName = ""
)

$ErrorActionPreference = "Stop"

if ($BackendPort) {
    $env:NGROK_BACKEND_PORT = $BackendPort
}
if ($Domain) {
    $env:NGROK_DOMAIN = $Domain
}
if ($TunnelName) {
    $env:NGROK_TUNNEL_NAME = $TunnelName
}

Set-Location $ProjectRoot
node (Join-Path $ProjectRoot "scripts\start-ngrok.js")
