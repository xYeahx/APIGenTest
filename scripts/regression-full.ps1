# regression-full.ps1 - 全量功能回归（面向运行中的系统）
# 依赖: 后端 8081 运行中; mock-target 9090 运行中; mysql root/root
param([string]$Base = 'http://127.0.0.1:8081')
$ErrorActionPreference = 'Continue'
$env:HTTP_PROXY=''; $env:HTTPS_PROXY=''; $env:ALL_PROXY=''; $env:NO_PROXY='*'
$base = $Base
$root = 'E:\GuaduationProject\APIGenTest'
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

function Wait-Execution($execId, $auth, [int]$maxSec = 300) {
    $seq = @()
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $s = $null
    for ($i = 0; $i -lt ($maxSec / 2); $i++) {
        Start-Sleep -Milliseconds 2000
        $s = Invoke-Json 'Get' "/api/executions/$execId" $auth $null
        $st = $s.data.status
        $dc = $s.data.detailCount
        if ($seq.Count -eq 0 -or $seq[-1] -ne ("st=$st dc=$dc")) { $seq += ("st=$st dc=$dc") }
        if ($st -eq 1) { break }
    }
    $sw.Stop()
    return @{ summary = $s.data; seq = $seq; elapsedSec = [math]::Round($sw.Elapsed.TotalSeconds, 1) }
}

# ============ 登录 ============
try {
    $login = Invoke-Json 'Post' '/api/auth/login' @{} (@{ username = 'admin'; password = 'admin123' } | ConvertTo-Json -Compress)
    $token = $login.data.token
    if (-not $token) { throw 'no token' }
    $auth = @{ Authorization = "Bearer $token" }
    Add-Result 'auth_login' '认证-管理员登录' 'PASS' 'admin/admin123 -> token ok'
} catch { Write-Host 'FATAL: admin login failed'; exit 1 }

# ============ A. 认证与项目 ============
try {
    $me = Invoke-Json 'Get' '/api/auth/me' $auth $null
    Add-Result 'auth_me' '认证-获取当前用户' 'PASS' "username=$($me.data.username) role=$($me.data.role)"
} catch { Add-Result 'auth_me' '认证-获取当前用户' 'FAIL' $_.Exception.Message }

try {
    $projs = Invoke-Json 'Get' '/api/projects' $auth $null
    $ids = (($projs.data | ForEach-Object { $_.id }) -join ',')
    Add-Result 'project_list' '项目管理-项目列表' 'PASS' "projects=[$ids]"
} catch { Add-Result 'project_list' '项目管理-项目列表' 'FAIL' $_.Exception.Message }

# ============ B. 项目19真实环境回归（DummyJSON 在线 API） ============
try {
    $proj19 = Invoke-Json 'Get' '/api/projects/19' $auth $null
    Add-Result 'project19_detail' '项目管理-项目详情(19)' 'PASS' "name=$($proj19.data.name)"
} catch { Add-Result 'project19_detail' '项目管理-项目详情(19)' 'FAIL' $_.Exception.Message }

try {
    $apis19 = Invoke-Json 'Get' '/api/projects/19/apis?page=1&size=50' $auth $null
    Add-Result 'api_list19' '接口管理-接口列表(19)' 'PASS' "apiCount=$($apis19.data.total)"
} catch { Add-Result 'api_list19' '接口管理-接口列表(19)' 'FAIL' $_.Exception.Message }

try {
    $cases19 = Invoke-Json 'Get' '/api/projects/19/cases?page=1&size=100' $auth $null
    Add-Result 'case_list19' '用例管理-用例列表(19)' 'PASS' "caseCount=$($cases19.data.total)"
} catch { Add-Result 'case_list19' '用例管理-用例列表(19)' 'FAIL' $_.Exception.Message }

try {
    $runBody = @{ projectId = 19; environmentId = 10; scope = @{ type = 'all' } } | ConvertTo-Json -Compress -Depth 4
    $run = Invoke-Json 'Post' '/api/executions/run' $auth $runBody
    $execId = $run.data.executionId
    $w = Wait-Execution $execId $auth
    $d = $w.summary
    $seqStr = ($w.seq -join ' -> ')
    if ($d.totalCases -eq 22 -and $d.status -eq 1 -and $d.detailCount -ge 22) {
        Add-Result 'exec_run19' '执行引擎-项目19全量执行' 'PASS' "execId=$execId total=$($d.totalCases) passed=$($d.passed) failed=$($d.failed) rate=$($d.passRate)% duration=$($d.durationMs)ms poll=$($w.elapsedSec)s seq=[$seqStr]"
    } else {
        Add-Result 'exec_run19' '执行引擎-项目19全量执行' 'FAIL' "execId=$execId total=$($d.totalCases) passed=$($d.passed) failed=$($d.failed) status=$($d.status)"
    }
    $GLOBAL:exec19 = $execId
} catch { Add-Result 'exec_run19' '执行引擎-项目19全量执行' 'FAIL' $_.Exception.Message }

if ($GLOBAL:exec19) {
    try {
        $det = Invoke-Json 'Get' "/api/executions/$($GLOBAL:exec19)/details?page=1&size=50" $auth $null
        Add-Result 'exec_details19' '执行引擎-执行明细(19)' 'PASS' "detailCount=$($det.data.total)"
    } catch { Add-Result 'exec_details19' '执行引擎-执行明细(19)' 'FAIL' $_.Exception.Message }
    try {
        $rep = Invoke-Json 'Get' "/api/reports/$($GLOBAL:exec19)" $auth $null
        $r = $rep.data
        $errGrp = ($r.errorGroups | ForEach-Object { "$($_.error)(x$($_.count))" }) -join '; '
        if ($r.execution.id -eq $GLOBAL:exec19 -and $r.failedTop.Count -ge 1) {
            Add-Result 'report19' '测试报告-单次报告(19)' 'PASS' "failTopCount=$($r.failedTop.Count) errorGroups=$($r.errorGroups.Count) | $errGrp"
        } else {
            Add-Result 'report19' '测试报告-单次报告(19)' 'FAIL' 'no failedTop'
        }
    } catch { Add-Result 'report19' '测试报告-单次报告(19)' 'FAIL' $_.Exception.Message }
    try {
        $tr = Invoke-Json 'Get' '/api/projects/19/stats/trend?limit=20' $auth $null
        $t = $tr.data
        if ($t.points.Count -ge 2 -and $t.executionCount -ge 2) {
            Add-Result 'trend19' '测试报告-执行趋势(19)' 'PASS' "points=$($t.points.Count) execCount=$($t.executionCount) avgRate=$($t.avgPassRate)%"
        } else {
            Add-Result 'trend19' '测试报告-执行趋势(19)' 'FAIL' "points=$($t.points.Count) execCount=$($t.executionCount)"
        }
    } catch { Add-Result 'trend19' '测试报告-执行趋势(19)' 'FAIL' $_.Exception.Message }
}

# ============ C. LLM 连接与生成（临时项目） ============
try {
    $llm = Invoke-Json 'Post' '/api/admin/configs/test-llm' $auth $null
    Add-Result 'llm_test' '系统设置-LLM连接测试' 'PASS' "data=$($llm.data | ConvertTo-Json -Compress -Depth 3)"
} catch { Add-Result 'llm_test' '系统设置-LLM连接测试' 'FAIL' $_.Exception.Message }

$tmpProjectId = 0
try {
    $tp = Invoke-Json 'Post' '/api/projects' $auth (@{ name = "regression-tmp-$ts"; description = 'full regression temp project' } | ConvertTo-Json -Compress)
    $tmpProjectId = $tp.data.id
    $imp = & curl.exe -s -X POST "$base/api/projects/$tmpProjectId/import" -H "Authorization: Bearer $token" -F "file=@$root\docs\samples\openapi-users.json"
    $impObj = $imp | ConvertFrom-Json
    if ($impObj.code -eq 0) {
        Add-Result 'import_openapi' '接口管理-OpenAPI导入' 'PASS' "projectId=$tmpProjectId imported=$($impObj.data.total)"
    } else {
        Add-Result 'import_openapi' '接口管理-OpenAPI导入' 'FAIL' $imp
    }
} catch { Add-Result 'import_openapi' '接口管理-OpenAPI导入' 'FAIL' $_.Exception.Message }

if ($tmpProjectId -ne 0) {
    try {
        $apiPage = Invoke-Json 'Get' "/api/projects/$tmpProjectId/apis?page=1&size=50" $auth $null
        $apiList = @($apiPage.data.records)
        $apiLogin = ($apiList | Where-Object { $_.path -eq '/api/login' } | Select-Object -First 1).id
        $apiUsers = ($apiList | Where-Object { $_.path -eq '/api/users' } | Select-Object -First 1).id
        Add-Result 'api_list_tmp' '接口管理-解析结果' 'PASS' "apis=$($apiList.Count) login=$apiLogin users=$apiUsers"

        $envBody = @{ name = 'local-mock'; baseUrl = 'http://127.0.0.1:9090'; variables = '{"username":"admin","password":"123456"}' } | ConvertTo-Json -Compress
        $envr = Invoke-Json 'Post' "/api/projects/$tmpProjectId/environments" $auth $envBody
        $envId = $envr.data.id
        Add-Result 'env_create' '环境管理-创建环境' 'PASS' "envId=$envId"

        # AI 生成（真实 LLM, 状态为字符串枚举）
        $genBody = @{ apiIds = @([long]$apiLogin); businessDesc = '用户登录接口：用户名密码登录成功后返回 token 字段' } | ConvertTo-Json -Compress -Depth 4
        $gen = Invoke-Json 'Post' '/api/apis/generate' $auth $genBody
        $taskId = $gen.data.taskId
        $task = $null
        for ($i = 0; $i -lt 90; $i++) {
            Start-Sleep -Seconds 2
            $task = Invoke-Json 'Get' "/api/generations/$taskId" $auth $null
            $st = [string]$task.data.status
            if ($st -in @('SUCCESS','PARTIAL_FAILED','FAILED')) { break }
        }
        if ($task -and $task.data.status -in @('SUCCESS','PARTIAL_FAILED') -and $task.data.success -gt 0) {
            Add-Result 'ai_generate' 'AI生成-生成任务' 'PASS' "taskId=$taskId status=$($task.data.status) total=$($task.data.total) success=$($task.data.success) failed=$($task.data.failed)"
        } else {
            $err = if ($task) { "status=$($task.data.status) success=$($task.data.success) error=$($task.data.error)" } else { 'timeout' }
            Add-Result 'ai_generate' 'AI生成-生成任务' 'FAIL' $err
        }
        if ($task -and $task.data.status -in @('SUCCESS','PARTIAL_FAILED') -and $task.data.success -gt 0) {
            try {
                $conf = Invoke-Json 'Post' "/api/generations/$taskId/confirm" $auth $null
                Add-Result 'ai_confirm' 'AI生成-确认入库' 'PASS' "saved=$($conf.data.saved)"
            } catch { Add-Result 'ai_confirm' 'AI生成-确认入库' 'FAIL' $_.Exception.Message }
        }

        # 手动用例（依赖/变量/重试/异常）
        function New-Case([string]$name, $apiId, [string]$method, [string]$url, [string]$headers, [string]$body, [string]$asserts, [string]$extract, $pre) {
            $c = @{ projectId = $tmpProjectId; apiId = $apiId; name = $name; scenarioType = 'manual'; method = $method; urlTemplate = $url; status = 1 }
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
        $idLogin = New-Case 'login-success' $apiLogin 'POST' '{{baseUrl}}/api/login' '{"Content-Type":"application/json"}' '{"username":"{{env:username}}","password":"{{env:password}}"}' $assertLogin $extractToken $null
        $idUsers = New-Case 'users-with-token' $apiUsers 'GET' '{{baseUrl}}/api/users' '{"Authorization":"Bearer {{token}}"}' $null '[{"type":"statusCode","expect":200}]' $null $idLogin
        $idBoom = New-Case 'boom-should-fail' $null 'GET' '{{baseUrl}}/api/boom' $null $null '[{"type":"statusCode","expect":200}]' $null $null
        $idRefused = New-Case 'refused-connection' $null 'GET' 'http://127.0.0.1:1/api/x' $null $null '[]' $null $null
        Add-Result 'case_create' '用例管理-手动创建' 'PASS' "ids=$idLogin,$idUsers,$idBoom,$idRefused"

        # 批量操作（只操作额外用例，不影响主流程用例）
        try {
            $extra = New-Case 'extra-temp-case' $null 'GET' '{{baseUrl}}/api/orders' $null $null '[]' $null $null
            $bs = Invoke-Json 'Put' '/api/cases/batch-status' $auth (@{ ids = @([long]$extra); status = 0 } | ConvertTo-Json -Compress -Depth 4)
            $bd = Invoke-Json 'Delete' '/api/cases/batch' $auth (@{ ids = @([long]$extra) } | ConvertTo-Json -Compress -Depth 4)
            Add-Result 'case_batch' '用例管理-批量禁用/删除' 'PASS' "disable=$($bs.code -eq 0) delete=$($bd.code -eq 0)"
        } catch { Add-Result 'case_batch' '用例管理-批量禁用/删除' 'FAIL' $_.Exception.Message }

        # 执行临时项目（依赖/变量/重试/异常分类）
        try {
            $runBody2 = @{ projectId = $tmpProjectId; environmentId = $envId; scope = @{ type = 'all' } } | ConvertTo-Json -Compress -Depth 4
            $run2 = Invoke-Json 'Post' '/api/executions/run' $auth $runBody2
            $execId2 = $run2.data.executionId
            $w2 = Wait-Execution $execId2 $auth
            $d2 = $w2.summary
            $det2 = Invoke-Json 'Get' "/api/executions/$execId2/details?page=1&size=20" $auth $null
            $map = @{}
            foreach ($r in $det2.data.records) { if (-not $map.ContainsKey($r.caseName)) { $map[$r.caseName] = $r } }
            $loginOk = ($map['login-success'].status -eq 1)
            $usersOk = ($map['users-with-token'].status -eq 1)
            $boomOk = ($map['boom-should-fail'].status -eq 2 -and $map['boom-should-fail'].retryCount -ge 1)
            $refusedOk = ($map['refused-connection'].status -eq 3 -and $map['refused-connection'].retryCount -ge 1)
            if ($d2.totalCases -ge 4 -and $loginOk -and $usersOk -and $boomOk -and $refusedOk) {
                Add-Result 'exec_tmp' '执行引擎-依赖/变量/重试/异常' 'PASS' "execId=$execId2 total=$($d2.totalCases) passed=$($d2.passed) failed=$($d2.failed) login=$loginOk users=$usersOk boom=$boomOk refused=$refusedOk seq=[$($w2.seq -join ' -> ')]"
            } else {
                Add-Result 'exec_tmp' '执行引擎-依赖/变量/重试/异常' 'FAIL' "execId=$execId2 total=$($d2.totalCases) passed=$($d2.passed) failed=$($d2.failed) login=$loginOk users=$usersOk boom=$boomOk refused=$refusedOk"
            }
            $GLOBAL:failedDetail = $map['boom-should-fail'].id
        } catch { Add-Result 'exec_tmp' '执行引擎-依赖/变量/重试/异常' 'FAIL' $_.Exception.Message }

        # 失败归因（真实 LLM）
        if ($GLOBAL:failedDetail) {
            try {
                $fa = Invoke-Json 'Post' "/api/failures/$($GLOBAL:failedDetail)/analyze" $auth $null
                if ($fa.data.category) {
                    Add-Result 'failure_analyze' '失败归因-LLM分析' 'PASS' "detailId=$($GLOBAL:failedDetail) category=$($fa.data.category) model=$($fa.data.llmModel)"
                    try {
                        $fc = Invoke-Json 'Put' "/api/failures/$($fa.data.id)/confirm" $auth $null
                        Add-Result 'failure_confirm' '失败归因-人工确认' 'PASS' "confirmed=$($fc.data.confirmed)"
                    } catch { Add-Result 'failure_confirm' '失败归因-人工确认' 'FAIL' $_.Exception.Message }
                } else {
                    Add-Result 'failure_analyze' '失败归因-LLM分析' 'FAIL' "no category: $($fa | ConvertTo-Json -Compress -Depth 4)"
                }
            } catch { Add-Result 'failure_analyze' '失败归因-LLM分析' 'FAIL' $_.Exception.Message }
        }

        # 定时任务
        try {
            $prev = Invoke-Json 'Post' '/api/tasks/cron-preview' $auth (@{ cron = '0 0 2 * * ?' } | ConvertTo-Json -Compress)
            $nextStr = (($prev.data | Select-Object -First 3) -join ',')
            $taskBody = @{ name = "reg-task-$ts"; cron = '0 0 2 * * ?'; environmentId = $envId; scope = @{ type = 'all' }; enabled = 1 } | ConvertTo-Json -Compress -Depth 4
            $tr = Invoke-Json 'Post' "/api/projects/$tmpProjectId/tasks" $auth $taskBody
            $taskId2 = $tr.data.id
            $tl = Invoke-Json 'Get' "/api/projects/$tmpProjectId/tasks" $auth $null
            $found = @($tl.data.records | Where-Object { $_.id -eq $taskId2 }).Count -gt 0
            Invoke-Json 'Delete' "/api/tasks/$taskId2" $auth $null | Out-Null
            if ($found) { Add-Result 'task_crud' '定时任务-预览/创建/删除' 'PASS' "next=[$nextStr] taskId=$taskId2" }
            else { Add-Result 'task_crud' '定时任务-预览/创建/删除' 'FAIL' 'not found in list' }
        } catch { Add-Result 'task_crud' '定时任务-预览/创建/删除' 'FAIL' $_.Exception.Message }

        # CI 集成（token 从 DB 读取，不重新生成）
        try {
            $ciInfo = Invoke-Json 'Get' '/api/admin/ci/token' $auth $null
            $ciToken = (& mysql -uroot -proot apigentest -N -e "SELECT config_value FROM sys_config WHERE config_key='ci_token'" 2>$null | Select-Object -First 1).Trim()
            $ciBody = @{ projectId = $tmpProjectId; environmentId = $envId; scope = @{ type = 'all' } } | ConvertTo-Json -Compress -Depth 4
            $ciRun = Invoke-RestMethod -Uri "$base/api/ci/run" -Method Post -Headers @{ 'X-CI-Token' = $ciToken } -ContentType 'application/json' -Body $ciBody
            $ciExec = $ciRun.data.executionId
            $w3 = Wait-Execution $ciExec $auth
            if ($ciInfo.data.configured -and $w3.summary.triggerType -eq 3) {
                Add-Result 'ci_run' 'CI集成-免登录触发执行' 'PASS' "execId=$ciExec trigger=$($w3.summary.triggerType) total=$($w3.summary.totalCases)"
            } else {
                Add-Result 'ci_run' 'CI集成-免登录触发执行' 'FAIL' "configured=$($ciInfo.data.configured) trigger=$($w3.summary.triggerType)"
            }
        } catch { Add-Result 'ci_run' 'CI集成-免登录触发执行' 'FAIL' $_.Exception.Message }

        # 通知
        try {
            $uc = Invoke-Json 'Get' '/api/notifications/unread-count' $auth $null
            $nl = Invoke-Json 'Get' '/api/notifications?page=1&size=5' $auth $null
            Invoke-Json 'Put' '/api/notifications/read-all' $auth $null | Out-Null
            Add-Result 'notification' '通知-未读/列表/已读' 'PASS' "unread=$($uc.data.count) total=$($nl.data.total)"
        } catch { Add-Result 'notification' '通知-未读/列表/已读' 'FAIL' $_.Exception.Message }

        # 管理员
        try {
            $cfg = Invoke-Json 'Get' '/api/admin/configs' $auth $null
            $keys = (($cfg.data | ForEach-Object { $_.configKey }) -join ',')
            $us = Invoke-Json 'Get' '/api/admin/users?page=1&size=50' $auth $null
            Add-Result 'admin_config' '管理员-系统配置/用户' 'PASS' "configKeys=[$keys] users=$($us.data.total)"
        } catch { Add-Result 'admin_config' '管理员-系统配置/用户' 'FAIL' $_.Exception.Message }

        # 清理临时项目 + 级联删除验证
        try {
            Invoke-Json 'Delete' "/api/projects/$tmpProjectId" $auth $null | Out-Null
            $verify = "SELECT (SELECT COUNT(*) FROM project WHERE id=$tmpProjectId) p, (SELECT COUNT(*) FROM api_info WHERE project_id=$tmpProjectId) a, (SELECT COUNT(*) FROM test_case WHERE project_id=$tmpProjectId) c, (SELECT COUNT(*) FROM environment WHERE project_id=$tmpProjectId) e, (SELECT COUNT(*) FROM execution WHERE project_id=$tmpProjectId) x"
            $vout = ($verify | & mysql -uroot -proot apigentest -N 2>$null).Trim()
            if ($vout -match '^0\s+0\s+0\s+0\s+0') { Add-Result 'project_delete' '项目管理-删除+级联清理' 'PASS' "cascade=[$vout]" }
            else { Add-Result 'project_delete' '项目管理-删除+级联清理' 'FAIL' "cascade=[$vout]" }
        } catch { Add-Result 'project_delete' '项目管理-删除+级联清理' 'FAIL' $_.Exception.Message }
    } catch {
        Add-Result 'tmp_flow' '临时项目流程' 'FAIL' $_.Exception.Message
        if ($tmpProjectId -ne 0) { try { Invoke-Json 'Delete' "/api/projects/$tmpProjectId" $auth $null | Out-Null } catch { } }
    }
}

# ============ 汇总 ============
$passN = @($results | Where-Object { $_.status -eq 'PASS' }).Count
$failN = @($results | Where-Object { $_.status -eq 'FAIL' }).Count
Write-Host ''
Write-Host "========== FULL REGRESSION SUMMARY =========="
Write-Host "TOTAL=$($results.Count) PASS=$passN FAIL=$failN"
$out = [pscustomobject]@{ time = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'; base = $base; total = $results.Count; pass = $passN; fail = $failN; items = $results }
$outFile = 'E:\GuaduationProject\files\测试\full-regression-result.json'
$out | ConvertTo-Json -Depth 6 | Set-Content -Path $outFile -Encoding UTF8
Write-Host "result saved: $outFile"
if ($failN -gt 0) { exit 1 } else { exit 0 }