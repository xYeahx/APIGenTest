# 启动前端开发服务（端口 5173）
$frontend = Join-Path $PSScriptRoot "..\frontend"
Set-Location $frontend
npm run dev