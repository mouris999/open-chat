param([switch]$Reupload)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$proj = "open-hub-apk"
$store = "openchat-apk"
$storeUrlPrefix = "https://wtnvvv8fy2rwk1ne.public.blob.vercel-storage.com"

$projectRoot = Split-Path $PSScriptRoot -Parent
$apkRelease = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
$apkDebug = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$apk = if (Test-Path $apkRelease) { $apkRelease } else { $apkDebug }
if (-not (Test-Path $apk)) { throw "APK not found: $apkRelease nor $apkDebug" }

$gradle = Get-Content (Join-Path $projectRoot 'app\build.gradle.kts') -Raw
if ($gradle -match 'versionName\s*=\s*"([^"]+)"') { $version = $Matches[1] } else { $version = '1.0.0' }
$apkInfo = Get-Item $apk
$size = [math]::Round($apkInfo.Length / 1MB, 1)
$date = $apkInfo.LastWriteTime.ToString('yyyy-MM-dd')

function Render-Site($url) {
    New-Item -ItemType Directory -Force 'dist' | Out-Null
    (Get-Content 'index.html' -Raw) `
        -replace '__APK_URL__', $url `
        -replace '__APK_VERSION__', $version `
        -replace '__APK_SIZE__', "$size MB" `
        -replace '__APK_DATE__', $date |
        Set-Content 'dist\index.html' -Encoding UTF8
}

function Get-BlobToken {
    $line = Get-Content '.env.local' | Where-Object { $_ -match '^BLOB_READ_WRITE_TOKEN=' }
    if (-not $line) { throw "BLOB_READ_WRITE_TOKEN not found in .env.local. Create it with: vercel blob create-store $store --access public --yes" }
    return (($line -replace '^BLOB_READ_WRITE_TOKEN=', '') -replace '^"', '' -replace '"$', '')
}

write-host "== Step 1: link project =="
if (-not (Test-Path '.vercel')) {
    npx --yes vercel link --yes --project $proj
    npx --yes vercel blob create-store $store --access public --yes | Out-Null
}

write-host "== Step 2: Blob store + APK upload =="
$blobEnv = Get-BlobToken
$env:BLOB_READ_WRITE_TOKEN = $blobEnv

$uploaded = [bool](cmd /c "npx --yes vercel blob list 2>&1" | Select-String -Quiet 'openchat\.apk')
if ($Reupload -or -not $uploaded) {
    write-host "Uploading $apk ..."
    $putOut = cmd /c "npx --yes vercel blob put `"$apk`" --access public --pathname openchat.apk --allow-overwrite true --cache-control-max-age 0 2>&1"
    if ($LASTEXITCODE -ne 0) { throw "blob put failed: $putOut" }
    $url = ($putOut | Select-String -Pattern 'https://[A-Za-z0-9.-]+\.public\.blob\.vercel-storage\.com/openchat\.apk' | ForEach-Object { $_.Matches.Value } | Select-Object -First 1)
    if (-not $url) { throw "Could not read upload URL. Output was:`n$putOut" }
} else {
    $url = "$storeUrlPrefix/openchat.apk"
    write-host "openchat.apk already uploaded. Reusing $url (use -Reupload to force)"
}
write-host "APK live at: $url"

write-host "== Step 3: rebuild site with the real download link =="
Render-Site $url

write-host "== Step 4: deploy =="
npx --yes vercel --prod --yes --name $proj ./dist