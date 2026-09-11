param(
    [string]$BrowserPath = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $repoRoot "image\架构图\source"
$outputRoot = Join-Path $repoRoot "image\架构图"

if ([string]::IsNullOrWhiteSpace($BrowserPath)) {
    $candidates = @(
        "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        "C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        "C:\Program Files\Google\Chrome\Application\chrome.exe",
        "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
    )
    $BrowserPath = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}

if ([string]::IsNullOrWhiteSpace($BrowserPath) -or -not (Test-Path -LiteralPath $BrowserPath)) {
    throw "未找到 Edge 或 Chrome；可通过 -BrowserPath 指定浏览器路径。"
}

$diagrams = @(
    @{ Source = "InterWise-系统架构图.html"; Output = "InterWise-系统架构图.png"; Width = 1800; Height = 1180 },
    @{ Source = "InterWise-RAG流程图.html"; Output = "InterWise-RAG流程图.png"; Width = 1800; Height = 1360 }
)

foreach ($diagram in $diagrams) {
    $source = Join-Path $sourceRoot $diagram.Source
    $output = Join-Path $outputRoot $diagram.Output
    $temporaryOutput = Join-Path $outputRoot (".render-" + [Guid]::NewGuid().ToString("N") + ".png")
    if (-not (Test-Path -LiteralPath $source)) {
        throw "图源不存在：$source"
    }

    $uri = [Uri]::new($source).AbsoluteUri
    $arguments = @(
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--force-device-scale-factor=1",
        "--window-size=$($diagram.Width),$($diagram.Height)",
        "--screenshot=$temporaryOutput",
        $uri
    )
    try {
        $process = Start-Process -FilePath $BrowserPath -ArgumentList $arguments -Wait -PassThru -WindowStyle Hidden
        if ($process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $temporaryOutput)) {
            throw "渲染失败：$($diagram.Source)"
        }

        Add-Type -AssemblyName System.Drawing
        $image = [System.Drawing.Image]::FromFile($temporaryOutput)
        try {
            if ($image.Width -ne $diagram.Width -or $image.Height -ne $diagram.Height) {
                throw "图片尺寸异常：$($image.Width)x$($image.Height)，预期 $($diagram.Width)x$($diagram.Height)"
            }
        } finally {
            $image.Dispose()
        }
        Move-Item -LiteralPath $temporaryOutput -Destination $output -Force
    } finally {
        if (Test-Path -LiteralPath $temporaryOutput) {
            Remove-Item -LiteralPath $temporaryOutput -Force
        }
    }
    Write-Host "已生成 $output"
}
