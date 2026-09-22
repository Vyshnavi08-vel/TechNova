#!/usr/bin/env bash
set -e

if ! command -v git >/dev/null 2>&1; then
  echo "git not found. Install git and try again." >&2
  exit 1
fi

if [ ! -d .git ]; then
  git init
fi

git checkout -B main

git add -A

git commit -m "Initial commit" --allow-empty

git remote remove origin 2>/dev/null || true

git remote add origin https://github.com/Vyshnavi08-vel/TechNova.git

git push -u origin main
