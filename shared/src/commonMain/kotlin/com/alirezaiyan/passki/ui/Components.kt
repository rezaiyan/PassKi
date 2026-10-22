package com.alirezaiyan.passki.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alirezaiyan.passki.Activity
import com.alirezaiyan.passki.DoctorStatus
import com.alirezaiyan.passki.Outcome

/** Visual status shared by activities and checks. */
enum class Tone { Pass, Warn, Fail, Skip, Busy }

fun Outcome.tone(): Tone = when (this) {
    Outcome.Ok -> Tone.Pass
    Outcome.Failed -> Tone.Fail
    Outcome.Info -> Tone.Skip
    Outcome.Running -> Tone.Busy
}

fun DoctorStatus.tone(): Tone = when (this) {
    DoctorStatus.Pass -> Tone.Pass
    DoctorStatus.Warn -> Tone.Warn
    DoctorStatus.Fail -> Tone.Fail
    DoctorStatus.Skipped -> Tone.Skip
}

@Composable
private fun Tone.colors(): Pair<Color, Color> {
    val s = LocalStatusColors.current
    return when (this) {
        Tone.Pass -> s.pass to s.passBg
        Tone.Warn -> s.warn to s.warnBg
        Tone.Fail -> s.fail to s.failBg
        Tone.Skip -> s.skip to s.skipBg
        Tone.Busy -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
    }
}

/** Round status mark: tick, cross, bang, dash or a spinner. */
@Composable
fun StatusMark(tone: Tone, size: Int = 26) {
    val (fg, bg) = tone.colors()
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        when (tone) {
            Tone.Busy -> CircularProgressIndicator(Modifier.size((size * 0.55).dp), color = fg, strokeWidth = 2.dp)
            else -> Icon(
                when (tone) {
                    Tone.Pass -> PassKiIcons.Check
                    Tone.Fail -> PassKiIcons.Cross
                    Tone.Warn -> PassKiIcons.Warn
                    else -> PassKiIcons.Skip
                },
                contentDescription = tone.name,
                tint = fg,
                modifier = Modifier.size((size * 0.55).dp),
            )
        }
    }
}

/** Catalog ID such as `AND-01`, set in mono on a chip. */
@Composable
fun IdChip(id: String) {
    Text(
        id,
        fontFamily = Mono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** Outlined surface that groups rows; rows inside get dividers from [Rows]. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

/** Rows separated by hairlines. */
@Composable
fun <T> Rows(items: List<T>, row: @Composable (T) -> Unit) {
    items.forEachIndexed { i, item ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        row(item)
    }
}

/** Small heading above a group of panels. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Screen title in the display face, with an optional line under it and an action on the right. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        trailing?.invoke(this)
    }
}

/**
 * A tappable flow: title, what it does, and the outcome of its last run underneath once there is one.
 */
@Composable
fun ActionRow(title: String, description: String, last: Activity?, busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !busy, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            when {
                busy -> StatusMark(Tone.Busy, 24)
                last != null && last.outcome != Outcome.Running -> StatusMark(last.outcome.tone(), 24)
                else -> Icon(PassKiIcons.Chevron, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
            }
        }
        if (last != null && last.outcome != Outcome.Running && !busy) {
            Text(
                last.title,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = Mono,
                color = if (last.outcome == Outcome.Failed) LocalStatusColors.current.fail else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** One entry in the activity timeline; tap to see the full log entry. */
@Composable
fun ActivityItem(activity: Activity) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = activity.detail.isNotBlank() || activity.fixHint != null) { open = !open }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusMark(activity.outcome.tone(), 22)
        Column(Modifier.weight(1f)) {
            Text(activity.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                activity.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (open) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            activity.fixHint?.let { FixHint(it, Modifier.padding(top = 8.dp)) }
            if (open && activity.detail.isNotBlank()) {
                SelectionContainer {
                    Text(
                        activity.detail,
                        fontFamily = Mono,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp),
                    )
                }
            }
        }
    }
}

/** "Fix:" callout, as PasskeyError.Misconfigured and the doctor hand it over. */
@Composable
fun FixHint(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .padding(10.dp),
    ) {
        Text("Fix  ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Label on the left, value on the right; [mono] for identifiers. */
@Composable
fun InfoRow(label: String, value: String, mono: Boolean = false, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(128.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) Mono else null,
            fontSize = if (mono) 13.sp else MaterialTheme.typography.bodyMedium.fontSize,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Rounded icon tile used in headers and empty states. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, background: Color, size: Int = 44) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3).dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.5).dp))
    }
}
