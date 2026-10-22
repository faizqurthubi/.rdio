#!/usr/bin/env bash
# Memulihkan toolchain build OtoRadio setelah pembersihan workspace.
# Butuh internet. Total unduhan ± 700 MB.
set -euo pipefail

ROOT=/home/user
TOOLS="$ROOT/tools"
SDK="$ROOT/android-sdk"
mkdir -p "$TOOLS" "$SDK"

# ---------- 1. JDK 17 ----------
if [ ! -x "$TOOLS/jdk-17.0.20.1+1/bin/java" ]; then
  echo "==> JDK 17"
  curl -fsSL -o /tmp/jdk.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  mkdir -p "$TOOLS"
  tar -xzf /tmp/jdk.tar.gz -C "$TOOLS"
  rm -f /tmp/jdk.tar.gz
  # normalisasi nama folder agar sama dengan dokumentasi
  jdk_dir=$(find "$TOOLS" -maxdepth 1 -type d -name 'jdk-17*' | head -1)
  [ "$jdk_dir" = "$TOOLS/jdk-17.0.20.1+1" ] || ln -sfn "$jdk_dir" "$TOOLS/jdk-17.0.20.1+1"
fi
export JAVA_HOME="$TOOLS/jdk-17.0.20.1+1"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

# ---------- 2. Gradle 8.9 ----------
if [ ! -x "$TOOLS/gradle-8.9/bin/gradle" ]; then
  echo "==> Gradle 8.9"
  curl -fsSL -o /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-8.9-bin.zip"
  unzip -q /tmp/gradle.zip -d "$TOOLS"
  rm -f /tmp/gradle.zip
fi

# ---------- 3. Android SDK ----------
CMDLINE="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$CMDLINE" ]; then
  echo "==> Android cmdline-tools"
  curl -fsSL -o /tmp/cmdline.zip \
    "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  mkdir -p "$SDK/cmdline-tools"
  unzip -q /tmp/cmdline.zip -d /tmp/cmdline
  mv /tmp/cmdline/cmdline-tools "$SDK/cmdline-tools/latest"
  rm -rf /tmp/cmdline /tmp/cmdline.zip
fi

export ANDROID_SDK_ROOT="$SDK"
export ANDROID_HOME="$SDK"
echo "==> Paket SDK (platform-35, build-tools 35, platform-tools)"
yes | "$CMDLINE" --licenses >/dev/null 2>&1 || true
"$CMDLINE" --install "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null

# ---------- 4. local.properties ----------
printf 'sdk.dir=%s\n' "$SDK" > "$ROOT/otoradio/local.properties"

echo
echo "Selesai. Build dengan:"
echo "  export JAVA_HOME=$JAVA_HOME"
echo "  export PATH=\$JAVA_HOME/bin:\$PATH"
echo "  cd $ROOT/otoradio && ./gradlew :app:assembleDebug --no-daemon"
echo
echo "Opsional (uji emulator, ±4 GB lagi): lihat docs/06-uji-emulator.md"
