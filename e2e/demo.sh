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

# The practice demo: a Back and a Home drawn on its phone each pass their step, and neither
# leaves the demo. The service is on throughout, so a practice swipe that ran a real Back or
# Home would close the screen. The lookup helpers scroll the page with swipes across the middle
# of the screen, which on the demo land on the drawn phone and count as tries, so on the demo
# this suite reads one dump at a time and taps with tap_node.
#
# Expects a device prepared by e2e/device.sh. Run e2e/run.sh for everything.

# Every predicate below is a test result that pass or fail reports, never an error to exit on.
# shellcheck disable=SC2310,SC2312

set -e

# shellcheck source-path=SCRIPTDIR
# shellcheck source=lib
. "$(dirname -- "$0")/lib"

require_adb
geometry
warm_input

phone_shown() {
  ui_nodes | grep -q 'resource-id="demo_phone"'
}

# step_reaches <n> - waits up to 5 s for "Step <n> of 7": a pass plays its effect for 900 ms first.
step_reaches() {
  waited=0
  while [ "${waited}" -lt 5 ]; do
    if ui_nodes | grep -m1 'resource-id="demo_step"' | grep -q "text=\"[^\"]* $1 of [0-9]*\""; then
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  return 1
}

echo >&2
echo "== the demo opens from home ==" >&2
open_screen row_demo
shown=''
waited=0
while [ "${waited}" -lt 5 ]; do
  if phone_shown; then
    shown='1'
    break
  fi
  sleep 1
  waited=$((waited + 1))
done
if [ -n "${shown}" ]; then pass "the Try the gestures row opens the demo"; else fail "the Try the gestures row opens the demo"; fi
# The screen takes no practice touch until it has settled.
sleep 1

bounds="$(bounds_of_id demo_phone)"
density="$("${ADB_BIN}" shell wm density | tr -d '\r' | awk -F': *' '{print $2}' | tail -1)"
if [ -z "${bounds}" ] || [ -z "${density}" ]; then
  fail "the drawn phone has bounds (bounds '${bounds}', density '${density}')"
  report "DEMO"
  exit 1
fi
# shellcheck disable=SC2086 # the four bounds are meant to split
set -- ${bounds}
left="$1" top="$2" right="$3" bottom="$4"
dp() {
  echo $(($1 * density / 160))
}
echo "   drawn phone ${left},${top} to ${right},${bottom} at density ${density}" >&2

echo >&2
echo "== a practice Back passes step 1 and stays in the demo ==" >&2
# 60 dp in from 10 dp inside the left drawn edge: past the 24 dp trigger, short of the long line.
mid_y=$(((top + bottom) / 2))
back_x=$((left + $(dp 10)))
swipe "${back_x}" "${mid_y}" $((back_x + $(dp 60))) "${mid_y}" 120
if step_reaches 2; then pass "the practice Back moves to step 2"; else fail "the practice Back moves to step 2"; fi
if phone_shown; then pass "the practice Back ran no real Back"; else fail "the practice Back ran no real Back"; fi

echo >&2
echo "== a practice Home passes step 2 and stays in the demo ==" >&2
centre_x=$(((left + right) / 2))
home_y=$((bottom - $(dp 10)))
swipe "${centre_x}" "${home_y}" "${centre_x}" $((home_y - $(dp 150))) 120
if step_reaches 3; then pass "the practice Home moves to step 3"; else fail "the practice Home moves to step 3"; fi
if phone_shown; then pass "the practice Home ran no real Home"; else fail "the practice Home ran no real Home"; fi

echo >&2
echo "== Skip closes the demo ==" >&2
tap_node "$(ui_nodes | grep -m1 'resource-id="demo_skip"' || true)"
sleep 2
if ! phone_shown && find_id row_demo >/dev/null; then
  pass "Skip returns to home"
else
  fail "Skip returns to home"
fi

report "DEMO"
