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

# ============ login ============
$login = $null
try {
    $login = Invoke-Json 'Post' '/api/auth/login' @{} (@{ username = 'admin'; password = 'admin123' } | ConvertTo-Json -Compress)
    $auth = @{ Authorization = "Bearer $($login.data.token)" }
    Add-Result 'p2_login' 'P2 登录' 'PASS' 'admin ok'
} catch { Write-Host 'FATAL: login failed'; exit 1 }

$projId = $null
try {
    $proj = Invoke-Json 'Post' '/api/projects' $auth (@{ name = "P2Stats$ts"; description = 'p2 stats smoke' } | ConvertTo-Json -Compress)
    $projId = $proj.data.id
    Add-Result 'p2_project' 'P2 创建项目' 'PASS' "projectId=$projId"
} catch { Add-Result 'p2_project' 'P2 创建项目' 'FAIL' $_.Exception.Message }

if ($projId) {
    # import fixture
    try {
        $curlOut = & curl.exe -s -X POST -H "Authorization: Bearer $($login.data.token)" -F "file=@$root\fixtures\p0-openapi.json" "$base/api/projects/$projId/import"
        $imp = $curlOut | ConvertFrom-Json
        if ($imp.code -eq 0 -and $imp.data.total -eq 3) {
            Add-Result 'p2_import' 'P2 导入 OpenAPI' 'PASS' "total=$($imp.data.total)"
        } else {
            Add-Result 'p2_import' 'P2 导入 OpenAPI' 'FAIL' "code=$($imp.code) total=$($imp.data.total)"
        }
    } catch { Add-Result 'p2_import' 'P2 导入 OpenAPI' 'FAIL' $_.Exception.Message }    # ============ seed synthetic P2 data ============
    $sql = @"
USE apigentest;
SET @pid = $projId;
SET @uid = (SELECT id FROM user WHERE username = 'admin');
SET @a1 = (SELECT id FROM api_info WHERE project_id = @pid ORDER BY id LIMIT 1 OFFSET 0);
SET @a2 = (SELECT id FROM api_info WHERE project_id = @pid ORDER BY id LIMIT 1 OFFSET 1);
SET @a3 = (SELECT id FROM api_info WHERE project_id = @pid ORDER BY id LIMIT 1 OFFSET 2);
INSERT INTO generation_record (task_id, project_id, api_id, model, temperature, prompt_version, max_retry, retry_used, business_desc, generated_count, confirmed_count, scenario_generated, scenario_confirmed, status, error, created_by, created_at, confirmed_at) VALUES
('T1', @pid, @a1, 'deepseek-v4-flash', 0.3, 'v1', 2, 0, 'P2 test', 3, 2, '{"normal":1,"boundary":1,"exception":1}', '{"normal":1,"boundary":1}', 'SUCCESS', NULL, @uid, NOW(), NOW()),
('T2', @pid, @a2, 'qwen-plus', 0.7, 'v1', 2, 1, 'P2 test', 2, 2, '{"normal":1,"exception":1}', '{"normal":1,"exception":1}', 'SUCCESS', NULL, @uid, NOW(), NOW()),
('T3', @pid, @a3, 'deepseek-v4-flash', 0.3, 'v1', 2, 2, 'P2 test', 0, 0, '{}', NULL, 'FAILED', 'LLM call failed: test', @uid, NOW(), NULL);
INSERT INTO test_case (project_id, api_id, name, scenario_type, method, url_template, asserts, status, source, gen_task_id, gen_model, gen_temperature, gen_prompt_version, gen_retry_count, creator_id) VALUES
(@pid, @a1, 'P2T-normal',    'normal',    'GET', '{{baseUrl}}/users', '[]', 1, 2, 'T1', 'deepseek-v4-flash', 0.3, 'v1', 0, @uid),
(@pid, @a1, 'P2T-boundary',  'boundary',  'GET', '{{baseUrl}}/users', '[]', 1, 2, 'T1', 'deepseek-v4-flash', 0.3, 'v1', 0, @uid),
(@pid, @a1, 'P2T-exception', 'exception', 'GET', '{{baseUrl}}/users', '[]', 1, 2, 'T1', 'deepseek-v4-flash', 0.3, 'v1', 0, @uid),
(@pid, @a2, 'P2T-normal2',   'normal',    'GET', '{{baseUrl}}/posts', '[]', 1, 2, 'T2', 'qwen-plus', 0.7, 'v1', 1, @uid),
(@pid, @a2, 'P2T-exception2','exception','GET', '{{baseUrl}}/posts', '[]', 1, 2, 'T2', 'qwen-plus', 0.7, 'v1', 1, @uid);
INSERT INTO execution (project_id, trigger_type, status, total_cases, passed, failed, duration_ms, started_at, finished_at, operator_id) VALUES (@pid, 1, 1, 5, 2, 3, 800, NOW(), NOW(), @uid);
SET @eid = LAST_INSERT_ID();
SET @c1 = (SELECT id FROM test_case WHERE project_id = @pid AND gen_task_id = 'T1' AND name = 'P2T-normal' LIMIT 1);
SET @c2 = (SELECT id FROM test_case WHERE project_id = @pid AND gen_task_id = 'T1' AND name = 'P2T-boundary' LIMIT 1);
SET @c3 = (SELECT id FROM test_case WHERE project_id = @pid AND gen_task_id = 'T1' AND name = 'P2T-exception' LIMIT 1);
SET @c4 = (SELECT id FROM test_case WHERE project_id = @pid AND gen_task_id = 'T2' AND name = 'P2T-normal2' LIMIT 1);
SET @c5 = (SELECT id FROM test_case WHERE project_id = @pid AND gen_task_id = 'T2' AND name = 'P2T-exception2' LIMIT 1);
INSERT INTO execution_detail (execution_id, case_id, status, request_text, response_text, error_message, duration_ms, retry_count) VALUES
(@eid, @c1, 1, 'GET /users', '{"ok":1}', NULL, 120, 0),
(@eid, @c2, 2, 'GET /users', '{}', 'assert fail: expect 200 actual 400', 100, 1),
(@eid, @c3, 2, 'GET /users', '{}', 'assert fail: field missing', 100, 0),
(@eid, @c4, 1, 'GET /posts', '{"ok":1}', NULL, 150, 0),
(@eid, @c5, 3, 'GET /posts', NULL, 'connection refused', 50, 2);
SET @d2 = (SELECT id FROM execution_detail WHERE execution_id = @eid AND case_id = @c2);
SET @d3 = (SELECT id FROM execution_detail WHERE execution_id = @eid AND case_id = @c3);
SET @d5 = (SELECT id FROM execution_detail WHERE execution_id = @eid AND case_id = @c5);
INSERT INTO failure_analysis (execution_detail_id, category, reason, suggestion, confirmed, confirmed_category, llm_model, created_at, confirmed_at) VALUES
(@d2, 'assert_error', 'assert unreasonable', 'fix assert', 1, 'assert_error', 'deepseek-v4-flash', NOW(), NOW()),
(@d3, 'env_error', 'env issue', 'check env', 1, 'data_error', 'deepseek-v4-flash', NOW(), NOW()),
(@d5, 'real_defect', 'real defect', 'locate defect', 0, NULL, 'deepseek-v4-flash', NOW(), NULL);
SELECT COUNT(*) AS gen_rec FROM generation_record WHERE project_id = @pid;
"@
    $sqlFile = Join-Path $env:TEMP "p2-seed-$ts.sql"
    [System.IO.File]::WriteAllText($sqlFile, $sql)
    try {
        $seedOut = mysql -uroot -proot --default-character-set=utf8mb4 -e "source $($sqlFile.Replace('\','/'))" 2>$null
        $genRec = ($seedOut | Select-String '^\d+$' | Select-Object -First 1).Line
        if ($genRec -eq '3') {
            Add-Result 'p2_seed' 'P2 种子数据' 'PASS' 'generation_record=3'
        } else {
            Add-Result 'p2_seed' 'P2 种子数据' 'FAIL' "gen_rec=$genRec"
        }
    } catch { Add-Result 'p2_seed' 'P2 种子数据' 'FAIL' $_.Exception.Message }
    Remove-Item -LiteralPath $sqlFile -Force -ErrorAction SilentlyContinue    # ============ P2-1 generation quality ============
    try {
        $q = Invoke-Json 'Get' "/api/stats/generation-quality?projectId=$projId" $auth $null
        $o = $q.data.overall
        $ok = $true
        $checks = @(
            @($o.generated, 5, 'generated'),
            @($o.confirmed, 4, 'confirmed'),
            @($o.validRate, 80, 'validRate'),
            @($o.executed, 5, 'executed'),
            @($o.passed, 2, 'passed'),
            @($o.executable, 4, 'executable'),
            @($o.executableRate, 80, 'executableRate'),
            @($o.passRate, 40, 'passRate')
        )
        foreach ($c in $checks) {
            if ([Math]::Abs([double]$c[0] - [double]$c[1]) -gt 0.01) { $ok = $false; Add-Result 'p2_quality_overall' 'P2-1 生成质量整体' 'FAIL' "$($c[2]): $($c[0]) != $($c[1])" }
        }
        if ($ok) { Add-Result 'p2_quality_overall' 'P2-1 生成质量整体' 'PASS' '8 项指标全部正确' }
    } catch { Add-Result 'p2_quality_overall' 'P2-1 生成质量整体' 'FAIL' $_.Exception.Message }

    try {
        $q = Invoke-Json 'Get' "/api/stats/generation-quality?projectId=$projId" $auth $null
        $m = @{}
        foreach ($row in $q.data.byScenario) { $m[$row.group] = $row }
        $ok = $true
        $checks = @(
            @('normal', 2, 2, 100, 2, 2, 100, 100),
            @('boundary', 1, 1, 100, 1, 0, 100, 0),
            @('exception', 2, 1, 50, 2, 0, 50, 0)
        )
        foreach ($c in $checks) {
            $r = $m[$c[0]]
            if (-not $r) { $ok = $false; Add-Result 'p2_quality_scenario' 'P2-1 按场景类型' 'FAIL' "missing $($c[0])"; continue }
            $expect = @($r.generated, $r.confirmed, $r.validRate, $r.executed, $r.passed, $r.executableRate, $r.passRate)
            $want = @($c[1], $c[2], $c[3], $c[4], $c[5], $c[6], $c[7])
            for ($i = 0; $i -lt $want.Count; $i++) {
                if ([Math]::Abs([double]$expect[$i] - [double]$want[$i]) -gt 0.01) {
                    $ok = $false; Add-Result 'p2_quality_scenario' 'P2-1 按场景类型' 'FAIL' "$($c[0])[$i]: $($expect[$i]) != $($want[$i])"
                }
            }
        }
        if ($ok) { Add-Result 'p2_quality_scenario' 'P2-1 按场景类型' 'PASS' 'normal/boundary/exception 全部正确' }
    } catch { Add-Result 'p2_quality_scenario' 'P2-1 按场景类型' 'FAIL' $_.Exception.Message }

    try {
        $q = Invoke-Json 'Get' "/api/stats/generation-quality?projectId=$projId" $auth $null
        $m = @{}
        foreach ($row in $q.data.byModel) { $m[$row.group] = $row }
        $ok = $true
        if (-not $m['deepseek-v4-flash'] -or $m['deepseek-v4-flash'].generated -ne 3 -or $m['deepseek-v4-flash'].confirmed -ne 2) { $ok = $false }
        if (-not $m['qwen-plus'] -or $m['qwen-plus'].generated -ne 2 -or $m['qwen-plus'].confirmed -ne 2) { $ok = $false }
        if ($ok) { Add-Result 'p2_quality_model' 'P2-1 按生成模型' 'PASS' 'deepseek/qwen 分组正确' }
        else { Add-Result 'p2_quality_model' 'P2-1 按生成模型' 'FAIL' 'model groups mismatch' }
    } catch { Add-Result 'p2_quality_model' 'P2-1 按生成模型' 'FAIL' $_.Exception.Message }

    # ============ P2-2 generation records ============
    try {
        $page = Invoke-Json 'Get' "/api/stats/generation-records?projectId=$projId&page=1&size=10" $auth $null
        $r0 = $page.data.records[0]
        $hasParams = $r0.model -and $r0.promptVersion -and $null -ne $r0.temperature
        if ($page.data.total -eq 3 -and $page.data.records.Count -eq 3 -and $hasParams) {
            Add-Result 'p2_records' 'P2-2 生成记录' 'PASS' 'total=3，model/temperature/prompt 参数完整'
        } else {
            Add-Result 'p2_records' 'P2-2 生成记录' 'FAIL' "total=$($page.data.total) params=$hasParams"
        }
    } catch { Add-Result 'p2_records' 'P2-2 生成记录' 'FAIL' $_.Exception.Message }

    # ============ P2-4 attribution accuracy ============
    try {
        $a = Invoke-Json 'Get' "/api/stats/attribution-accuracy?projectId=$projId" $auth $null
        $d = $a.data
        $ok = $true
        $checks = @(
            @($d.totalAnalyzed, 3, 'totalAnalyzed'),
            @($d.totalConfirmed, 2, 'totalConfirmed'),
            @($d.correct, 1, 'correct'),
            @($d.corrected, 1, 'corrected'),
            @($d.accuracy, 50, 'accuracy')
        )
        foreach ($c in $checks) {
            if ([Math]::Abs([double]$c[0] - [double]$c[1]) -gt 0.01) { $ok = $false; Add-Result 'p2_attr_overall' 'P2-4 归因准确率' 'FAIL' "$($c[2]): $($c[0]) != $($c[1])" }
        }
        if ($ok) { Add-Result 'p2_attr_overall' 'P2-4 归因准确率' 'PASS' '5 项指标正确' }
    } catch { Add-Result 'p2_attr_overall' 'P2-4 归因准确率' 'FAIL' $_.Exception.Message }

    # confirm with corrected category
    try {
        $a = Invoke-Json 'Get' "/api/stats/attribution-accuracy?projectId=$projId" $auth $null
        $target = $a.data.recentSamples | Where-Object { $_.corrected } | Select-Object -First 1
        if ($target) {
            $cfm = Invoke-Json 'Put' "/api/failures/$($target.id)/confirm" $auth (@{ category = 'env_error' } | ConvertTo-Json -Compress)
            if ($cfm.code -eq 0 -and $cfm.data.confirmed -eq 1 -and $cfm.data.confirmedCategory -eq 'env_error') {
                Add-Result 'p2_confirm_correct' 'P2-4 确认并修正分类' 'PASS' "id=$($target.id) -> env_error"
            } else {
                Add-Result 'p2_confirm_correct' 'P2-4 确认并修正分类' 'FAIL' "code=$($cfm.code) confirmedCategory=$($cfm.data.confirmedCategory)"
            }
        } else {
            Add-Result 'p2_confirm_correct' 'P2-4 确认并修正分类' 'FAIL' 'no confirmed sample found'
        }
    } catch { Add-Result 'p2_confirm_correct' 'P2-4 确认并修正分类' 'FAIL' $_.Exception.Message }

    # access control: global stats require admin
    try {
        $u = "p2user_$ts"
        $null = Invoke-Json 'Post' '/api/auth/register' @{} (@{ username = $u; password = 'Test123456'; nickname = 'p2user' } | ConvertTo-Json -Compress)
        $uLogin = Invoke-Json 'Post' '/api/auth/login' @{} (@{ username = $u; password = 'Test123456' } | ConvertTo-Json -Compress)
        $uAuth = @{ Authorization = "Bearer $($uLogin.data.token)" }
        $denied = $false
        try {
            $resp = Invoke-WebRequest -Uri "$base/api/stats/generation-quality" -Headers $uAuth -Method Get -UseBasicParsing
            $parsed = $resp.Content | ConvertFrom-Json
            $denied = $parsed.code -eq 403
        } catch {
            $denied = $true
        }
        if ($denied) { Add-Result 'p2_acl' 'P2 访问控制(非管理员403)' 'PASS' 'normal user denied' }
        else { Add-Result 'p2_acl' 'P2 访问控制(非管理员403)' 'FAIL' 'normal user got access' }
        $users = Invoke-Json 'Get' "/api/admin/users?page=1&size=50&keyword=$u" $auth $null
        foreach ($rec in $users.data.records) {
            $null = Invoke-Json 'Delete' "/api/admin/users/$($rec.id)" $auth $null
        }
    } catch { Add-Result 'p2_acl' 'P2 访问控制(非管理员403)' 'FAIL' $_.Exception.Message }

    # ============ cleanup ============
    try {
        $null = Invoke-Json 'Delete' "/api/projects/$projId" $auth $null
        Start-Sleep -Seconds 1
        $left = mysql -uroot -proot apigentest -N -e "SELECT COUNT(*) FROM generation_record WHERE project_id = $projId; SELECT COUNT(*) FROM test_case WHERE project_id = $projId;" 2>$null
        $parts = @($left | Where-Object { $_ -match '^\d+$' })
        if ($parts.Count -ge 2 -and [int]$parts[0] -eq 0 -and [int]$parts[1] -eq 0) {
            Add-Result 'p2_cleanup' 'P2 清理(级联含generation_record)' 'PASS' 'deleted project & records'
        } else {
            Add-Result 'p2_cleanup' 'P2 清理(级联含generation_record)' 'FAIL' "leftover=$($left -join ',')"
        }
    } catch { Add-Result 'p2_cleanup' 'P2 清理(级联含generation_record)' 'FAIL' $_.Exception.Message }
}

Write-Host ''
Write-Host '===== P2 stats smoke summary ====='
$pass = @($results | Where-Object { $_.status -eq 'PASS' }).Count
$fail = @($results | Where-Object { $_.status -eq 'FAIL' }).Count
Write-Host "PASS=$pass FAIL=$fail"
if ($fail -gt 0) { exit 2 } else { exit 0 }