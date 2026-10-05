# Build the headless test harness (mod id corntest) into ..\..\out\corntest-<version>.jar.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\build-harness.ps1
#
# Same JDK, libs and compat libs as tools\build.ps1 (and the same overrides).

$ErrorActionPreference = 'Stop'
$repo   = Split-Path -Parent $PSScriptRoot
$work   = (Resolve-Path (Join-Path $repo '..\..')).Path
$jdk    = if ($env:COLONYPATCHES_JDK)    { $env:COLONYPATCHES_JDK }    else { Join-Path $work 'tools\jdk21' }
$libs   = if ($env:COLONYPATCHES_LIBS)   { $env:COLONYPATCHES_LIBS }   else { Join-Path $work 'libs' }
$compat = if ($env:COLONYPATCHES_COMPAT) { $env:COLONYPATCHES_COMPAT } else { Join-Path $work 'compat-libs' }
$javac  = Join-Path $jdk 'bin\javac.exe'
$jarx   = Join-Path $jdk 'bin\jar.exe'
$src    = Join-Path $repo 'tools\harness'

$patched = (Get-ChildItem $libs -Filter 'neoforge-*-client.jar' | Select-Object -First 1 | ForEach-Object { $_.FullName + ';' })
$build = Join-Path $repo 'build\harness'; $stubs = Join-Path $build 'stubs'; $classes = Join-Path $build 'classes'; $res = Join-Path $build 'res'
if (Test-Path $build) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory $stubs, $classes, (Join-Path $res 'META-INF') | Out-Null

function Write-Args($files, $path) {
  $list = @($files | ForEach-Object { $_.FullName.Replace('\', '/') })
  if ($list.Count -eq 0) { throw "no .java files for $path" }
  $list | Set-Content -Encoding ascii $path
}

Write-Args (Get-ChildItem (Join-Path $repo 'stubsrc') -Recurse -Filter *.java) (Join-Path $build 'stubs.args')
& $javac -encoding UTF-8 --release 21 -proc:none -nowarn -cp "$libs\*" -d $stubs "@$build\stubs.args"
if ($LASTEXITCODE) { throw 'stubs failed' }

Write-Args (Get-ChildItem (Join-Path $src 'src') -Recurse -Filter *.java) (Join-Path $build 'src.args')
& $javac -g -encoding UTF-8 --release 21 -proc:none -Xlint:-options -cp "$stubs;$patched$libs\*;$compat\*" -d $classes "@$build\src.args"
if ($LASTEXITCODE) { throw 'javac failed' }

$toml = Join-Path $src 'neoforge.mods.toml'
Copy-Item $toml (Join-Path $res 'META-INF\neoforge.mods.toml')
$ver = [regex]::Match((Get-Content $toml -Raw), '(?m)^version="([^"]+)"').Groups[1].Value
New-Item -ItemType Directory -Force (Join-Path $work 'out') | Out-Null
$jar = Join-Path $work "out\corntest-$ver.jar"
if (Test-Path $jar) { Remove-Item $jar -Force }
& $jarx cf $jar -C $classes . -C $res .
if ($LASTEXITCODE) { throw 'jar failed' }
"built $jar ($((Get-Item $jar).Length) bytes)"
