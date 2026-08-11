# 初始化数据库：执行建表脚本与初始化数据
# 用法: .\init-db.ps1 [-User root] [-Password root]
param(
    [string]$User = "root",
    [string]$Password = "root"
)

$dbDir = Join-Path $PSScriptRoot "..\database"
$schema = (Join-Path $dbDir "01_schema.sql").Replace("\", "/")
$init = (Join-Path $dbDir "02_init_data.sql").Replace("\", "/")

Write-Host "[1/2] 执行建表脚本: $schema"
mysql -u $User "-p$Password" --default-character-set=utf8mb4 -e "source $schema"
if ($LASTEXITCODE -ne 0) { Write-Host "建表失败"; exit 1 }

Write-Host "[2/2] 执行初始化数据: $init"
mysql -u $User "-p$Password" --default-character-set=utf8mb4 -e "source $init"
if ($LASTEXITCODE -ne 0) { Write-Host "初始化失败"; exit 1 }

Write-Host "数据库初始化完成（库名 apigentest，默认管理员 admin / admin123）"