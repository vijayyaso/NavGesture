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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vijayyaso.navgesture.R
import com.vijayyaso.navgesture.ui.theme.onSuccessContainer
import com.vijayyaso.navgesture.ui.theme.onWarningContainer
import com.vijayyaso.navgesture.ui.theme.successContainer
import com.vijayyaso.navgesture.ui.theme.warningContainer
import kotlin.math.roundToInt

internal fun Modifier.tagged(tag: String?): Modifier = if (tag != null) testTag(tag) else this

/** Corners of a group card and of a home tile. */
internal val CardShape = RoundedCornerShape(16.dp)
internal val TileShape = RoundedCornerShape(20.dp)

/** A group's title: small grey text, lined up with the text inside the card below it. */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
internal fun FootNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Rows grouped in one white rounded card, with an optional grey title above it and an optional
 * grey footer under it. A thin line separates each child from the next, so a block made of
 * several composables that belong together must be passed as one child (wrap it in a Column).
 */
@Composable
internal fun SettingsGroup(
    title: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) SectionHeader(title) else Spacer(Modifier.size(12.dp))
        DividedCard(content)
        footer?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
            )
        }
    }
}

/** Stacks its children like a Column and draws a hairline between each child and the next. */
@Composable
private fun DividedCard(content: @Composable () -> Unit) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    // Written at placement, read at draw, so a change in the rows redraws the lines.
    var edges by remember { mutableStateOf(IntArray(0)) }
    Layout(
        content = content,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .drawWithContent {
                drawContent()
                val inset = 16.dp.toPx()
                val stroke = 0.5.dp.toPx().coerceAtLeast(1f)
                edges.forEach { y ->
                    val line = y - stroke / 2
                    drawLine(lineColor, Offset(inset, line), Offset(size.width, line), stroke)
                }
            },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val height = placeables.sumOf { it.height }
        layout(constraints.maxWidth, height) {
            var y = 0
            val found = ArrayList<Int>(placeables.size)
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height
                if (index < placeables.lastIndex && placeable.height > 0) found += y
            }
            val updated = found.toIntArray()
            if (!updated.contentEquals(edges)) edges = updated
        }
    }
}

/** A glyph and the colour of the square it sits on. */
internal data class GroupIcon(val icon: ImageVector, val tint: Color)

/** A small rounded square in the icon's colour with the glyph in white. */
@Composable
internal fun IconBadge(icon: GroupIcon, size: Dp = 30.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(icon.tint, RoundedCornerShape(size * 0.27f)),
    ) {
        Icon(icon.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

/** A home tile's own on/off switch. */
internal data class TileToggle(val checked: Boolean, val tag: String, val onChange: (Boolean) -> Unit)

/** One tile on home: a screen to open, with its state and, for a feature, its switch. */
internal data class HomeTile(
    val title: String,
    val summary: String,
    val icon: GroupIcon,
    val tag: String,
    val toggle: TileToggle? = null,
    val onOpen: () -> Unit,
)

/**
 * The switch sits on top of the tile as a sibling of the clickable area, not inside it, so the
 * two stay separate touch targets and separate accessibility nodes, and the tile's centre opens
 * the screen.
 */
@Composable
internal fun FeatureTile(tile: HomeTile, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = 128.dp)
                .testTag(tile.tag)
                .clickable(role = Role.Button, onClick = tile.onOpen)
                .padding(16.dp),
        ) {
            IconBadge(tile.icon, size = 36.dp)
            Spacer(Modifier.size(8.dp))
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = tile.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        tile.toggle?.let { toggle ->
            Switch(
                checked = toggle.checked,
                onCheckedChange = toggle.onChange,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 12.dp)
                    .testTag(toggle.tag),
            )
        }
    }
}

/** Green for done, amber for something to do, neutral for a fact or a wait. */
internal enum class Tone { GOOD, ATTENTION, NEUTRAL }

@Composable
internal fun StatusChip(label: String, tone: Tone, modifier: Modifier = Modifier) {
    val (background, foreground) = toneColors(tone)
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = foreground,
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
internal fun toneColors(tone: Tone): Pair<Color, Color> = when (tone) {
    Tone.GOOD -> successContainer to onSuccessContainer
    Tone.ATTENTION -> warningContainer to onWarningContainer
    Tone.NEUTRAL ->
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun SupportingText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

private val transparentItem
    @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    supporting: String? = null,
    tag: String? = null,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { SupportingText(it) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.tagged(tag))
        },
        colors = transparentItem,
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
internal fun NavigationRow(
    title: String,
    supporting: String? = null,
    tag: String? = null,
    icon: GroupIcon? = null,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = icon?.let { { IconBadge(it) } },
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { SupportingText(it) } },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        },
        colors = transparentItem,
        modifier = Modifier
            .tagged(tag)
            .clickable(onClick = onClick),
    )
}

/** A row with an optional trailing element and actions laid under its text. */
@Composable
internal fun InfoRow(
    title: String,
    supporting: String? = null,
    supportingTag: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    // A large font leaves the title a word per line beside a chip, so it is drawn below the title.
    val stacked = trailing != null && LocalDensity.current.fontScale >= STACKED_FONT_SCALE
    val under = trailing.takeIf { stacked }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (supporting == null && actions == null && under == null) {
            null
        } else {
            {
                Column {
                    under?.let { Box(Modifier.padding(vertical = 4.dp)) { it() } }
                    supporting?.let { SupportingText(it, Modifier.tagged(supportingTag)) }
                    actions?.let {
                        // Wraps onto a second line rather than squeezing a button at a large font.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp),
                        ) { it() }
                    }
                }
            }
        },
        trailingContent = trailing.takeUnless { stacked },
        colors = transparentItem,
    )
}

/** From this font scale on, side-by-side layouts stack: chips go under titles, tiles one per row. */
internal const val STACKED_FONT_SCALE = 1.5f

/** Label and value stacked once a large font leaves no room beside each other. */
@Composable
internal fun ValueRow(label: String, value: String, valueTag: String? = null) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.tagged(valueTag),
            )
        },
        colors = transparentItem,
    )
}

@Composable
internal fun ThresholdSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    formatter: @Composable (Int) -> String,
    supporting: String? = null,
    onCommit: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { if (!dragging) local = value }

    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            )
            Text(
                text = formatter(local.roundToInt()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Slider(
            value = local,
            valueRange = range,
            onValueChange = {
                dragging = true
                local = it
            },
            onValueChangeFinished = {
                dragging = false
                onCommit(local)
            },
        )
    }
}

@Composable
internal fun LastProblem(failure: String?) {
    ValueRow(
        label = stringResource(R.string.label_last_problem),
        value = failure ?: stringResource(R.string.diag_none),
    )
}
