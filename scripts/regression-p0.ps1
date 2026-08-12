param([string]$Base = 'http://127.0.0.1:8081')
$ErrorActionPreference = 'Continue'
$root = 'E:\GuaduationProject\APIGenTest\scripts'
$base = $Base
$ts = Get-Date -Format 'yyyyMMddHHmmss'
$results = New-Object System.Collections.ArrayList

function Add-Result([string]$key, [string]$name, [string]$status, [string]$detail) {
    [void]$results.Add([pscustomobject]@{ key = $key; name = $name; status = $status; detail = $detail })
    $mark = if ($status -eq 'PASS') { 'PASS' } else { 'FAIL' }
    Write-Host ("[{0}] {1} | {2} | {3}" -f $mark, $key, $name, $detail)
}

function Invoke-Json($method, $path, $headers, $body) {
    $params = @{ Uri = "$base$path"; Method = $method; Headers = $headers }
    if ($body) { $params.ContentType = 'application/json'; $params.Body = $body }
    return Invoke-RestMethod @params
}

function Wait-Execution($execId, $auth) {
    $s = $null
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Milliseconds 1000
        $s = Invoke-Json 'Get' "/api/executions/$execId" $auth $null
        if ($s.data.status -eq 1) { break }
    }
    return $s
}

# ============ login ============
try {
    $login = Invoke-Json 'Post' '/api/auth/login' @{} (@{ username = 'admin'; password = 'admin123' } | ConvertTo-Json -Compress)
    $auth = @{ Authorization = "Bearer $($login.data.token)" }
    Add-Result 'p0_login' 'P0 登录' 'PASS' 'admin ok'
} catch { Write-Host 'FATAL: login failed'; exit 1 }

# ============ 1. create project ============
$projId = $null
try {
    $proj = Invoke-Json 'Post' '/api/projects' $auth (@{ name = "P0Smoke$ts"; description = 'p0 feature smoke' } | ConvertTo-Json -Compress)
    $projId = $proj.data.id
    Add-Result 'p0_project' 'P0 创建项目' 'PASS' "projectId=$projId"
} catch { Add-Result 'p0_project' 'P0 创建项目' 'FAIL' $_.Exception.Message }

# ============ 2. import openapi (multipart via curl) ============
if ($projId) {
    try {
        $curlOut = & curl.exe -s -X POST -H "Authorization: Bearer $($login.data.token)" -F "file=@$root\fixtures\p0-openapi.json" "$base/api/projects/$projId/import"
        $imp = $curlOut | ConvertFrom-Json
        if ($imp.code -eq 0 -and $imp.data.total -eq 3) {
            Add-Result 'p0_import' 'P0 导入 OpenAPI' 'PASS' "total=$($imp.data.total)"
        } else {
            Add-Result 'p0_import' 'P0 导入 OpenAPI' 'FAIL' "code=$($imp.code) total=$($imp.data.total)"
        }
    } catch { Add-Result 'p0_import' 'P0 导入 OpenAPI' 'FAIL' $_.Exception.Message }
    try {
        $apiPage = Invoke-Json 'Get' "/api/projects/$projId/apis?page=1&size=50" $auth $null
        $apiMap = @{}
        foreach ($a in $apiPage.data.records) { $apiMap[$a.path] = $a.id }
        Add-Result 'p0_apilist' 'P0 接口列表' 'PASS' ("apis=" + $apiMap.Count)
    } catch { Add-Result 'p0_apilist' 'P0 接口列表' 'FAIL' $_.Exception.Message }
}

# ============ 3. coverage (empty) ============
if ($projId) {
    try {
        $cov = Invoke-Json 'Get' "/api/projects/$projId/coverage" $auth $null
        if ($cov.data.totalApis -eq 3 -and $cov.data.coveredApis -eq 0) {
            Add-Result 'p0_cov0' 'P0-1 覆盖率(空)' 'PASS' "total=$($cov.data.totalApis) covered=$($cov.data.coveredApis) rate=$($cov.data.rate)"
        } else {
            Add-Result 'p0_cov0' 'P0-1 覆盖率(空)' 'FAIL' "total=$($cov.data.totalApis) covered=$($cov.data.coveredApis)"
        }
    } catch { Add-Result 'p0_cov0' 'P0-1 覆盖率(空)' 'FAIL' $_.Exception.Message }

    # ============ 4. env (mock base) ============
    $envId = $null
    try {
        $env = Invoke-Json 'Post' "/api/projects/$projId/environments" $auth (@{ name = 'MockEnv'; baseUrl = "$base/mock/$projId"; variables = $null } | ConvertTo-Json -Compress -Depth 4)
        $envId = $env.data.id
        if ($envId) { Add-Result 'p0_env' 'P0 创建环境(Mock)' 'PASS' "envId=$envId" } else { Add-Result 'p0_env' 'P0 创建环境(Mock)' 'FAIL' "resp=$($env | ConvertTo-Json -Compress)" }
    } catch { Add-Result 'p0_env' 'P0 创建环境(Mock)' 'FAIL' $_.Exception.Message }

    # ============ 5. create 3 cases ============
    $c1 = $null; $c2 = $null; $c3 = $null
    try {
        $c1 = Invoke-Json 'Post' '/api/cases' $auth (@{ projectId = $projId; apiId = $apiMap['/api/users']; name = 'list users'; scenarioType = 'normal'; method = 'GET'; urlTemplate = '/api/users'; asserts = '[{"type":"statusCode","expect":200},{"type":"field","path":"$.data[0].username","condition":"notEmpty"}]'; extractVars = '[]'; status = 1 } | ConvertTo-Json -Compress -Depth 5)
        $c2 = Invoke-Json 'Post' '/api/cases' $auth (@{ projectId = $projId; apiId = $apiMap['/api/login']; name = 'login'; scenarioType = 'normal'; method = 'POST'; urlTemplate = '/api/login'; body = '{"username":"admin","password":"123456"}'; asserts = '[{"type":"statusCode","expect":200},{"type":"field","path":"$.data.token","condition":"notEmpty"}]'; extractVars = '[{"from":"response","expr":"$.data.token","varName":"token"}]'; status = 1 } | ConvertTo-Json -Compress -Depth 5)
        $c3 = Invoke-Json 'Post' '/api/cases' $auth (@{ projectId = $projId; apiId = $apiMap['/api/users/{id}']; name = 'user detail'; scenarioType = 'normal'; method = 'GET'; urlTemplate = '/api/users/1'; asserts = '[{"type":"statusCode","expect":200},{"type":"field","path":"$.data.username","condition":"notEmpty"}]'; extractVars = '[]'; status = 1 } | ConvertTo-Json -Compress -Depth 5)
        Add-Result 'p0_cases' 'P0 创建用例x3' 'PASS' "ids=$($c1.data.id),$($c2.data.id),$($c3.data.id)"
    } catch { Add-Result 'p0_cases' 'P0 创建用例x3' 'FAIL' $_.Exception.Message }

    # ============ 6. coverage after cases ============
    try {
        $cov = Invoke-Json 'Get' "/api/projects/$projId/coverage" $auth $null
        if ($cov.data.coveredApis -eq 3 -and $cov.data.rate -eq 100) {
            Add-Result 'p0_cov1' 'P0-1 覆盖率(3/3)' 'PASS' "covered=$($cov.data.coveredApis) rate=$($cov.data.rate) byTag=$($cov.data.byTag.Count)"
        } else {
            Add-Result 'p0_cov1' 'P0-1 覆盖率(3/3)' 'FAIL' "covered=$($cov.data.coveredApis) rate=$($cov.data.rate)"
        }
    } catch { Add-Result 'p0_cov1' 'P0-1 覆盖率(3/3)' 'FAIL' $_.Exception.Message }

    # ============ 7. execute all against mock ============
    try {
        $run = Invoke-Json 'Post' '/api/executions/run' $auth (@{ projectId = $projId; environmentId = $envId; scope = @{ type = 'all' } } | ConvertTo-Json -Compress -Depth 4)
        $w = Wait-Execution $run.data.executionId $auth
        $d = $w.data
        if ($d.passed -eq 3 -and $d.failed -eq 0) {
            Add-Result 'p0_exec_mock' 'P0-6 执行(Mock 全过)' 'PASS' "passed=$($d.passed) failed=$($d.failed) execId=$($run.data.executionId)"
        } else {
            Add-Result 'p0_exec_mock' 'P0-6 执行(Mock 全过)' 'FAIL' "passed=$($d.passed) failed=$($d.failed)"
        }
    } catch { Add-Result 'p0_exec_mock' 'P0-6 执行(Mock 全过)' 'FAIL' $_.Exception.Message }

    # ============ 8. debug replay ============
    if ($c1 -and $envId) {
        try {
            $dbg = Invoke-Json 'Post' "/api/cases/$($c1.data.id)/debug" $auth (@{ environmentId = $envId; asserts = $null } | ConvertTo-Json -Compress)
            if ($dbg.data.status -eq 1) {
                Add-Result 'p0_debug_ok' 'P0-7 调试重放(通过)' 'PASS' "status=$($dbg.data.status) case=$($dbg.data.caseName)"
            } else {
                Add-Result 'p0_debug_ok' 'P0-7 调试重放(通过)' 'FAIL' "status=$($dbg.data.status) err=$($dbg.data.errorMessage)"
            }
        } catch { Add-Result 'p0_debug_ok' 'P0-7 调试重放(通过)' 'FAIL' $_.Exception.Message }
        try {
            $dbg2 = Invoke-Json 'Post' "/api/cases/$($c1.data.id)/debug" $auth (@{ environmentId = $envId; asserts = '[{"type":"statusCode","expect":201}]' } | ConvertTo-Json -Compress)
            if ($dbg2.data.status -eq 2) {
                Add-Result 'p0_debug_fail' 'P0-7 调试重放(断言失败)' 'PASS' "status=$($dbg2.data.status) err=$($dbg2.data.errorMessage)"
            } else {
                Add-Result 'p0_debug_fail' 'P0-7 调试重放(断言失败)' 'FAIL' "status=$($dbg2.data.status)"
            }
        } catch { Add-Result 'p0_debug_fail' 'P0-7 调试重放(断言失败)' 'FAIL' $_.Exception.Message }
        try {
            $execs = Invoke-Json 'Get' "/api/executions?projectId=$projId" $auth $null
            if ($execs.data.total -eq 1) {
                Add-Result 'p0_debug_nopersist' 'P0-7 调试不落执行记录' 'PASS' "execTotal=$($execs.data.total)"
            } else {
                Add-Result 'p0_debug_nopersist' 'P0-7 调试不落执行记录' 'FAIL' "execTotal=$($execs.data.total)"
            }
        } catch { Add-Result 'p0_debug_nopersist' 'P0-7 调试不落执行记录' 'FAIL' $_.Exception.Message }
    }

    # ============ 9. export json / postman / openapi ============
    try {
        $exp = Invoke-RestMethod -Uri "$base/api/projects/$projId/cases/export?format=json" -Headers $auth
        if ($exp.format -eq 'apigentest-cases' -and $exp.cases.Count -eq 3) {
            Add-Result 'p0_export_json' 'P0-2 导出 JSON' 'PASS' "cases=$($exp.cases.Count)"
        } else {
            Add-Result 'p0_export_json' 'P0-2 导出 JSON' 'FAIL' "format=$($exp.format) count=$($exp.cases.Count)"
        }
    } catch { Add-Result 'p0_export_json' 'P0-2 导出 JSON' 'FAIL' $_.Exception.Message }
    try {
        $exp = Invoke-RestMethod -Uri "$base/api/projects/$projId/cases/export?format=postman" -Headers $auth
        if ($exp.info.schema -like '*postman*' -and $exp.item.Count -eq 3) {
            Add-Result 'p0_export_pm' 'P0-2 导出 Postman' 'PASS' "items=$($exp.item.Count)"
        } else {
            Add-Result 'p0_export_pm' 'P0-2 导出 Postman' 'FAIL' "schema=$($exp.info.schema) items=$($exp.item.Count)"
        }
    } catch { Add-Result 'p0_export_pm' 'P0-2 导出 Postman' 'FAIL' $_.Exception.Message }
    try {
        $exp = Invoke-RestMethod -Uri "$base/api/projects/$projId/cases/export?format=openapi" -Headers $auth
        if ($exp.openapi -eq '3.0.1' -and $exp.paths.'/api/users'.get -and $exp.paths.'/api/login'.post -and $exp.paths.'/api/users/1'.get) {
            Add-Result 'p0_export_oas' 'P0-2 导出 OpenAPI' 'PASS' 'paths=3'
        } else {
            Add-Result 'p0_export_oas' 'P0-2 导出 OpenAPI' 'FAIL' "openapi=$($exp.openapi)"
        }
    } catch { Add-Result 'p0_export_oas' 'P0-2 导出 OpenAPI' 'FAIL' $_.Exception.Message }

    # ============ 10. import postman ============
    try {
        $curlOut = & curl.exe -s -X POST -H "Authorization: Bearer $($login.data.token)" -F "file=@$root\fixtures\p0-postman.json" "$base/api/projects/$projId/cases/import"
        $imp2 = $curlOut | ConvertFrom-Json
        if ($imp2.code -eq 0 -and $imp2.data.total -eq 2) {
            Add-Result 'p0_import_pm' 'P0-2 导入 Postman' 'PASS' "total=$($imp2.data.total)"
        } else {
            Add-Result 'p0_import_pm' 'P0-2 导入 Postman' 'FAIL' "code=$($imp2.code) total=$($imp2.data.total) msg=$($imp2.message)"
        }
    } catch { Add-Result 'p0_import_pm' 'P0-2 导入 Postman' 'FAIL' $_.Exception.Message }

    # ============ 11. export pytest ============
    try {
        $resp = Invoke-WebRequest -Uri "$base/api/projects/$projId/cases/export-pytest" -Headers $auth
        $script = $resp.Content
        if ($script -like '*def test_*' -and $script -like '*requests*') {
            Add-Result 'p0_pytest' 'P0-3 pytest 导出' 'PASS' "len=$($script.Length) hasDef=$($script.Contains('def test_'))"
        } else {
            Add-Result 'p0_pytest' 'P0-3 pytest 导出' 'FAIL' "len=$($script.Length)"
        }
    } catch { Add-Result 'p0_pytest' 'P0-3 pytest 导出' 'FAIL' $_.Exception.Message }

    # ============ 12. mock direct ============
    try {
        $m = Invoke-RestMethod -Uri "$base/mock/$projId/api/users" -TimeoutSec 5
        if ($m.code -eq 0 -and $m.data.Count -ge 1 -and $m.data[0].username) {
            Add-Result 'p0_mock_users' 'P0-6 Mock 列表(schema)' 'PASS' "code=$($m.code) dataLen=$($m.data.Count) first=$($m.data[0].username)"
        } else {
            Add-Result 'p0_mock_users' 'P0-6 Mock 列表(schema)' 'FAIL' "code=$($m.code) dataLen=$($m.data.Count)"
        }
    } catch { Add-Result 'p0_mock_users' 'P0-6 Mock 列表(schema)' 'FAIL' $_.Exception.Message }
    try {
        $m2 = Invoke-RestMethod -Uri "$base/mock/$projId/api/login" -Method Post -ContentType 'application/json' -Body '{"username":"a","password":"b"}' -TimeoutSec 5
        if ($m2.code -eq 0 -and $m2.data.token) {
            Add-Result 'p0_mock_login' 'P0-6 Mock 对象(schema)' 'PASS' "token=$($m2.data.token)"
        } else {
            Add-Result 'p0_mock_login' 'P0-6 Mock 对象(schema)' 'FAIL' "code=$($m2.code)"
        }
    } catch { Add-Result 'p0_mock_login' 'P0-6 Mock 对象(schema)' 'FAIL' $_.Exception.Message }
    try {
        $m3code = & curl.exe -s -o NUL -w "%{http_code}" "$base/mock/$projId/api/users?mock_error=500"
        if ($m3code -eq '500') {
            Add-Result 'p0_mock_err' 'P0-6 Mock mock_error' 'PASS' "status=$m3code"
        } else {
            Add-Result 'p0_mock_err' 'P0-6 Mock mock_error' 'FAIL' "status=$m3code"
        }
    } catch { Add-Result 'p0_mock_err' 'P0-6 Mock mock_error' 'FAIL' $_.Exception.Message }
    try {
        $m4 = Invoke-WebRequest -Uri "$base/mock/$projId/api/users?mock_data=%7B%22x%22%3A1%7D" -TimeoutSec 5
        $j = $m4.Content | ConvertFrom-Json
        if ($j.x -eq 1) {
            Add-Result 'p0_mock_data' 'P0-6 Mock mock_data' 'PASS' "x=$($j.x)"
        } else {
            Add-Result 'p0_mock_data' 'P0-6 Mock mock_data' 'FAIL' "content=$($m4.Content)"
        }
    } catch { Add-Result 'p0_mock_data' 'P0-6 Mock mock_data' 'FAIL' $_.Exception.Message }
}

# ============ 13. webhook ============
$captured = 'E:\GuaduationProject\APIGenTest\scripts\fixtures\webhook-captured.txt'
if (Test-Path $captured) { Remove-Item -LiteralPath $captured -Force }
$listener = Start-Job -ScriptBlock {
    node 'E:\GuaduationProject\APIGenTest\scripts\fixtures\webhook-listener.js' 9091 'E:\GuaduationProject\APIGenTest\scripts\fixtures\webhook-captured.txt'
}
Start-Sleep -Seconds 2
try {
    Invoke-Json 'Put' '/api/admin/configs/webhook_url' $auth (@{ value = 'http://127.0.0.1:9091/hook' } | ConvertTo-Json -Compress) | Out-Null
    Invoke-Json 'Put' '/api/admin/configs/webhook_enabled' $auth (@{ value = '1' } | ConvertTo-Json -Compress) | Out-Null
    $cfg = Invoke-Json 'Get' '/api/admin/configs' $auth $null
    $urlKey = $cfg.data | Where-Object { $_.configKey -eq 'webhook_url' }
    if ($urlKey.configValue -eq 'http://127.0.0.1:9091/hook') {
        Add-Result 'p0_webhook_cfg' 'P0-4 Webhook 配置' 'PASS' 'saved'
    } else {
        Add-Result 'p0_webhook_cfg' 'P0-4 Webhook 配置' 'FAIL' "value=$($urlKey.configValue)"
    }
    $tw = Invoke-Json 'Post' '/api/admin/configs/test-webhook' $auth $null
    Start-Sleep -Seconds 1
    $txt = if (Test-Path $captured) { Get-Content -Raw $captured } else { '' }
    if ($txt -like '*"event":"test"*') {
        Add-Result 'p0_webhook_test' 'P0-4 Webhook 测试消息' 'PASS' $tw.data.message
    } else {
        Add-Result 'p0_webhook_test' 'P0-4 Webhook 测试消息' 'FAIL' "msg=$($tw.data.message) captured=$($txt.Length)"
    }
    if ($projId) {
        $run2 = Invoke-Json 'Post' '/api/executions/run' $auth (@{ projectId = $projId; environmentId = $envId; scope = @{ type = 'all' } } | ConvertTo-Json -Compress -Depth 4)
        $null = Wait-Execution $run2.data.executionId $auth
        Start-Sleep -Seconds 1
        $txt2 = if (Test-Path $captured) { Get-Content -Raw $captured } else { '' }
        if ($txt2 -like '*execution_finished*') {
            Add-Result 'p0_webhook_exec' 'P0-4 执行完成 Webhook' 'PASS' 'received execution_finished'
        } else {
            Add-Result 'p0_webhook_exec' 'P0-4 执行完成 Webhook' 'FAIL' "captured=$($txt2.Length)"
        }
    }
    # cleanup config
    Invoke-Json 'Put' '/api/admin/configs/webhook_enabled' $auth (@{ value = '0' } | ConvertTo-Json -Compress) | Out-Null
} catch { Add-Result 'p0_webhook' 'P0-4 Webhook 整体' 'FAIL' $_.Exception.Message }
Stop-Job $listener -ErrorAction SilentlyContinue | Out-Null
Remove-Job $listener -Force -ErrorAction SilentlyContinue | Out-Null

# ============ cleanup test project ============
if ($projId) {
    try {
        Invoke-Json 'Delete' "/api/projects/$projId" $auth $null | Out-Null
        Add-Result 'p0_cleanup' '清理烟测项目' 'PASS' "deleted project $projId"
    } catch { Add-Result 'p0_cleanup' '清理烟测项目' 'FAIL' $_.Exception.Message }
}

Write-Host ''
Write-Host '===== P0 smoke summary ====='
$pass = @($results | Where-Object { $_.status -eq 'PASS' }).Count
$fail = @($results | Where-Object { $_.status -eq 'FAIL' }).Count
Write-Host "PASS=$pass FAIL=$fail"
if ($fail -gt 0) { exit 2 } else { exit 0 }