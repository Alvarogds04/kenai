package com.kenai.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kenai.android.character.BotState
import com.kenai.android.character.KodaRole
import com.kenai.android.character.KodaView
import com.kenai.android.core.HomePanel
import com.kenai.android.core.IslandSurface
import com.kenai.android.core.TypeScale
import com.kenai.android.core.Words

/**
 * The strip at the top of Inicio (kenai-0.1 S9), drawn like the island: always dark, a 1 dp ring in the role colour (sun while
 * something waits, go while calm, red while the computer is away), a small Koda in the matching pose, a title, a subtitle and a chip
 * with the count. It replaces the hero's old link row. TalkBack hears it as one polite live region, so a change is announced once.
 * After a decision (T14) it says what the computer got: live with a spinner while sending, go once allowed, faint when denied or
 * expired, red when it could not be sent.
 */
@Composable
fun StatusStrip(strip: HomePanel.Strip, modifier: Modifier = Modifier) {
    val ring = Color(
        when (strip.tone) {
            HomePanel.Tone.WAITING -> KodaRole.SUN
            HomePanel.Tone.CALM -> KodaRole.GO
            HomePanel.Tone.OFFLINE -> KodaRole.RED
            HomePanel.Tone.SENDING -> KodaRole.LIVE
            HomePanel.Tone.DONE -> KodaRole.GO
            HomePanel.Tone.FAINT -> KodaRole.FAINT
        },
    )
    val pose = when (strip.tone) {
        HomePanel.Tone.WAITING -> BotState.APPROVAL
        HomePanel.Tone.CALM -> BotState.IDLE
        HomePanel.Tone.OFFLINE -> BotState.ERROR
        HomePanel.Tone.SENDING -> BotState.APPROVAL
        HomePanel.Tone.DONE -> BotState.FINISHED
        HomePanel.Tone.FAINT -> BotState.IDLE
    }
    val white = Color(IslandSurface.TEXT)
    val dim = Color(IslandSurface.TEXT_DIM)
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CircleShape).background(Color(IslandSurface.PANEL)).border(1.dp, ring, CircleShape)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        KodaView(pose, Modifier.size(40.dp))
        Column(Modifier.weight(1f)) {
            Text(strip.title, style = TypeScale.BODY.style(white).copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (strip.subtitle.isNotBlank()) Text(strip.subtitle, style = TypeScale.SECONDARY.style(dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Sending: the computer has not confirmed yet (the title says so; the spinner is decoration).
        if (strip.spinner) CircularProgressIndicator(Modifier.size(20.dp), color = ring, strokeWidth = 2.dp, trackColor = Color.Transparent)
        if (strip.count > 0) {
            val w = Words.current()
            when (strip.tone) {
                // What waits: a solid sun chip with the number; TalkBack hears "2 are waiting for you".
                HomePanel.Tone.WAITING -> Row(
                    Modifier.heightIn(min = 28.dp).widthIn(min = 28.dp).clip(CircleShape).background(ring)
                        .clearAndSetSemantics { contentDescription = w.waitingForYouCount(strip.count) }.padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${strip.count}", style = TypeScale.LABEL.style(Color(KodaRole.ON_ROLE)).copy(fontWeight = FontWeight.Bold), maxLines = 1)
                }
                // Calm: what runs, in the live role colour.
                else -> Chip(w.runningCount(strip.count), tint = Color(KodaRole.LIVE))
            }
        }
    }
}
