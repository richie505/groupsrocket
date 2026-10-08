#!/usr/bin/env bash
# Builds the Windows app on Linux:
#   Rocket-Prep-Setup.exe        installer (Start menu + desktop shortcut, uninstaller)
#   Rocket-Prep-Windows.zip      portable copy: unzip and double-click "Rocket Prep.exe"
# Needs: gcc-mingw-w64-x86-64, nsis, unzip, zip, curl.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$ROOT/tools/windows"
WORK="$ROOT/desktop/build/windows"
VERSION="$(sed -n 's/^val appVersion = "\(.*\)"/\1/p' "$ROOT/desktop/build.gradle.kts")"
MAJOR="${VERSION%%.*}"; MINOR="${VERSION#*.}"; MINOR="${MINOR%%.*}"
STAGE="$WORK/Rocket Prep"

echo "== app jars (version $VERSION)"
"$ROOT/gradlew" -q -p "$ROOT" :desktop:windowsApp -Ptarget=windows

echo "== Java runtime for Windows (only the modules the app needs)"
# jlink must be the same Java version as the Windows modules, so fetch both JDKs from one release.
REL="$(curl -sSf "https://api.adoptium.net/v3/info/release_names?version=%5B17,18%29&release_type=ga&vendor=eclipse&sort_order=DESC&page_size=1" | python3 -c 'import json,sys; print(json.load(sys.stdin)["releases"][0])')"
JDKS="$WORK/jdk/$REL"
if [ ! -d "$JDKS/linux" ] || [ ! -d "$JDKS/windows" ]; then
  rm -rf "$WORK/jdk"; mkdir -p "$JDKS/linux" "$JDKS/windows"
  api="https://api.adoptium.net/v3/binary/version/$(python3 -c 'import sys,urllib.parse; print(urllib.parse.quote(sys.argv[1]))' "$REL")"
  curl -sSfL "$api/linux/x64/jdk/hotspot/normal/eclipse" | tar xz -C "$JDKS/linux"
  curl -sSfL -o "$WORK/jdk/win.zip" "$api/windows/x64/jdk/hotspot/normal/eclipse"
  unzip -q "$WORK/jdk/win.zip" -d "$JDKS/windows" && rm "$WORK/jdk/win.zip"
fi
rm -rf "$STAGE"
mkdir -p "$STAGE"
"$JDKS"/linux/*/bin/jlink --module-path "$(echo "$JDKS"/windows/*/jmods)" \
  --add-modules java.base,java.desktop,jdk.unsupported,jdk.accessibility \
  --strip-debug --no-header-files --no-man-pages --compress=0 --output "$STAGE/runtime"
echo "   $REL"
# Windows 10/11 always use their own C runtime (UCRT), so the copies for Windows 7/8 are dead weight;
# Nor does the app use sound, JPEG or a splash screen, and Java 2D falls back to plain Java
# when its imaging library (mlib_image) is missing.
rm -f "$STAGE"/runtime/bin/api-ms-win-*.dll "$STAGE"/runtime/bin/ucrtbase.dll \
  "$STAGE"/runtime/bin/{jsound,javajpeg,splashscreen,mlib_image}.dll \
  "$STAGE"/runtime/bin/{keytool,jabswitch,jaccessinspector,jaccesswalker}.exe

echo "== app jars, stored uncompressed so the installer's LZMA packs them tighter"
mkdir -p "$STAGE/app"
python3 - "$WORK/app" "$STAGE/app" <<'PY'
import sys, zipfile, pathlib, re
src, dst = map(pathlib.Path, sys.argv[1:])
for jar in sorted(src.glob("*.jar")):
    name = re.sub(r"-[0-9a-f]{28,32}\.jar$", ".jar", jar.name)  # drop ProGuard's hash suffix
    with zipfile.ZipFile(jar) as zin, zipfile.ZipFile(dst / name, "w", zipfile.ZIP_STORED) as zout:
        for info in zin.infolist():
            zout.writestr(info.filename, zin.read(info), zipfile.ZIP_STORED)
PY

echo "== launcher"
sed "s/@VERSION@/$VERSION/g; s/@MAJOR@/$MAJOR/g; s/@MINOR@/$MINOR/g" "$HERE/launcher.rc.in" > "$WORK/launcher.rc"
cp "$HERE/app.ico" "$WORK/app.ico"
x86_64-w64-mingw32-windres "$WORK/launcher.rc" -O coff -o "$WORK/launcher.res"
x86_64-w64-mingw32-gcc -O2 -s -municode -mwindows -o "$STAGE/Rocket Prep.exe" "$HERE/launcher.c" "$WORK/launcher.res"

echo "== installer"
makensis -V2 -DVERSION="$VERSION" -DSRC="$STAGE" -DOUT="$ROOT/Rocket-Prep-Setup.exe" "-X!cd $HERE" "$HERE/installer.nsi"

echo "== portable zip"
rm -f "$ROOT/Rocket-Prep-Windows.zip"
(cd "$WORK" && zip -qr -9 "$ROOT/Rocket-Prep-Windows.zip" "Rocket Prep")

ls -la "$ROOT/Rocket-Prep-Setup.exe" "$ROOT/Rocket-Prep-Windows.zip"
