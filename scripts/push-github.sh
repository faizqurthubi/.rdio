#!/usr/bin/env bash
# Push repo Github (deploy key). Repo: faizqurthubi/.rdio
# Prasyarat: public key ~/.ssh/otoradio_deploy.pub sudah ditambahkan di
#   GitHub -> repo -> Settings -> Deploy keys -> Allow write access
set -euo pipefail
SRC="${1:-/home/user/publish}"
REMOTE="git@github.com:faizqurthubi/.rdio.git"
KEY="$HOME/.ssh/otoradio_deploy"

cd "$SRC"
git config user.name  >/dev/null 2>&1 || git config user.name  "OtoRadio"
git config user.email >/dev/null 2>&1 || git config user.email "otoradio@local"

export GIT_SSH_COMMAND="ssh -i $KEY -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new"

git remote remove origin 2>/dev/null || true
git remote add origin "$REMOTE"

echo "→ cek koneksi";  ssh -i "$KEY" -o IdentitiesOnly=yes -o StrictHostKeyChecking=accept-new -T git@github.com 2>&1 | tail -1 || true
echo "→ push main";    git push -u origin main
echo "Selesai. Buka https://github.com/faizqurthubi/.rdio"

cat <<'TIP'

Langkah lanjutan (rilis pertama):
  1) Repo -> Settings -> Secrets and variables -> Actions, tambahkan:
       KEYSTORE_BASE64            = base64 -w0 keystore/otoradio-release.jks
       KEYSTORE_STORE_PASSWORD    = (password store)
       KEYSTORE_KEY_PASSWORD      = (password key)
       KEYSTORE_KEY_ALIAS         = otoradio
  2) Settings -> General -> Releases -> aktifkan "Enable release immutability"
  3) git tag v1.1 && git push origin v1.1     # Actions membangun APK & draft Release
TIP
