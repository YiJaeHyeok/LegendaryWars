param([switch]$SkipTests)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSCommandPath
$workspaceRoot=Split-Path -Parent $projectRoot
if(!$SkipTests){& (Join-Path $projectRoot 'test.ps1')}
$junctionRoot=Join-Path $env:TEMP ('LegendaryBuild-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Junction -Path $junctionRoot -Target $workspaceRoot | Out-Null
try {
    $project=Join-Path $junctionRoot 'legendary-wars-reborn'
    $jdkRoot=Join-Path $junctionRoot '.tools\jdk'
    $sdkRoot=Join-Path $junctionRoot '.tools\android-sdk'
    $buildTools=Join-Path $sdkRoot 'build-tools\35.0.0'
    $androidJar=Join-Path $sdkRoot 'platforms\android-35\android.jar'
    $output=Join-Path $project ('build\apk-'+[Guid]::NewGuid().ToString('N'))
    foreach($sub in @('classes','generated','dex')){New-Item -ItemType Directory -Force -Path (Join-Path $output $sub) | Out-Null}
    function Check-Step([string]$step){if($LASTEXITCODE -ne 0){throw "$step failed ($LASTEXITCODE)"}}
    & (Join-Path $buildTools 'aapt2.exe') compile --dir (Join-Path $project 'app\src\main\res') -o (Join-Path $output 'resources.zip')
    Check-Step 'Resources'
    $unsigned=Join-Path $output 'unsigned.apk'
    & (Join-Path $buildTools 'aapt2.exe') link -o $unsigned -I $androidJar --manifest (Join-Path $project 'app\src\main\AndroidManifest.xml') --java (Join-Path $output 'generated') --min-sdk-version 26 --target-sdk-version 35 --version-code 3 --version-name '0.3.0' (Join-Path $output 'resources.zip')
    Check-Step 'Resource linking'
    $src=@(Get-ChildItem -LiteralPath (Join-Path $project 'app\src\main\java'),(Join-Path $output 'generated') -Recurse -Filter '*.java' | ForEach-Object {$_.FullName})
    & (Join-Path $jdkRoot 'bin\javac.exe') --release 8 -Xlint:-options -encoding UTF-8 -classpath $androidJar -d (Join-Path $output 'classes') @src
    Check-Step 'Java compile'
    $classes=@(Get-ChildItem -LiteralPath (Join-Path $output 'classes') -Recurse -Filter '*.class' | ForEach-Object {$_.FullName})
    & (Join-Path $jdkRoot 'bin\java.exe') -cp (Join-Path $buildTools 'lib\d8.jar') com.android.tools.r8.D8 --lib $androidJar --min-api 26 --output (Join-Path $output 'dex') @classes
    Check-Step 'DEX compile'
    # The Windows aapt2 -A implementation preserves backslashes in nested asset
    # names. JDK jar normalizes names to '/', and storing WAV permits openFd.
    & (Join-Path $jdkRoot 'bin\jar.exe') uf0 $unsigned -C (Join-Path $project 'app\src\main') assets -C (Join-Path $output 'dex') classes.dex
    Check-Step 'Package code'
    $aligned=Join-Path $output 'aligned.apk'
    & (Join-Path $buildTools 'zipalign.exe') -f -p 4 $unsigned $aligned
    Check-Step 'Alignment'
    $keyDir=Join-Path $project '.local'
    New-Item -ItemType Directory -Force -Path $keyDir | Out-Null
    $keystore=Join-Path $keyDir 'legendary-development.keystore'
    if(!(Test-Path -LiteralPath $keystore)){
        & (Join-Path $jdkRoot 'bin\keytool.exe') -genkeypair -noprompt -keystore $keystore -storepass android -keypass android -alias legendary-local -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Legendary Offline Local Build,O=Personal Restoration,C=KR'
        Check-Step 'Local signing key'
    }
    $dist=Join-Path $junctionRoot 'dist'
    New-Item -ItemType Directory -Force -Path $dist | Out-Null
    $apk=Join-Path $dist 'legendary-wars-offline.apk'
    & (Join-Path $jdkRoot 'bin\java.exe') -jar (Join-Path $buildTools 'lib\apksigner.jar') sign --ks $keystore --ks-key-alias legendary-local --ks-pass pass:android --key-pass pass:android --out $apk $aligned
    Check-Step 'Signing'
    & (Join-Path $jdkRoot 'bin\java.exe') -jar (Join-Path $buildTools 'lib\apksigner.jar') verify --verbose --print-certs $apk | Tee-Object -FilePath (Join-Path $project 'build\signature-verification.txt')
    Check-Step 'Signature verification'
    & (Join-Path $buildTools 'aapt.exe') dump permissions $apk | Tee-Object -FilePath (Join-Path $project 'build\packaged-permissions.txt')
    Check-Step 'Permission inspection'
    node (Join-Path $project 'tools\audit-apk.mjs')
    Check-Step 'Packaged code and resource audit'
    $sha=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLower()
    [IO.File]::WriteAllText((Join-Path $dist 'legendary-wars-offline.SHA256.txt'),"$sha  legendary-wars-offline.apk`n",[Text.UTF8Encoding]::new($false))
    Write-Output "APK ready: $workspaceRoot\dist\legendary-wars-offline.apk"
} finally {
    # Delete only our junction itself, never recurse into its target.
    if(Test-Path -LiteralPath $junctionRoot){[IO.Directory]::Delete($junctionRoot,$false)}
}
