$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8081/api'
$results = New-Object System.Collections.Generic.List[string]
$failCount = 0

function Call-Api($method, $path, $body, $token) {
  $headers = @{}
  if ($token) { $headers.Authorization = 'Bearer ' + $token }
  $params = @{ Uri = ($base + $path); Method = $method; Headers = $headers; TimeoutSec = 20 }
  if ($null -ne $body) { $params.Body = ($body | ConvertTo-Json -Depth 10); $params.ContentType = 'application/json' }
  try {
    $r = Invoke-RestMethod @params
    return @{ ok = $true; code = $r.code; data = $r.data; message = $r.message }
  } catch {
    $status = 0
    try { $status = [int]$_.Exception.Response.StatusCode } catch { }
    return @{ ok = $false; code = $status; message = $_.ErrorDetails.Message }
  }
}

function Check($name, $cond, $detail) {
  if ($cond) { $results.Add("PASS | $name | $detail") } else { $results.Add("FAIL | $name | $detail"); $script:failCount++ }
}

$ts = Get-Date -Format 'HHmmss'
$uA = 'owner' + $ts; $uB = 'member' + $ts; $uC = 'ro' + $ts; $uD = 'outsider' + $ts

foreach ($u in @($uA, $uB, $uC, $uD)) {
  $r = Call-Api 'POST' '/auth/register' @{ username = $u; password = '123456'; nickname = $u } $null
  Check ("register " + $u) ($r.ok -and $r.code -eq 0) $r.message
}
$tA = (Call-Api 'POST' '/auth/login' @{ username = $uA; password = '123456' } $null).data.token
$tB = (Call-Api 'POST' '/auth/login' @{ username = $uB; password = '123456' } $null).data.token
$tC = (Call-Api 'POST' '/auth/login' @{ username = $uC; password = '123456' } $null).data.token
$tD = (Call-Api 'POST' '/auth/login' @{ username = $uD; password = '123456' } $null).data.token
Check 'login A/B/C/D tokens' ($tA -and $tB -and $tC -and $tD) ''

$proj = Call-Api 'POST' '/projects' @{ name = 'collab-test' + $ts; description = 'member feature test' } $tA
$projId = $proj.data.id
Check 'owner creates project' ($proj.ok -and $null -ne $projId) ("projId=" + $projId)

$r1 = Call-Api 'POST' ("/projects/" + $projId + "/members") @{ username = $uB; role = 1 } $tA
Check 'invite member(role=1)' ($r1.ok -and $r1.code -eq 0) $r1.message
$r2 = Call-Api 'POST' ("/projects/" + $projId + "/members") @{ username = $uC; role = 2 } $tA
Check 'invite readonly(role=2)' ($r2.ok -and $r2.code -eq 0) $r2.message
$r3 = Call-Api 'POST' ("/projects/" + $projId + "/members") @{ username = 'nobody' + $ts; role = 1 } $tA
Check 'invite nonexist -> 404' ($r3.code -eq 404) ("code=" + $r3.code)
$r4 = Call-Api 'POST' ("/projects/" + $projId + "/members") @{ username = $uA; role = 1 } $tA
Check 'invite owner -> 400' ($r4.code -eq 400) ("code=" + $r4.code)
$r5 = Call-Api 'POST' ("/projects/" + $projId + "/members") @{ username = $uB; role = 1 } $tA
Check 'duplicate invite -> 400' ($r5.code -eq 400) ("code=" + $r5.code)

$list = Call-Api 'GET' ("/projects/" + $projId + "/members") $null $tA
$roles = @($list.data | ForEach-Object { $_.role })
Check 'member list owner+2' (($roles -contains 0) -and ($roles -contains 1) -and ($roles -contains 2) -and $roles.Count -eq 3) ("roles=" + ($roles -join ','))

$mb = $list.data | Where-Object { $_.username -eq $uB } | Select-Object -First 1
$r6 = Call-Api 'PUT' ("/members/" + $mb.id) @{ username = $uB; role = 2 } $tA
Check 'change B role 1->2' ($r6.ok -and $r6.code -eq 0) $r6.message
$list2 = Call-Api 'GET' ("/projects/" + $projId + "/members") $null $tA
$mb2 = $list2.data | Where-Object { $_.username -eq $uB } | Select-Object -First 1
Check 'B role now 2' ($mb2.role -eq 2) ("role=" + $mb2.role)
Call-Api 'PUT' ("/members/" + $mb.id) @{ username = $uB; role = 1 } $tA | Out-Null

$d1 = Call-Api 'GET' ("/projects/" + $projId) $null $tB
Check 'B read project myRole=1' ($d1.ok -and $d1.data.myRole -eq 1) ("myRole=" + $d1.data.myRole)
$w1 = Call-Api 'POST' ("/projects/" + $projId + "/environments") @{ name = 'test-env'; baseUrl = 'http://127.0.0.1:8080' } $tB
Check 'B write environment' ($w1.ok -and $w1.code -eq 0) $w1.message
$m1 = Call-Api 'PUT' ("/projects/" + $projId) @{ name = 'rename-test'; description = '' } $tB
Check 'B edit project -> 403' ($m1.code -eq 403) ("code=" + $m1.code)
$m2 = Call-Api 'POST' ("/projects/" + $projId + "/members") @{ username = $uD; role = 1 } $tB
Check 'B invite member -> 403' ($m2.code -eq 403) ("code=" + $m2.code)

$d2 = Call-Api 'GET' ("/projects/" + $projId) $null $tC
Check 'C read project myRole=2' ($d2.ok -and $d2.data.myRole -eq 2) ("myRole=" + $d2.data.myRole)
$w2 = Call-Api 'POST' ("/projects/" + $projId + "/environments") @{ name = 'ro-try'; baseUrl = 'http://x' } $tC
Check 'C write environment -> 403' ($w2.code -eq 403) ("code=" + $w2.code)
$run = Call-Api 'POST' '/executions/run' @{ projectId = $projId; environmentId = $null; scope = @{ type = 'all' } } $tC
Check 'C run execution -> 403' ($run.code -eq 403) ("code=" + $run.code)

$plB = Call-Api 'GET' '/projects' $null $tB
$pB = @($plB.data | Where-Object { $_.id -eq $projId })
Check 'B project list myRole=1' ($pB.Count -eq 1 -and $pB[0].myRole -eq 1) ("myRole=" + $pB[0].myRole)
$plC = Call-Api 'GET' '/projects' $null $tC
$pC = @($plC.data | Where-Object { $_.id -eq $projId })
Check 'C project list myRole=2' ($pC.Count -eq 1 -and $pC[0].myRole -eq 2) ("myRole=" + $pC[0].myRole)

$d4 = Call-Api 'GET' ("/projects/" + $projId) $null $tD
Check 'outsider read -> 403' ($d4.code -eq 403) ("code=" + $d4.code)

$mc = $list2.data | Where-Object { $_.username -eq $uC } | Select-Object -First 1
$r7 = Call-Api 'DELETE' ("/members/" + $mc.id) $null $tA
Check 'remove C' ($r7.ok -and $r7.code -eq 0) $r7.message
$list3 = Call-Api 'GET' ("/projects/" + $projId + "/members") $null $tA
Check 'member list after remove = 2' (@($list3.data).Count -eq 2) ("count=" + @($list3.data).Count)

$notif = Call-Api 'GET' '/notifications' $null $tB
$hasInvite = @($notif.data.records | Where-Object { $_.type -eq 'member' })
Check 'B got invite notification' ($hasInvite.Count -ge 1) ("count=" + $hasInvite.Count)

Write-Output '===== MEMBER TEST RESULTS ====='
$results | ForEach-Object { Write-Output $_ }
Write-Output ('===== FAIL: ' + $failCount + ' / ' + $results.Count + ' =====')
exit $failCount