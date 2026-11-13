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

# Run-time app shortcuts, github build. A test app (e2e/fixtures/shortcut-app) publishes
# shortcuts the way GPay does: action RUN to its launcher activity, with a string extra whose
# key has spaces. Without the one-time DUMP grant the corner picker shows the computer step and
# the corner finds nothing; with it the picker lists the shortcut, and a corner swipe starts it
# with its extras rebuilt, of the right types. Shortcuts the dump cannot rebuild (a data URI)
# or that aim at an activity that is not exported are never listed or run.
#
# Uses the debug-only corner overrides, so it needs the debug APK. Expects a device prepared by
# e2e/device.sh. Run e2e/run.sh for everything.

# The helpers called in conditions below report their own misses through pass/fail.
# shellcheck disable=SC2310
set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb

if ! "${ADB_BIN}" shell dumpsys package "${PKG}" 2>/dev/null | grep -q 'pkgFlags=.*DEBUGGABLE'; then
  echo "app-shortcuts suite needs the debug APK (it sets debug-only overrides); skipped" >&2
  exit 0
fi
if ! "${ADB_BIN}" shell dumpsys package "${PKG}" 2>/dev/null | grep -q 'android.permission.DUMP'; then
  echo "app-shortcuts suite needs the github build (play does not ask for DUMP); skipped" >&2
  exit 0
fi

FIX="com.vijayyaso.navgesture.fixture.shortcuts"
FIX_LABEL="NG Shortcut Fixture"

echo "== building and installing the shortcut test app ==" >&2
if ! fixture_apk="$(e2e/fixtures/shortcut-app/build.sh "${LOG_DIR}/fixture" 2>"${LOG_DIR}/fixture-build.err")"; then
  cat "${LOG_DIR}/fixture-build.err" >&2
  fail "the shortcut test app builds"
  report "APP SHORTCUTS" || exit 1
fi
"${ADB_BIN}" install -r "${fixture_apk}" >/dev/null
# It publishes its shortcuts when it starts.
"${ADB_BIN}" logcat -c >/dev/null 2>&1 || true
"${ADB_BIN}" shell am start -W -n "${FIX}/.LauncherActivity" >/dev/null
sleep 2
if "${ADB_BIN}" logcat -d -s NGFixture 2>/dev/null | grep -q 'FIXTURE_PUBLISHED true 4'; then
  pass "the test app published its four shortcuts"
else
  fail "the test app published its four shortcuts"
fi

# The usage app-op as device.sh left it, put back by cleanup so later suites see no change.
usage_mode="$("${ADB_BIN}" shell appops get "${PKG}" GET_USAGE_STATS 2>/dev/null | tr -d '\r' |
  sed -n 's/^GET_USAGE_STATS: \([a-z]*\).*/\1/p')"

grant() {
  "${ADB_BIN}" shell pm grant "${PKG}" android.permission.DUMP >/dev/null
  "${ADB_BIN}" shell pm grant "${PKG}" android.permission.PACKAGE_USAGE_STATS >/dev/null
  "${ADB_BIN}" shell appops set "${PKG}" GET_USAGE_STATS allow >/dev/null
}

# left_corner <value> - the debug override for the left corner, then a rebind to read it.
left_corner() {
  "${ADB_BIN}" shell settings put global navgesture_left_corner "$1" >/dev/null
  if enable_service; then pass "rebound with the left corner on $1"; else fail "rebound with the left corner on $1"; fi
}

# open_left_picker - Gesture settings, then the left corner's picker page. Twice if need be: just
# after a rebind the app can still be starting, and the first taps land on nothing.
# Its own counter: open_screen resets open_try, which would make this loop run for ever.
open_left_picker() {
  picker_try=1
  while [ "${picker_try}" -le 2 ]; do
    open_screen row_gesture_settings
    if tap_id row_left_corner; then
      sleep 3
      title="$(string_res corner_section_app_shortcuts)"
      if ui_nodes | grep -q "text=\"${title}\""; then
        return 0
      fi
    fi
    picker_try=$((picker_try + 1))
  done
  return 1
}

# The picked corner is stored, so put it back to Nothing through the picker, as a user would.
reset_stored_corner() {
  "${ADB_BIN}" shell settings delete global navgesture_left_corner >/dev/null 2>&1 || true
  open_left_picker || true
  nothing="$(string_res corner_none)"
  tap_node "$(ui_nodes | grep -m1 "text=\"${nothing}\"" || true)"
  sleep 1
}

cleanup() {
  "${ADB_BIN}" shell settings delete global navgesture_left_corner >/dev/null 2>&1 || true
  grant
  "${ADB_BIN}" shell appops set "${PKG}" GET_USAGE_STATS "${usage_mode:-default}" >/dev/null 2>&1 || true
  "${ADB_BIN}" uninstall "${FIX}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# fixture_has <grep-pattern> - whether the test app logged that since the last reset_log.
fixture_has() {
  "${ADB_BIN}" logcat -d -s NGFixture 2>/dev/null | tr -d '\r' | grep -q -- "$1"
}

assert_fixture() {
  if fixture_has "$2"; then pass "$1"; else fail "$1 (the test app logged no '$2')"; fi
}

assert_no_fixture() {
  if fixture_has "$2"; then fail "$1 (the test app logged '$2')"; else pass "$1"; fi
}

geometry

echo >&2
echo "== without the grant: the picker shows the computer step, the corner finds nothing ==" >&2
# Revoking a permission stops the app, so the rebind below comes after it.
"${ADB_BIN}" shell pm revoke "${PKG}" android.permission.DUMP >/dev/null
left_corner "shortcut:${FIX}/qr_scanner"
if open_left_picker; then pass "the left corner picker opens"; else fail "the left corner picker opens"; fi
if find_id runtime_shortcuts_hint >/dev/null; then
  pass "the picker shows the one-time computer step"
else
  fail "the picker shows the one-time computer step"
fi
if tap_id runtime_shortcuts_copy; then
  sleep 1
  copied="$(string_res label_copied)"
  if ui_nodes | grep -q "text=\"${copied}\""; then pass "Copy commands copies"; else fail "Copy commands copies"; fi
else
  fail "Copy commands copies (no button)"
fi
# Android 13 and later show a clipboard preview at the bottom left for a few seconds.
sleep 5
if ui_nodes | grep -q "resource-id=\"corner_app_${FIX}\""; then
  fail "the test app has no shortcuts listed without the grant"
else
  pass "the test app has no shortcuts listed without the grant"
fi
open_test_app
reset_log
corner_swipe 30 1
assert_log "the corner runs the stored shortcut" "CORNER_SHORTCUT shortcut:${FIX}/qr_scanner"
assert_log "it is reported as not found" "shortcut:${FIX}/qr_scanner: shortcut not found"
assert_no_fixture "the test app was not started" "FIXTURE_LAUNCH"

echo >&2
echo "== with the grant: pick Scan QR in the picker, and the corner starts it with its extra ==" >&2
grant
"${ADB_BIN}" shell settings delete global navgesture_left_corner >/dev/null
if enable_service; then pass "rebound with no corner override"; else fail "rebound with no corner override"; fi
open_left_picker || true
if ui_nodes | grep -q 'resource-id="runtime_shortcuts_hint"'; then
  fail "the computer step is gone once granted"
else
  pass "the computer step is gone once granted"
fi
# On a slow emulator the list is still settling when the first tap lands: the row moves once
# the run-time shortcuts are read, and the tap opens nothing. So tap again until the rows show.
# A tap is repeated only when no row showed, so it never closes an opened app.
listed=''
app_seen=''
tap_try=1
while [ "${tap_try}" -le 4 ] && [ -z "${listed}" ]; do
  # shellcheck disable=SC2310 # a missing row is reported after the loop
  if tap_id "corner_app_${FIX}"; then app_seen='1'; fi
  wait_try=1
  while [ "${wait_try}" -le 4 ] && [ -z "${listed}" ]; do
    sleep 1
    listed="$(ui_nodes | grep -o "resource-id=\"corner_shortcut_${FIX}/[^\"]*\"" | sed 's/.*\///; s/"$//' | sort | paste -sd, -)"
    wait_try=$((wait_try + 1))
  done
  tap_try=$((tap_try + 1))
done
if [ -n "${app_seen}" ]; then pass "the test app is listed under App shortcuts"; else fail "the test app is listed under App shortcuts"; fi
if [ "${listed}" = "qr_scanner,typed" ]; then
  pass "it lists Scan QR and Typed extras, not the data-URI or private ones"
else
  fail "it lists Scan QR and Typed extras, not the data-URI or private ones (listed: '${listed}')"
  # Saved for the CI artifact: what the app read, and what the shortcut service holds.
  "${ADB_BIN}" logcat -d -s NavGesture >"${LOG_DIR}/app-shortcuts-logcat.txt" 2>&1 || true
  "${ADB_BIN}" shell dumpsys shortcut >"${LOG_DIR}/app-shortcuts-dump.txt" 2>&1 || true
  ui_nodes >"${LOG_DIR}/app-shortcuts-screen.xml" 2>&1 || true
fi
if tap_id "corner_shortcut_${FIX}/qr_scanner"; then pass "Scan QR can be picked"; else fail "Scan QR can be picked"; fi
sleep 2
# The page closes on a pick; the corner rows sit below the touch area on Gesture settings, so
# scroll to them.
find_id row_left_corner >/dev/null 2>&1 || true
if ui_nodes | grep -q "text=\"${FIX_LABEL}: Scan QR\""; then
  pass "the corner row shows the picked shortcut"
else
  fail "the corner row shows the picked shortcut"
fi
open_test_app
reset_log
corner_swipe 30 1
assert_log "the corner runs the picked shortcut" "CORNER_SHORTCUT shortcut:${FIX}/qr_scanner"
assert_no_log "nothing failed" "shortcut not found"
assert_fixture "the test app opened with action RUN" "FIXTURE_LAUNCH action=RUN"
assert_fixture "the GPay-style string extra arrived" "FIXTURE_EXTRA some unique action key|String|QR_SCANNER"

echo >&2
echo "== typed extras arrive with their types ==" >&2
left_corner "shortcut:${FIX}/typed"
open_test_app
reset_log
corner_swipe 30 1
assert_fixture "the test app opened" "FIXTURE_LAUNCH action=RUN"
assert_fixture "an int stays an int" "FIXTURE_EXTRA count|Integer|3"
assert_fixture "a long stays a long" "FIXTURE_EXTRA big|Long|9999999999"
assert_fixture "a boolean stays a boolean" "FIXTURE_EXTRA on|Boolean|true"
assert_fixture "a comma stays inside its string" "FIXTURE_EXTRA note|String|a, b"
assert_fixture "an equals sign stays inside its string" "FIXTURE_EXTRA pair|String|x=y"

for id in with_data private; do
  echo >&2
  echo "== the ${id} shortcut is never run ==" >&2
  left_corner "shortcut:${FIX}/${id}"
  open_test_app
  reset_log
  corner_swipe 30 1
  assert_log "it is reported as not found" "shortcut:${FIX}/${id}: shortcut not found"
  assert_no_fixture "the test app was not started" "FIXTURE_LAUNCH"
done

reset_stored_corner
report "APP SHORTCUTS"
