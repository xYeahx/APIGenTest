# 启动后端开发服务（端口 8081）
$backend = Join-Path $PSScriptRoot "..\backend"
Set-Location $backend
if ($env:MAVEN_HOME) {
    & "$env:MAVEN_HOME\bin\mvn.cmd" spring-boot:run
} else {
    mvn spring-boot:run
}