$ErrorActionPreference = 'Stop'

# This file is intentionally ASCII-only so it survives any encoding edit.
# Paths are derived from the script location; the jar name suffix is built from char codes.
$patch = $PSScriptRoot
$root = Split-Path -Parent $patch
$build = Join-Path $patch 'build'
$javac = 'G:\java17\bin\javac.exe'
$java = 'G:\java17\bin\java.exe'
$asm = 'G:\MC\PCL\.minecraft\libraries\org\ow2\asm\asm\9.6\asm-9.6.jar'
# suffix = "GT" + U+7279 + U+4F9B  (Chinese suffix meaning "GT edition")
$outJar = Join-Path $root ('out\Torcherino-1.12.2-7.6-gtce-GT' + [char]0x7279 + [char]0x4F9B + '.jar')

Remove-Item -Recurse -Force $build -ErrorAction SilentlyContinue
$stubOut = Join-Path $build 'stub'
$hookOut = Join-Path $build 'hook'
$toolOut = Join-Path $build 'tools'
$fakeOut = Join-Path $build 'fake'
$testOut = Join-Path $build 'test'
New-Item -ItemType Directory -Force -Path $stubOut, $hookOut, $toolOut, $fakeOut, $testOut, (Join-Path $root 'out') | Out-Null

Write-Host '--- 1/5 compile stub ---'
& $javac --release 8 -encoding UTF-8 -d $stubOut (Join-Path $patch 'stub\net\minecraft\util\ITickable.java')
if ($LASTEXITCODE -ne 0) { throw 'stub compile failed' }

Write-Host '--- 2/5 compile GTCECompat ---'
& $javac --release 8 -encoding UTF-8 -cp $stubOut -d $hookOut (Join-Path $patch 'src\com\sci\torcherino\GTCECompat.java')
if ($LASTEXITCODE -ne 0) { throw 'hook compile failed' }

Write-Host '--- 3/5 compile Patcher ---'
& $javac -encoding UTF-8 -cp $asm -d $toolOut (Join-Path $patch 'tools\Patcher.java')
if ($LASTEXITCODE -ne 0) { throw 'patcher compile failed' }

Write-Host '--- 4/5 patch jar ---'
# strip UTF-8 BOM from overlay resources (Minecraft JSON/lang parsers dislike BOM)
Get-ChildItem -Recurse -File (Join-Path $patch 'resources') | ForEach-Object {
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    if ($bytes.Length -gt 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        [System.IO.File]::WriteAllBytes($_.FullName, $bytes[3..($bytes.Length - 1)])
        Write-Host ("stripped BOM: " + $_.Name)
    }
}
& $java -cp "$toolOut;$asm" Patcher (Join-Path $root 'torcherino-7.6.jar') $hookOut $outJar (Join-Path $patch 'resources')
if ($LASTEXITCODE -ne 0) { throw 'jar patch failed' }

Write-Host '--- verify hook classes inside jar ---'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipCheck = [System.IO.Compression.ZipFile]::OpenRead($outJar)
$hookEntries = $zipCheck.Entries | Where-Object { $_.FullName -like 'com/sci/torcherino/GTCECompat*' } | ForEach-Object { $_.FullName }
$zipCheck.Dispose()
$hookEntries | ForEach-Object { Write-Host ("  " + $_) }
if (-not ($hookEntries -contains 'com/sci/torcherino/GTCECompat$RecipeLogicAccess.class')) {
    throw 'missing inner class com/sci/torcherino/GTCECompat$RecipeLogicAccess.class in jar'
}

Write-Host '--- 5/5 offline hook test ---'
& $javac --release 8 -encoding UTF-8 -cp $stubOut -d $fakeOut (Get-ChildItem -Recurse -Filter *.java (Join-Path $patch 'test\fake') | ForEach-Object { $_.FullName })
if ($LASTEXITCODE -ne 0) { throw 'fake compile failed' }
& $javac -encoding UTF-8 -cp "$stubOut;$outJar;$fakeOut" -d $testOut (Join-Path $patch 'test\HookTest.java')
if ($LASTEXITCODE -ne 0) { throw 'test compile failed' }
# run the test against the packaged jar (not the compile output) so missing inner classes are caught
& $java -cp "$stubOut;$outJar;$fakeOut;$testOut" HookTest
if ($LASTEXITCODE -ne 0) { throw 'hook test failed' }

Write-Host "BUILD OK -> $outJar"
