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

package com.vijayyaso.navgesture.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vijayyaso.navgesture.R

/**
 * Google Play's prominent disclosure for an Accessibility service that is not an accessibility
 * tool: what the service reads, what it does with it and what it never does, with an explicit
 * Agree. It covers the Accessibility service alone, since Play forbids bundling it with other
 * data disclosures. The text is mirrored in docs/play-store/disclosure.md.
 */
@Composable
internal fun DisclosureScreen(onAgree: () -> Unit, onNotNow: () -> Unit) {
    Text(
        text = stringResource(R.string.disclosure_intro),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    SettingsGroup {
        Part(R.string.disclosure_reads_title, R.string.disclosure_reads_body)
        Part(R.string.disclosure_does_title, R.string.disclosure_does_body)
        Part(R.string.disclosure_never_title, R.string.disclosure_never_body)
    }
    // Full width and stacked, so a large font never squeezes either button.
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    ) {
        Button(
            onClick = onAgree,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.DISCLOSURE_AGREE),
        ) {
            Text(stringResource(R.string.disclosure_agree))
        }
        OutlinedButton(
            onClick = onNotNow,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TestTags.DISCLOSURE_NOT_NOW),
        ) {
            Text(stringResource(R.string.disclosure_not_now))
        }
    }
    FootNote(stringResource(R.string.disclosure_footnote), Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun Part(@StringRes title: Int, @StringRes body: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(16.dp),
    ) {
        Text(text = stringResource(title), style = MaterialTheme.typography.titleMedium)
        SupportingText(stringResource(body))
    }
}
