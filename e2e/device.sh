#!/bin/sh
#
# Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
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

# Installs the app on a booted device and binds it, ready for any suite.
#
# Usage: e2e/device.sh [-p apk]
#   -p  APK to install (default: debug). Point at navgesture.apk to catch R8 breakage.

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

apk="app/build/outputs/apk/github/debug/app-github-debug.apk"
usage="usage: $0 [-p apk]"

while getopts 'p:h' opt; do
  case "${opt}" in
    p) apk="${OPTARG}" ;;
    h)
      echo "${usage}"
      exit 0
      ;;
    *)
      echo "${usage}" >&2
      exit 2
      ;;
  esac
done

require_adb

if [ ! -f "${apk}" ]; then
  echo "APK missing at ${apk}. Build it first: gradle assembleGithubDebug" >&2
  exit 1
fi

# Whether the service was on before the install decides the wait after it.
was_enabled=''
if "${ADB_BIN}" shell settings get secure enabled_accessibility_services 2>/dev/null | grep -q "${PKG}"; then
  was_enabled='1'
fi
"${ADB_BIN}" logcat -c >/dev/null 2>&1 || true

echo "== installing ${apk} ==" >&2
# A release and a debug build carry different keys, and Android refuses one over the other.
if ! "${ADB_BIN}" install -r -g "${apk}" >/dev/null 2>"${LOG_DIR}/install.err"; then
  if ! grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE "${LOG_DIR}/install.err"; then
    cat "${LOG_DIR}/install.err" >&2
    exit 1
  fi
  echo "   signed with another key, uninstalling it first" >&2
  "${ADB_BIN}" uninstall "${PKG}" >/dev/null
  "${ADB_BIN}" install -g "${apk}" >/dev/null
fi

# override <setting> <value> <why it matters>. Read in debuggable builds alone, and only
# before the service binds, or the first configuration emission misses it.
override() {
  "${ADB_BIN}" shell settings put global "$1" "$2" >/dev/null 2>&1 || true
  got="$("${ADB_BIN}" shell settings get global "$1" 2>/dev/null | tr -d '\r')"
  if [ "${got}" != "$2" ]; then
    echo "Could not set $1 (got '${got}')." >&2
    echo "$3" >&2
    exit 1
  fi
  echo "   $1=$2" >&2
}

# Flips the user-facing toggle, so a release build is tested through the path a user takes.
enable_edge_app_list() {
  open_home
  # shellcheck disable=SC2310 # the failure branch reports and exits, which is the point
  if ! set_toggle_id switch_edge_app_list true; then
    echo "The app-list cases cannot reach the hold while the feature is off." >&2
    exit 1
  fi
}

# Only a debuggable build reads the overrides. A release APK takes the toggle instead.
if "${ADB_BIN}" shell dumpsys package "${PKG}" 2>/dev/null | grep -q 'pkgFlags=.*DEBUGGABLE'; then
  # Force the side-edge hold on even if an earlier run saved it off.
  override navgesture_edge_app_list 1 \
    "The app-list cases cannot reach the hold while the feature is off."
else
  echo "   release build, so no debug override and the Recents hold keeps its default" >&2
  enable_edge_app_list
fi

# An install over a bound service kills it, and Android restarts it in a fresh process that
# runs unoptimised code for its first seconds. Switching the service off and on while that
# process is still starting leaves its windows on the dead connection ("token null"), so wait
# for the restart to finish first. Measured on a cold API 36 emulator, 2026-11-01.
if [ -n "${was_enabled}" ]; then
  settle=1
  while [ "${settle}" -le 40 ]; do
    if "${ADB_BIN}" logcat -d -s NavGesture 2>/dev/null | grep -q 'strip BOTTOM installed'; then
      echo "   the restarted service is up" >&2
      break
    fi
    sleep 1
    settle=$((settle + 1))
  done
fi

# A bank-pause run that stopped early leaves Settings picked as a bank app, and the other suites
# open Settings. The bank-pause suite sets it again itself.
"${ADB_BIN}" shell settings delete global navgesture_bank_apps >/dev/null 2>&1 || true

echo "== enabling the accessibility service ==" >&2
# Every case in every suite is meaningless if the service never bound.
# shellcheck disable=SC2310 # the failure branch reports and exits, which is the point
if ! enable_service; then
  echo "The accessibility service never bound. Reasons from logcat:" >&2
  "${ADB_BIN}" logcat -d 2>/dev/null | grep -iE 'AccessibilitySecurityPolicy|disallowed by AppOps' |
    tail -5 | sed 's/^/    /' >&2
  exit 1
fi
# Discovered here, while the bind-time log line still exists, and cached for the suites.
rm -f "${LOG_DIR}/geometry"
geometry
echo "device ready for the suites" >&2
