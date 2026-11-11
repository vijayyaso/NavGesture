// Copyright (C) 2026  Vijayyaso
//
// This file is part of HyperGesture.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

package com.vijayyaso.navgesture.fixture.shortcuts;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * Publishes run-time shortcuts on every start, then reports the intent it was started with:
 * one FIXTURE_LAUNCH line and one FIXTURE_EXTRA line per extra, with the extra's Java type,
 * so the e2e suite can check that NavGesture rebuilt the intent with the right types.
 */
public class LauncherActivity extends Activity {

    private static final String TAG = "NGFixture";

    /** The key Flutter's quick_actions plugin uses, as in GPay "Scan any QR code". */
    static final String FLUTTER_KEY = "some unique action key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        publish();
        report(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        report(intent);
    }

    private void publish() {
        ShortcutManager manager = getSystemService(ShortcutManager.class);
        List<ShortcutInfo> shortcuts = new ArrayList<>();

        // Like GPay: action RUN to the launcher activity, one string extra whose key has spaces.
        Intent qr = new Intent(this, LauncherActivity.class)
                .setAction("RUN")
                .putExtra(FLUTTER_KEY, "QR_SCANNER")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        shortcuts.add(new ShortcutInfo.Builder(this, "qr_scanner")
                .setShortLabel("Scan QR")
                .setIntent(qr)
                .setRank(0)
                .build());

        // Every extra type the parser rebuilds, plus string values with ", " and "=" in them.
        Intent typed = new Intent(this, LauncherActivity.class)
                .setAction("RUN")
                .putExtra("count", 3)
                .putExtra("big", 9999999999L)
                .putExtra("on", true)
                .putExtra("note", "a, b")
                .putExtra("pair", "x=y");
        shortcuts.add(new ShortcutInfo.Builder(this, "typed")
                .setShortLabel("Typed extras")
                .setIntent(typed)
                .setRank(1)
                .build());

        // A data URI: the dump hides it, so NavGesture must leave this one out.
        Intent withData = new Intent(this, LauncherActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("fixture://open/item?id=7"));
        shortcuts.add(new ShortcutInfo.Builder(this, "with_data")
                .setShortLabel("With data")
                .setIntent(withData)
                .setRank(2)
                .build());

        // Aimed at an activity that is not exported: NavGesture must leave this one out too.
        Intent hidden = new Intent(this, PrivateActivity.class).setAction("RUN");
        shortcuts.add(new ShortcutInfo.Builder(this, "private")
                .setShortLabel("Private")
                .setIntent(hidden)
                .setRank(3)
                .build());

        boolean done = manager.setDynamicShortcuts(shortcuts);
        Log.i(TAG, "FIXTURE_PUBLISHED " + done + " " + shortcuts.size());
    }

    private void report(Intent intent) {
        StringBuilder text = new StringBuilder();
        String line = "FIXTURE_LAUNCH action=" + intent.getAction();
        Log.i(TAG, line);
        text.append(line).append('\n');
        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String key : new TreeSet<>(extras.keySet())) {
                Object value = extras.get(key);
                String type = value == null ? "null" : value.getClass().getSimpleName();
                String extra = "FIXTURE_EXTRA " + key + "|" + type + "|"
                        + (value instanceof Object[] ? Arrays.toString((Object[]) value) : value);
                Log.i(TAG, extra);
                text.append(extra).append('\n');
            }
        }
        TextView view = new TextView(this);
        view.setText(text);
        view.setPadding(48, 160, 48, 48);
        setContentView(view);
    }
}
