#!/usr/bin/env bash
# Menyiapkan satu rilis OtoRadio: build, salin APK, checksum, version.json, draf changelog.
# Pakai:  bash scripts/prepare-release.sh "1.2" "Artwork 1:1, playlist, dll"
set -euo pipefail

VERSION="${1:?pakai: prepare-release.sh <versi> [catatan rilis]}"
NOTES="${2:-}"
ROOT=/home/user
PROJ="$ROOT/otoradio"
DIST="$ROOT/dist"

# Toolchain: pakai yang ada, atau pulihkan dulu
export JAVA_HOME="${JAVA_HOME:-/opt/tc/jdk-17.0.20.1+1}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/tc/sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "Toolchain tidak ada. Jalankan dulu: bash scripts/restore-toolchain.sh" >&2
  exit 1
fi

APK_OUT="$PROJ/app/build/outputs/apk/release/app-release.apk"
APK_NAME="OtoRadio-${VERSION}-release.apk"

# 1. Cek versi di build.gradle.kts sudah benar
VNAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$PROJ/app/build.gradle.kts" | head -1)
VCODE=$(grep -oP 'versionCode\s*=\s*\K[0-9]+' "$PROJ/app/build.gradle.kts" | head -1)
if [ "$VNAME" != "$VERSION" ]; then
  echo "PERINGATAN: versionName di build.gradle.kts = $VNAME, argumen = $VERSION" >&2
  echo "           ubah dulu: versionCode = $((VCODE+1)) dan versionName = \"$VERSION\"" >&2
  exit 2
fi

# 2. Build
cd "$PROJ"
./gradlew :app:assembleRelease --no-daemon --console=plain

# 3. Sanity check isi APK
LOGOS=$(unzip -l "$APK_OUT" | grep -c 'assets/logos/' || true)
[ "$LOGOS" -ge 600 ] || { echo "APK kehilangan logo ($LOGOS). Batal." >&2; exit 3; }
unzip -p "$APK_OUT" assets/stations.json | head -c 40 | grep -q '_meta' \
  || { echo "stations.json tidak ikut di APK. Batal." >&2; exit 3; }

# 4. Salin + checksum
mkdir -p "$DIST"
cp "$APK_OUT" "$DIST/$APK_NAME"
cd "$DIST"
rm -f SHA256.txt SHA256SUMS
sha256sum "$APK_NAME" > SHA256SUMS
cp SHA256SUMS SHA256.txt
SIZE=$(stat -c%s "$APK_NAME")
SHA=$(cut -d' ' -f1 SHA256SUMS)

# 5. version.json (untuk updater di dalam app / cek manual)
cat > version.json <<JSON
{
  "versionCode": $VCODE,
  "versionName": "$VERSION",
  "apk": "$APK_NAME",
  "sha256": "$SHA",
  "size": $SIZE,
  "minSdk": 26,
  "notes": "$NOTES",
  "releasedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
JSON

# 6. Draf changelog
cat > "CHANGELOG-v${VERSION}.md" <<MD
## OtoRadio v${VERSION} (versionCode $VCODE)

$NOTES

- APK: \`$APK_NAME\` ($SIZE byte)
- sha256: \`$SHA\`
- minSdk 26 (Android 8.0+), targetSdk 35

Unduh & pasang dari HP, atau \`adb install -r $APK_NAME\`.
MD

cat <<EOT

Rilis siap:
  dist/$APK_NAME   ($SIZE byte)
  dist/SHA256SUMS  ($SHA)
  dist/version.json
  dist/CHANGELOG-v$VERSION.md

Langkah berikutnya (pilih satu):
  # GitHub Releases (disarankan)
  cd $ROOT && git add -A && git commit -m "rilis v$VERSION" && git tag v$VERSION && git push --tags
  gh release create v$VERSION dist/$APK_NAME dist/SHA256SUMS dist/version.json \\
      --title "OtoRadio $VERSION" --notes-file dist/CHANGELOG-v$VERSION.md

  # atau cukup salin dist/$APK_NAME ke HP lalu ketuk (update menimpa, data tetap)
EOT
