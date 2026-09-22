param(
    [string]$RemoteUrl = 'https://github.com/Vyshnavi08-vel/TechNova.git'
)

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Write-Error "git not found. Install git and try again."
    exit 1
}

if (-not (Test-Path -Path .git -PathType Container)) {
    git init | Out-Null
}

git checkout -B main

git add -A

git commit -m "Initial commit" --allow-empty

try {
    git remote remove origin -ErrorAction SilentlyContinue
} catch {}

git remote add origin $RemoteUrl

git push -u origin main
