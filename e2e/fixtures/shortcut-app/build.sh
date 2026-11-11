#!/bin/sh
#
# Copyright (C) 2026  Vijayyaso
#
# This file is part of HyperGesture.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

# Builds the shortcut test app with the plain SDK tools (aapt2, aapt, javac, d8, apksigner), so it
# needs no Gradle project. Prints the APK path.
#
# Usage: e2e/fixtures/shortcut-app/build.sh [out-dir]   (default: build/e2e-fixtures)

set -e

here="$(cd "$(dirname -- "$0")" && pwd)"
out="${1:-build/e2e-fixtures}"
mkdir -p "${out}"
out="$(cd "${out}" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"

# The newest platform and build tools installed.
platform="$(find "${ANDROID_HOME}/platforms" -maxdepth 1 -name 'android-[0-9]*' | sort -t- -k2 -n | tail -1)"
tools="$(find "${ANDROID_HOME}/build-tools" -mindepth 1 -maxdepth 1 | sort -V | tail -1)"
jar="${platform}/android.jar"
if [ ! -f "${jar}" ] || [ ! -x "${tools}/d8" ]; then
  echo "No android.jar or build tools under ${ANDROID_HOME}" >&2
  exit 1
fi

work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT
mkdir -p "${work}/classes" "${work}/dex"

"${tools}/aapt2" link -I "${jar}" --manifest "${here}/AndroidManifest.xml" -o "${work}/base.apk"
find "${here}/src" -name '*.java' >"${work}/sources"
javac -source 8 -target 8 -nowarn -Xlint:-options -bootclasspath "${jar}" \
  -d "${work}/classes" @"${work}/sources"
find "${work}/classes" -name '*.class' >"${work}/class-list"
"${tools}/d8" --min-api 30 --lib "${jar}" --output "${work}/dex" @"${work}/class-list"
(cd "${work}/dex" && "${tools}/aapt" add "${work}/base.apk" classes.dex >/dev/null)
"${tools}/zipalign" -f 4 "${work}/base.apk" "${work}/aligned.apk"

# The SDK debug key, or a throwaway one when there is none (CI).
key="${HOME}/.android/debug.keystore"
if [ ! -f "${key}" ]; then
  key="${work}/fixture.keystore"
  keytool -genkeypair -keystore "${key}" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -validity 10000 -dname 'CN=fixture' >/dev/null 2>&1
fi
"${tools}/apksigner" sign --ks "${key}" --ks-pass pass:android --key-pass pass:android \
  --out "${out}/shortcut-fixture.apk" "${work}/aligned.apk"
echo "${out}/shortcut-fixture.apk"
