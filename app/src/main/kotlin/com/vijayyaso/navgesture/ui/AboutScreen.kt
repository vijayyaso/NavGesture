// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
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

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.vijayyaso.navgesture.BuildConfig
import com.vijayyaso.navgesture.R

@Composable
internal fun AboutScreen(model: AppModel) {
    val context = LocalContext.current
    val sourceUrl = stringResource(R.string.url_source)
    val licenseUrl = stringResource(R.string.url_license)
    SettingsGroup {
        ValueRow(
            stringResource(R.string.about_version),
            model.diagnostics.appVersionName ?: stringResource(R.string.diag_unknown),
        )
        // Plain text until the site is live, so there is nothing to open yet.
        ValueRow(stringResource(R.string.about_website), stringResource(R.string.value_website))
        // A device with no browser throws ActivityNotFoundException, which startFirst absorbs.
        NavigationRow(
            title = stringResource(R.string.about_source),
            supporting = stringResource(R.string.value_source),
        ) { context.startFirst(Intent(Intent.ACTION_VIEW, sourceUrl.toUri())) }
        NavigationRow(
            title = stringResource(R.string.diag_license),
            supporting = stringResource(R.string.value_license),
        ) { context.startFirst(Intent(Intent.ACTION_VIEW, licenseUrl.toUri())) }
    }
    HelpGroup(model.diagnostics.appVersionName ?: BuildConfig.VERSION_NAME)
    SettingsGroup(title = stringResource(R.string.section_credits)) {
        InfoRow(
            title = stringResource(R.string.credit_victor),
            supporting = stringResource(R.string.credit_victor_body),
        )
        InfoRow(
            title = stringResource(R.string.credit_cts),
            supporting = stringResource(R.string.credit_cts_body),
        )
    }
}

/** Ways to reach the developer. Each opens another app; startFirst absorbs a phone without one. */
@Composable
private fun HelpGroup(version: String) {
    val context = LocalContext.current
    val bugUrl = stringResource(R.string.url_report_bug)
    val featureUrl = stringResource(R.string.url_request_feature)
    val address = stringResource(R.string.value_email)
    val subject = stringResource(R.string.email_feedback_subject, version)
    SettingsGroup(title = stringResource(R.string.section_help_feedback)) {
        NavigationRow(
            title = stringResource(R.string.about_report_bug),
            supporting = stringResource(R.string.about_report_bug_body),
            tag = TestTags.ROW_REPORT_BUG,
        ) { context.startFirst(Intent(Intent.ACTION_VIEW, bugUrl.toUri())) }
        NavigationRow(
            title = stringResource(R.string.about_request_feature),
            supporting = stringResource(R.string.about_request_feature_body),
            tag = TestTags.ROW_REQUEST_FEATURE,
        ) { context.startFirst(Intent(Intent.ACTION_VIEW, featureUrl.toUri())) }
        NavigationRow(
            title = stringResource(R.string.about_email),
            supporting = stringResource(R.string.about_email_body),
            tag = TestTags.ROW_EMAIL_DEVELOPER,
        ) { context.startFirst(feedbackEmail(address, subject)) }
    }
}

/** A new email with the address and subject filled in; SENDTO offers only email apps. */
private fun feedbackEmail(address: String, subject: String): Intent =
    Intent(Intent.ACTION_SENDTO, "mailto:$address?subject=${Uri.encode(subject)}".toUri())
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
