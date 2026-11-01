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

# Bank pause: a picked app switches the service off and gives the phone its own bar back, and
# the service comes back once the user leaves, or at once from the tile. Settings stands in for
# a bank app through the debug-only navgesture_bank_apps override, so this needs a debug build.
# Runs before disclosure, which clears the app's data.

# Every predicate below is a test result that pass or fail reports, never an error to exit on,
# and the command output in a failure message is diagnostic text only.
# shellcheck disable=SC2310,SC2312

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb

BANK='com.android.settings'

if ! "${ADB_BIN}" shell dumpsys package "${PKG}" 2>/dev/null | grep -q 'pkgFlags=.*DEBUGGABLE'; then
  echo "   release build: bank pause needs the debug-only override, skipped" >&2
  report "BANK PAUSE"
  exit 0
fi

listed() {
  "${ADB_BIN}" shell settings get secure enabled_accessibility_services 2>/dev/null |
    tr -d '\r' | grep -q "${PKG}"
}

watcher_running() {
  "${ADB_BIN}" shell dumpsys activity services "${PKG}" 2>/dev/null | grep -q 'BankPauseWatcher'
}

# wait_for <seconds> <predicate...> - true once the predicate holds, polled every second.
wait_for() {
  limit="$1"
  shift
  waited=0
  while [ "${waited}" -lt "${limit}" ]; do
    if "$@"; then
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  return 1
}

not_listed() { ! listed; }

open_bank() {
  "${ADB_BIN}" shell am start -W -a android.settings.SETTINGS >/dev/null 2>&1 || true
}

echo >&2
echo "== set up: the adb grant, usage access, and Settings as the bank app ==" >&2
"${ADB_BIN}" shell pm grant "${PKG}" android.permission.WRITE_SECURE_SETTINGS >/dev/null
"${ADB_BIN}" shell appops set "${PKG}" GET_USAGE_STATS allow >/dev/null
"${ADB_BIN}" shell settings put global navgesture_bank_apps "${BANK}" >/dev/null
# The override is read when the service starts, so rebind it.
if enable_service; then pass "the service is bound with ${BANK} picked"; else fail "the service is bound with ${BANK} picked"; fi
"${ADB_BIN}" shell input keyevent KEYCODE_HOME >/dev/null
sleep 2

echo >&2
echo "== opening the picked app switches the service off ==" >&2
reset_log
open_bank
if wait_for 8 not_listed; then
  pass "the service left the Accessibility list"
else
  fail "the service left the Accessibility list (list: $("${ADB_BIN}" shell settings get secure enabled_accessibility_services | tr -d '\r'))"
fi
if watcher_running; then pass "the watcher runs during the pause"; else fail "the watcher runs during the pause"; fi
assert_log "the pause was logged" "BANK_PAUSE ${BANK}"
assert_no_log "the pause was not undone" "BANK_PAUSE_UNDONE"

echo >&2
echo "== leaving the picked app brings the service back ==" >&2
"${ADB_BIN}" shell input keyevent KEYCODE_HOME >/dev/null
if wait_for 25 service_bound; then
  pass "the service is back after leaving"
else
  fail "the service is back after leaving (the emulator may report no usage events)"
fi
if wait_for 5 sh -c "! \"${ADB_BIN}\" shell dumpsys activity services ${PKG} 2>/dev/null | grep -q BankPauseWatcher"; then
  pass "the watcher stopped"
else
  fail "the watcher stopped"
fi

echo >&2
echo "== the tile during a pause brings the service back at once ==" >&2
sleep 2
reset_log
open_bank
if wait_for 8 not_listed; then
  pass "a second open pauses again"
else
  fail "a second open pauses again"
  gesture_log | grep -E 'SERVICE_LOG|BANK' | tail -8 | sed 's/^/        /' >&2
fi
# click-tile reaches only a tile that is in the panel. After the earlier suites SystemUI's copy
# of the tile went stale and swallowed the click (seen 3 runs of 3 in the full order, never when
# this suite ran alone), so the tile is removed and added again first.
"${ADB_BIN}" shell cmd statusbar remove-tile "${PKG}/.setup.GestureTileService" >/dev/null 2>&1 || true
sleep 2
"${ADB_BIN}" shell cmd statusbar add-tile "${PKG}/.setup.GestureTileService" >/dev/null 2>&1 || true
sleep 3
tile_try=1
while [ "${tile_try}" -le 3 ]; do
  "${ADB_BIN}" shell cmd statusbar click-tile "${PKG}/.setup.GestureTileService" >/dev/null 2>&1 || true
  if wait_for 5 service_bound; then
    break
  fi
  tile_try=$((tile_try + 1))
done
if service_bound; then
  pass "the tile resumed gestures (tap ${tile_try})"
else
  fail "the tile resumed gestures"
  gesture_log | grep -E 'SERVICE_LOG|BANK' | tail -8 | sed 's/^/        /' >&2
fi
open_bank
sleep 4
if listed; then
  pass "the Resume-now hold keeps the service on inside the picked app"
else
  fail "the Resume-now hold keeps the service on inside the picked app"
fi

echo >&2
echo "== clean up ==" >&2
"${ADB_BIN}" shell input keyevent KEYCODE_HOME >/dev/null
"${ADB_BIN}" shell settings delete global navgesture_bank_apps >/dev/null 2>&1 || true
if enable_service; then pass "the service is bound with no app picked"; else fail "the service is bound with no app picked"; fi

report "BANK PAUSE"
