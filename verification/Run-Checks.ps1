param(
  [Parameter(Mandatory=$true)][string]$PaperLibraries,
  [Parameter(Mandatory=$true)][string]$PlaceholderApi,
  [Parameter(Mandatory=$true)][string]$Annotations,
  [Parameter(Mandatory=$true)][string]$Guava,
  [Parameter(Mandatory=$true)][string]$NbtApi,
  [Parameter(Mandatory=$true)][string]$BukkitVersion,
  [string]$ExpansionJar
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $ExpansionJar) { $ExpansionJar = Join-Path $projectRoot 'target/Expansion-CheckItem.jar' }
$testRoot = Join-Path $PSScriptRoot 'work'
$sourceCompile = Join-Path $testRoot 'source-api-compile'
$harnessClasses = Join-Path $testRoot 'harness-classes'
New-Item -ItemType Directory -Force $sourceCompile,$harnessClasses | Out-Null
$libs = @((Get-ChildItem -LiteralPath $PaperLibraries -Recurse -Filter '*.jar').FullName)
$shared = @((Resolve-Path -LiteralPath $PlaceholderApi).Path, (Resolve-Path -LiteralPath $Annotations).Path, (Resolve-Path -LiteralPath $Guava).Path)
$compileClasspath = $libs + $shared + (Resolve-Path -LiteralPath $NbtApi).Path
& javac -source 8 -target 8 -cp ($compileClasspath -join ';') -d $sourceCompile (Join-Path $projectRoot 'src/com/extendedclip/papi/expansion/checkitem/CheckItemExpansion.java')
if ($LASTEXITCODE -ne 0) { throw 'Compilation against the supplied APIs failed' }
$testClasspath = $libs + $shared
& javac -cp ($testClasspath -join ';') -d $harnessClasses (Join-Path $PSScriptRoot 'StaticCompatibility.java') (Join-Path $PSScriptRoot 'ModelSmoke.java')
if ($LASTEXITCODE -ne 0) { throw 'Verifier compilation failed' }
$runClasspath = @($harnessClasses, (Resolve-Path -LiteralPath $ExpansionJar).Path) + $testClasspath
& java -cp ($runClasspath -join ';') StaticCompatibility $ExpansionJar $BukkitVersion
if ($LASTEXITCODE -ne 0) { throw 'Static verification failed' }
& java -cp ($runClasspath -join ';') ModelSmoke $ExpansionJar $BukkitVersion
if ($LASTEXITCODE -ne 0) { throw 'Isolated model smoke tests failed' }
