param(
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][scriptblock]$ReadStatus,
    [string]$Adb='adb',
    [string]$Package='org.moontechlab.selenetv.tvqa',
    [string]$Title='QA live stream'
)
$ErrorActionPreference='Stop'
$status=& $ReadStatus
if ($status.state -ne 'playing' -or $status.title -ne $Title) {
    throw 'Start the known remote player before checking its full-screen window.'
}
$display=(& $Adb -s $Serial shell wm size) -join "`n"
$size=[regex]::Match($display,'Override size:\s*(\d+)x(\d+)')
if (-not $size.Success) { $size=[regex]::Match($display,'Physical size:\s*(\d+)x(\d+)') }
if (-not $size.Success) { throw 'Unable to read the logical display size.' }
$expected='[0,0]['+$size.Groups[1].Value+','+$size.Groups[2].Value+']'
$dump=(& $Adb -s $Serial shell uiautomator dump /sdcard/selene-fullscreen-qa.xml) -join "`n"
if ($dump -notmatch 'dumped to:') { throw 'Fresh UI inspection failed.' }
[xml]$tree=(& $Adb -s $Serial shell cat /sdcard/selene-fullscreen-qa.xml) -join "`n"
$root=$tree.SelectSingleNode("/hierarchy/node[@package='$Package']")
if (-not $root) { throw 'The expected TV player is not foreground.' }
if ($root.bounds -ne $expected) { throw "Player window is inset: expected $expected, got $($root.bounds)" }
Write-Output "PASS: Remote player window covers the logical display $expected."
