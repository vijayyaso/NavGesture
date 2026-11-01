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

# Google Play's prominent disclosure: every Turn on shows it until the user agrees, Not now
# opens nothing, and the tile's intent lands on it. Switches the service off and clears the
# app's data first (status-refresh.sh's sequence), so run it last; it binds the service again
# at the end.

# Every predicate below is a test result that pass or fail reports, never an error to exit on,
# and the front activity in a failure message is diagnostic text only.
# shellcheck disable=SC2310,SC2312

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb

service_off="$(string_res status_off)"

on_disclosure() {
  find_id disclosure_agree >/dev/null 2>&1
}

our_app_in_front() {
  resumed_activity | grep -q "${PKG}/"
}

echo >&2
echo "== a fresh install has no consent and the service is off ==" >&2
"${ADB_BIN}" shell settings delete secure enabled_accessibility_services >/dev/null 2>&1 || true
"${ADB_BIN}" shell pm clear "${PKG}" >/dev/null
sleep 2
open_home
shown="$(text_of_id service_status || true)"
if [ "${shown}" = "${service_off}" ]; then
  pass "home reads off"
else
  fail "home reads off (the screen shows '${shown:-nothing}')"
fi

echo >&2
echo "== Turn on shows the disclosure, Not now opens nothing ==" >&2
tap_id status_turn_on || echo "   no Turn on button" >&2
sleep 1
if on_disclosure; then pass "Turn on shows the disclosure"; else fail "Turn on shows the disclosure"; fi
tap_id disclosure_not_now || echo "   no Not now button" >&2
sleep 1
if our_app_in_front && ! on_disclosure; then
  pass "Not now returns to the app"
else
  fail "Not now returns to the app (front: $(resumed_activity))"
fi

echo >&2
echo "== Turn on again shows the disclosure again ==" >&2
tap_id status_turn_on || echo "   no Turn on button" >&2
sleep 1
if on_disclosure; then pass "the disclosure shows again"; else fail "the disclosure shows again"; fi
to_home

echo >&2
echo "== the tile's intent opens on the disclosure while consent is missing ==" >&2
"${ADB_BIN}" shell am start -n "${PKG}/.ui.MainActivity" \
  --ez com.vijayyaso.navgesture.SHOW_DISCLOSURE true >/dev/null 2>&1
sleep 2
if on_disclosure; then pass "the tile intent shows the disclosure"; else fail "the tile intent shows the disclosure"; fi

echo >&2
echo "== Agree opens Android's Accessibility settings ==" >&2
tap_id disclosure_agree || echo "   no Agree button" >&2
sleep 3
if resumed_activity | grep -q 'com.android.settings'; then
  pass "Agree opens Accessibility settings"
else
  fail "Agree opens Accessibility settings (front: $(resumed_activity))"
fi

echo >&2
echo "== after Agree, Turn on goes straight to Settings ==" >&2
# Settings opened inside the app's task, so Back returns to the app's home.
"${ADB_BIN}" shell input keyevent KEYCODE_BACK >/dev/null
sleep 2
open_home
if our_app_in_front && ! on_disclosure; then
  pass "back on the app's home"
else
  fail "back on the app's home (front: $(resumed_activity))"
fi
tap_id status_turn_on || echo "   no Turn on button" >&2
sleep 3
if resumed_activity | grep -q 'com.android.settings' && ! on_disclosure; then
  pass "consent is remembered: Turn on opens Settings directly"
else
  fail "consent is remembered (front: $(resumed_activity))"
fi

"${ADB_BIN}" shell input keyevent KEYCODE_HOME >/dev/null
"${ADB_BIN}" shell settings put secure accessibility_enabled 1 >/dev/null
"${ADB_BIN}" shell settings put secure enabled_accessibility_services "${SERVICE}" >/dev/null

report "DISCLOSURE"
