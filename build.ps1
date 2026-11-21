param([string]$SdkPath = (Join-Path ([Environment]::GetFolderPath('UserProfile')) 'AppData\Local\Android\Sdk'))

$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$sdk = $SdkPath
$tools = Join-Path $sdk 'build-tools\35.0.0'
$android = Join-Path $sdk 'platforms\android-35\android.jar'
$out = Join-Path $PSScriptRoot 'build'
$classes = Join-Path $out 'classes'
New-Item -ItemType Directory -Force $classes | Out-Null

function Invoke-Checked {
    param([string]$Tool, [string[]]$Arguments)
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool terminou com código $LASTEXITCODE" }
}

$zxing = Join-Path $out 'zxing.jar'
if (!(Test-Path -LiteralPath $zxing)) {
    Invoke-WebRequest 'https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.3/core-3.5.3.jar' -OutFile $zxing
}
$sources = @(Get-ChildItem (Join-Path $PSScriptRoot 'src') -Filter '*.java' | Select-Object -ExpandProperty FullName)
Invoke-Checked "$env:JAVA_HOME\bin\javac.exe" (@('--release','17','-encoding','UTF-8','-cp',"$android;$zxing",'-d',$classes) + $sources)
Invoke-Checked "$env:JAVA_HOME\bin\jar.exe" @('--create','--file',"$out\app-classes.jar",'-C',$classes,'.')
Invoke-Checked "$tools\d8.bat" @('--min-api','31','--lib',$android,'--output',$out,"$out\app-classes.jar",$zxing)
$resources = Join-Path $out 'resources.zip'
Invoke-Checked "$tools\aapt2.exe" @('compile','--dir',"$PSScriptRoot\res",'-o',$resources)
Invoke-Checked "$tools\aapt2.exe" @('link','-I',$android,'--manifest',"$PSScriptRoot\AndroidManifest.xml",'-A',"$PSScriptRoot\assets",'-o',"$out\base.apk",$resources)
Push-Location $out
try {
    Invoke-Checked "$env:JAVA_HOME\bin\jar.exe" @('--update','--file','base.apk','classes.dex')
} finally {
    Pop-Location
}
Invoke-Checked "$tools\zipalign.exe" @('-f','4',"$out\base.apk","$out\aligned.apk")
$keystore = Join-Path $out 'me-debug.jks'
if (!(Test-Path -LiteralPath $keystore)) {
    Invoke-Checked "$env:JAVA_HOME\bin\keytool.exe" @('-genkeypair','-keystore',$keystore,'-alias','medebug','-storepass','android','-keypass','android','-keyalg','RSA','-keysize','2048','-validity','3650','-dname','CN=ME Bateria Teste')
}
$apk = Join-Path $out 'ME-Bateria-Piloto.apk'
Invoke-Checked "$tools\apksigner.bat" @('sign','--ks',$keystore,'--ks-pass','pass:android','--out',$apk,"$out\aligned.apk")
Invoke-Checked "$tools\apksigner.bat" @('verify','--verbose',$apk)
Get-Item -LiteralPath $apk
