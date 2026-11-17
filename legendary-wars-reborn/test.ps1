$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSCommandPath
$workspaceRoot=Split-Path -Parent $projectRoot
$jdkRoot=Join-Path $workspaceRoot '.tools\jdk'
$out=Join-Path $projectRoot 'build\tests'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$tests=@(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tools') -Filter '*.test.mjs' | ForEach-Object {$_.FullName})
node --test @tests
if($LASTEXITCODE -ne 0){throw 'Asset safety tests failed'}
$javaRoot=Join-Path $projectRoot 'app\src\main\java\com\legendary\offline'
$src=@('UnitSpec.java','Progress.java','BattleEngine.java','BattlePlan.java','OriginalBattle.java') | ForEach-Object {Join-Path $javaRoot $_}
$src+=Join-Path $projectRoot 'tests\EngineTest.java'
$src+=Join-Path $projectRoot 'tests\CampaignTest.java'
$src+=Join-Path $projectRoot 'tests\OriginalEngineTest.java'
$src+=Join-Path $projectRoot 'tests\RecoveredScenarioTest.java'
$src+=Join-Path $projectRoot 'tests\stubs\android\content\res\AssetManager.java'
$src+=Join-Path $javaRoot 'GameData.java'
$jsonJar=Join-Path $workspaceRoot '.tools\combat-static\json-20240303.jar'
if((Get-FileHash -LiteralPath $jsonJar -Algorithm SHA256).Hash.ToLower() -ne '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'){throw 'Test JSON library integrity'}
& (Join-Path $jdkRoot 'bin\javac.exe') --release 8 -Xlint:-options -encoding UTF-8 -classpath $jsonJar -d $out @src
if($LASTEXITCODE -ne 0){throw 'Engine test compilation failed'}
& (Join-Path $jdkRoot 'bin\java.exe') -ea -cp $out com.legendary.offline.EngineTest
if($LASTEXITCODE -ne 0){throw 'Engine tests failed'}
& (Join-Path $jdkRoot 'bin\java.exe') -ea -cp $out com.legendary.offline.OriginalEngineTest
if($LASTEXITCODE -ne 0){throw 'Original scenario tests failed'}
& (Join-Path $jdkRoot 'bin\java.exe') -ea -cp "$out;$jsonJar" com.legendary.offline.RecoveredScenarioTest (Join-Path $projectRoot 'app\src\main\assets\game-data.json')
if($LASTEXITCODE -ne 0){throw 'Recovered source scenario tests failed'}
node (Join-Path $projectRoot 'tools\export-test-specs.mjs')
if($LASTEXITCODE -ne 0){throw 'Actual unit fixture export failed'}
& (Join-Path $jdkRoot 'bin\java.exe') -ea -cp $out com.legendary.offline.CampaignTest (Join-Path $projectRoot 'build\unit-specs.tsv')
if($LASTEXITCODE -ne 0){throw 'Campaign simulation failed'}
