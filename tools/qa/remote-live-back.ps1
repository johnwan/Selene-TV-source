param(
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][scriptblock]$ReadStatus,
    [string]$Adb='adb',
    [string]$Package='org.moontechlab.selenetv.tvqa',
    [string]$Title='QA live stream'
)
$ErrorActionPreference='Stop'
$before=& $ReadStatus
if ($before.state -ne 'playing' -or $before.title -ne $Title) {
    throw 'Start the known remote live test stream before running this regression.'
}
# Let the native controls hide: Back then arms exit and the next Back exits.
Start-Sleep -Seconds 6
$focus=& $Adb -s $Serial shell dumpsys window
if (-not ($focus | Select-String ('mCurrentFocus=.*'+[regex]::Escape($Package)+'/') -Quiet)) {
    throw 'The expected TV test app is not foreground; no keys were sent.'
}
& $Adb -s $Serial shell input keyevent 4
Start-Sleep -Milliseconds 300
& $Adb -s $Serial shell input keyevent 4
Start-Sleep -Seconds 1
$after=& $ReadStatus
if ($after.state -ne 'idle') { throw "Native Back failed: expected idle, got $($after.state)" }
Write-Output 'PASS: TV Back exits remote live playback and releases the session.'
