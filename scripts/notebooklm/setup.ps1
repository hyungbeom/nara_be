$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$VenvDir = Join-Path $ScriptDir ".venv"
$HomeDir = Join-Path $ProjectRoot "storage\notebooklm"

function Find-Python {
    $candidates = @("python", "python3", "py")
    foreach ($candidate in $candidates) {
        try {
            & $candidate --version 2>&1 | Out-Null
            if ($LASTEXITCODE -eq 0) {
                return $candidate
            }
        } catch {
            continue
        }
    }
    throw "Python 3.10+ is required."
}

$python = Find-Python

if (-not (Test-Path $VenvDir)) {
    & $python -m venv $VenvDir
}

$venvPython = Join-Path $VenvDir "Scripts\python.exe"
& $venvPython -m pip install --upgrade pip
& $venvPython -m pip install -r (Join-Path $ScriptDir "requirements.txt")

New-Item -ItemType Directory -Force -Path $HomeDir | Out-Null

Write-Host ""
Write-Host "NotebookLM setup complete." -ForegroundColor Green
Write-Host "Python: $venvPython"
Write-Host "Auth path: $HomeDir"
Write-Host ""
Write-Host "Next step (master token):" -ForegroundColor Yellow
$loginCmd = "& '$venvPython' -m notebooklm login --master-token --account YOUR_EMAIL@gmail.com"
Write-Host $loginCmd
Write-Host ""
Write-Host "Or use the NotebookLM tag in the web header and click Connect."
Write-Host ""
