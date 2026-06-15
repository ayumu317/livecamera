param(
    [string]$TaskName = "LiveCamera Ngrok Tunnel",
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$BackendPort = "",
    [string]$Domain = "",
    [string]$TunnelName = "",
    [switch]$PreferScheduledTask
)

$ErrorActionPreference = "Stop"

$startScript = Join-Path $ProjectRoot "scripts\start-ngrok.ps1"
if (-not (Test-Path $startScript)) {
    throw "start-ngrok.ps1 not found: $startScript"
}

$arguments = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", "`"$startScript`"",
    "-ProjectRoot", "`"$ProjectRoot`""
)

if ($BackendPort) {
    $arguments += @("-BackendPort", "`"$BackendPort`"")
}
if ($Domain) {
    $arguments += @("-Domain", "`"$Domain`"")
}
if ($TunnelName) {
    $arguments += @("-TunnelName", "`"$TunnelName`"")
}

$taskCommand = "powershell.exe " + ($arguments -join " ")

if ($PreferScheduledTask) {
    $escapedTaskName = $TaskName.Replace('"', '\"')
    $escapedTaskCommand = $taskCommand.Replace('"', '\"')
    $output = & cmd.exe /c "schtasks /Create /TN `"$escapedTaskName`" /SC ONLOGON /F /TR `"$escapedTaskCommand`" 2>&1"
    if ($LASTEXITCODE -eq 0) {
        $output | Out-Host
        Write-Host "Installed Windows logon task: $TaskName"
        Write-Host "Command: $taskCommand"
        exit 0
    }
    Write-Warning "Scheduled task install failed, falling back to Startup shortcut."
    $output | Out-Host
}

$startupDir = [Environment]::GetFolderPath("Startup")
$shortcutPath = Join-Path $startupDir "$TaskName.lnk"
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = "powershell.exe"
$shortcut.Arguments = $arguments -join " "
$shortcut.WorkingDirectory = $ProjectRoot
$shortcut.WindowStyle = 7
$shortcut.Description = "Start LiveCamera ngrok tunnel at Windows logon"
$shortcut.Save()

Write-Host "Installed Startup shortcut: $shortcutPath"
Write-Host "Command: $taskCommand"
