# smoke-exec.ps1 - Execution engine end-to-end smoke test (ASCII only)
# Usage: powershell -File smoke-exec.ps1 [-Base http://127.0.0.1:8081] [-StartVite]
param(
    [string]$Base = 'http://127.0.0.1:8081',
    [switch]$StartVite
)
$ErrorActionPreference = 'Continue'
$env:HTTP_PROXY = ''; $env:HTTPS_PROXY = ''; $env:ALL_PROXY = ''; $env:NO_PROXY = '*'
$base = $Base
$root = 'E:\GuaduationProject\APIGenTest'

function Invoke-Json($method, $path, $headers, $body) {
    $params = @{ Uri = "$base$path"; Method = $method; Headers = $headers }
    if ($body) {
        $params.ContentType = 'application/json'
        $params.Body = $body
    }
    return Invoke-RestMethod @params
}

$mockJob = Start-Job -ScriptBlock {
    Set-Location 'E:\GuaduationProject\APIGenTest\scripts'
    node mock-target.js 9090
}
$backendJob = Start-Job -ScriptBlock {
    $env:JAVA_HOME = 'D:\develop\Java'
    Set-Location 'E:\GuaduationProject\APIGenTest\backend'
    & 'E:\apache-maven-3.9.9\bin\mvn.cmd' spring-boot:run
}
$viteJob = $null
if ($StartVite) {
    $viteJob = Start-Job -ScriptBlock {
        $env:HTTP_PROXY = ''; $env:HTTPS_PROXY = ''; $env:ALL_PROXY = ''; $env:NO_PROXY = '*'
        Set-Location 'E:\GuaduationProject\APIGenTest\frontend'
        npm run dev
    }
    # wait vite ready (vite 8 binds IPv6 loopback only)
    $viteReady = $false
    for ($i = 0; $i -lt 60; $i++) {
        $code = & curl.exe -s -o NUL -w '%{http_code}' 'http://localhost:5173/' 2>$null
        if ($code -eq '200') { $viteReady = $true; break }
        Start-Sleep -Milliseconds 1000
    }
    if (-not $viteReady) { throw 'vite not ready' }
    $base = 'http://localhost:5173'
    Write-Host '[ok] vite dev ready (proxy -> 8081)'
}
$createdUser = ''
try {
    $mockReady = $false
    for ($i = 0; $i -lt 20; $i++) {
        $code = & curl.exe -s -o NUL -w '%{http_code}' 'http://127.0.0.1:9090/api/orders' 2>$null
        if ($code -eq '200') { $mockReady = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $mockReady) { throw 'mock-target not ready' }
    Write-Host '[ok] mock-target ready'

    $ready = $false
    for ($i = 0; $i -lt 180; $i++) {
        $code = & curl.exe -s -o NUL -w '%{http_code}' "$base/api/health" 2>$null
        if ($code -eq '200') { $ready = $true; break }
        Start-Sleep -Milliseconds 1000
    }
    if (-not $ready) { throw 'backend not ready' }
    Write-Host '[ok] backend ready'

    $ts = Get-Date -Format 'yyyyMMddHHmmss'
    $createdUser = "exec$ts"
    $regBody = @{ username = $createdUser; password = 'Test1234'; nickname = 'exec-smoke' } | ConvertTo-Json -Compress
    Invoke-Json 'Post' '/api/auth/register' @{} $regBody | Out-Null
    $login = Invoke-Json 'Post' '/api/auth/login' @{} (@{ username = $createdUser; password = 'Test1234' } | ConvertTo-Json -Compress)
    $token = $login.data.token
    if (-not $token) { throw 'login failed' }
    $auth = @{ Authorization = "Bearer $token" }
    Write-Host '[ok] register+login'

    $proj = Invoke-Json 'Post' '/api/projects' $auth (@{ name = "exec-smoke-$ts"; description = 'smoke' } | ConvertTo-Json -Compress)
    $projectId = $proj.data.id
    Write-Host "[project] id=$projectId"

    $imp = & curl.exe -s -X POST "$base/api/projects/$projectId/import" -H "Authorization: Bearer $token" -F "file=@$root\docs\samples\openapi-users.json"
    Write-Host "[import] $imp"
    $apiPage = Invoke-Json 'Get' "/api/projects/$projectId/apis?page=1&size=50" $auth $null
    $apiList = $apiPage.data.records
    $apiLogin = ($apiList | Where-Object { $_.path -eq '/api/login' } | Select-Object -First 1).id
    $apiUsers = ($apiList | Where-Object { $_.path -eq '/api/users' } | Select-Object -First 1).id
    $apiUserDetail = ($apiList | Where-Object { $_.path -eq '/api/users/{id}' } | Select-Object -First 1).id
    Write-Host "[apis] login=$apiLogin users=$apiUsers detail=$apiUserDetail"

    $envBody = @{ name = 'mock-env'; baseUrl = 'http://127.0.0.1:9090'; variables = '{"username":"admin","password":"123456"}' } | ConvertTo-Json -Compress
    $envr = Invoke-Json 'Post' "/api/projects/$projectId/environments" $auth $envBody
    $envId = $envr.data.id
    Write-Host "[env] id=$envId"

    function New-Case([string]$name, $apiId, [string]$method, [string]$url, [string]$headers, [string]$body, [string]$asserts, [string]$extract, $pre) {
        $c = @{ projectId = $projectId; apiId = $apiId; name = $name; scenarioType = 'normal'; method = $method; urlTemplate = $url; status = 1 }
        if ($headers) { $c.headers = $headers }
        if ($body) { $c.body = $body }
        if ($asserts) { $c.asserts = $asserts }
        if ($extract) { $c.extractVars = $extract }
        if ($pre) { $c.preCaseId = $pre }
        $json = $c | ConvertTo-Json -Compress -Depth 6
        $resp = Invoke-Json 'Post' '/api/cases' $auth $json
        return $resp.data.id
    }

    $assertLogin = '[{"type":"statusCode","expect":200},{"type":"field","path":"$.data.token","condition":"notEmpty"}]'
    $extractToken = '[{"from":"response","expr":"$.data.token","varName":"token"}]'
    $caseLogin = New-Case 'login-success' $apiLogin 'POST' '{{baseUrl}}/api/login' '{"Content-Type":"application/json"}' '{"username":"{{env:username}}","password":"{{env:password}}"}' $assertLogin $extractToken $null
    $assertUsers = '[{"type":"statusCode","expect":200},{"type":"field","path":"$.data","condition":"notEmpty"}]'
    $caseUsers = New-Case 'users-with-token' $apiUsers 'GET' '{{baseUrl}}/api/users' '{"Authorization":"Bearer {{token}}"}' $null $assertUsers $null $caseLogin
    $assertDetail = '[{"type":"statusCode","expect":200},{"type":"field","path":"$.data.id","condition":"equal","expect":"1"}]'
    $caseDetail = New-Case 'user-detail-1' $apiUserDetail 'GET' '{{baseUrl}}/api/users/1' '{"Authorization":"Bearer {{token}}"}' $null $assertDetail $null $caseLogin
    $assertOrders = '[{"type":"statusCode","expect":200},{"type":"field","path":"$.code","condition":"equal","expect":"0"}]'
    $caseOrders = New-Case 'orders-ok' $null 'GET' '{{baseUrl}}/api/orders' $null $null $assertOrders $null $null
    $assertBoom = '[{"type":"statusCode","expect":200}]'
    $caseBoom = New-Case 'boom-should-fail' $null 'GET' '{{baseUrl}}/api/boom' $null $null $assertBoom $null $null
    $caseRefused = New-Case 'refused-connection' $null 'GET' 'http://127.0.0.1:1/api/x' $null $null '[]' $null $null
    Write-Host "[cases] login=$caseLogin users=$caseUsers detail=$caseDetail orders=$caseOrders boom=$caseBoom refused=$caseRefused"

    $runBody = @{ projectId = $projectId; environmentId = $envId; scope = @{ type = 'all' } } | ConvertTo-Json -Compress -Depth 4
    $run = Invoke-Json 'Post' '/api/executions/run' $auth $runBody
    $executionId = $run.data.executionId
    if (-not $executionId) { throw 'run failed' }
    Write-Host "[run] executionId=$executionId"

    $summary = $null
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Milliseconds 500
        $summary = Invoke-Json 'Get' "/api/executions/$executionId" $auth $null
        if ($summary.data.status -eq 1) { break }
    }
    if (-not $summary -or $summary.data.status -ne 1) { throw "execution not finished" }
    Write-Host "[summary] total=$($summary.data.totalCases) passed=$($summary.data.passed) failed=$($summary.data.failed) rate=$($summary.data.passRate) duration=$($summary.data.durationMs)ms"

    $details = Invoke-Json 'Get' "/api/executions/$executionId/details?page=1&size=20" $auth $null
    foreach ($d in $details.data.records) {
        Write-Host ("[detail] status={0} retry={1} name={2} err={3}" -f $d.status, $d.retryCount, $d.caseName, $d.errorMessage)
    }

    $ok = $true
    if ($summary.data.totalCases -ne 6) { Write-Host '[FAIL] totalCases != 6'; $ok = $false }
    if ($summary.data.passed -ne 4) { Write-Host '[FAIL] passed != 4'; $ok = $false }
    if ($summary.data.failed -ne 2) { Write-Host '[FAIL] failed != 2'; $ok = $false }
    $statusMap = @{}
    foreach ($d in $details.data.records) {
        if (-not $statusMap.ContainsKey($d.caseName)) { $statusMap[$d.caseName] = $d }
    }
    if ($statusMap['login-success'].status -ne 1) { Write-Host '[FAIL] login not passed'; $ok = $false }
    if ($statusMap['users-with-token'].status -ne 1) { Write-Host '[FAIL] users not passed (var token?)'; $ok = $false }
    if ($statusMap['user-detail-1'].status -ne 1) { Write-Host '[FAIL] user-detail not passed'; $ok = $false }
    if ($statusMap['orders-ok'].status -ne 1) { Write-Host '[FAIL] orders not passed'; $ok = $false }
    if ($statusMap['boom-should-fail'].status -ne 2) { Write-Host '[FAIL] boom not failed(2)'; $ok = $false }
    if ($statusMap['boom-should-fail'].retryCount -lt 1) { Write-Host '[FAIL] boom not retried'; $ok = $false }
    if ($statusMap['refused-connection'].status -ne 3) { Write-Host '[FAIL] refused not exception(3)'; $ok = $false }
    if ($statusMap['login-success'].responseText -notmatch 'mock-token') { Write-Host '[FAIL] login response no token'; $ok = $false }
    if ($statusMap['users-with-token'].requestText -notmatch 'Bearer mock-token') { Write-Host '[FAIL] token not injected into users request'; $ok = $false }
    if ($ok) { Write-Host '=== SMOKE PASS ===' } else { Write-Host '=== SMOKE FAIL ===' }

    Invoke-Json 'Delete' "/api/projects/$projectId" $auth $null | Out-Null
    # verify cascade delete in DB
    $verify = @"
SELECT (SELECT COUNT(*) FROM project WHERE id=$projectId) AS p,
       (SELECT COUNT(*) FROM test_case WHERE project_id=$projectId) AS c,
       (SELECT COUNT(*) FROM api_info WHERE project_id=$projectId) AS a,
       (SELECT COUNT(*) FROM environment WHERE project_id=$projectId) AS e,
       (SELECT COUNT(*) FROM execution WHERE project_id=$projectId) AS x;
"@
    $verifyOut = $verify | & mysql -uroot -proot apigentest -N 2>$null
    Write-Host "[verify cascade] $verifyOut"
    if ($verifyOut -notmatch '^0\s+0\s+0\s+0\s+0') { Write-Host '[FAIL] cascade delete incomplete' }
}
finally {
    Stop-Job $mockJob, $backendJob -ErrorAction SilentlyContinue
    if ($viteJob) { Stop-Job $viteJob -ErrorAction SilentlyContinue }
    Remove-Job $mockJob, $backendJob -Force -ErrorAction SilentlyContinue
    if ($viteJob) { Remove-Job $viteJob -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 2
    foreach ($port in 8081, 9090, 5173) {
        $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        if ($conn) {
            Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
            Write-Host "[cleanup] killed PID $($conn.OwningProcess) on port $port"
        }
    }
    if ($createdUser) {
        $cleanSql = @"
SET @uid = (SELECT id FROM user WHERE username='$createdUser');
DELETE FROM failure_analysis WHERE execution_detail_id IN (SELECT id FROM execution_detail WHERE execution_id IN (SELECT id FROM execution WHERE project_id IN (SELECT id FROM project WHERE owner_id=@uid)));
DELETE FROM execution_detail WHERE execution_id IN (SELECT id FROM execution WHERE project_id IN (SELECT id FROM project WHERE owner_id=@uid));
DELETE FROM execution WHERE project_id IN (SELECT id FROM project WHERE owner_id=@uid);
UPDATE test_case SET pre_case_id = NULL WHERE project_id IN (SELECT id FROM project WHERE owner_id=@uid);
DELETE FROM test_case WHERE project_id IN (SELECT id FROM project WHERE owner_id=@uid);
DELETE FROM api_info WHERE project_id IN (SELECT id FROM project WHERE owner_id=@uid);
DELETE FROM environment WHERE project_id IN (SELECT id FROM project WHERE owner_id=@uid);
DELETE FROM project WHERE owner_id=@uid;
DELETE FROM user WHERE id=@uid;
"@
        $cleanSql | & mysql -uroot -proot apigentest 2>$null
        Write-Host "[cleanup] db cleaned: $createdUser"
    }
}