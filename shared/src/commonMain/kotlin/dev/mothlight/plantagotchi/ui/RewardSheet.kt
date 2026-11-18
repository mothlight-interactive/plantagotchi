package dev.mothlight.plantagotchi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import dev.mothlight.plantagotchi.data.state.DailyReward
import dev.mothlight.plantagotchi.data.state.DayMark
import dev.mothlight.plantagotchi.data.state.PlayerState
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.day_fri
import dev.mothlight.plantagotchi.resources.day_mon
import dev.mothlight.plantagotchi.resources.day_sat
import dev.mothlight.plantagotchi.resources.day_sun
import dev.mothlight.plantagotchi.resources.day_thu
import dev.mothlight.plantagotchi.resources.day_tue
import dev.mothlight.plantagotchi.resources.day_wed
import dev.mothlight.plantagotchi.resources.reward_claim
import dev.mothlight.plantagotchi.resources.reward_claimed
import dev.mothlight.plantagotchi.resources.reward_streak
import dev.mothlight.plantagotchi.resources.reward_title
import dev.mothlight.plantagotchi.resources.reward_today
import dev.mothlight.plantagotchi.ui.theme.PlantagotchiColors
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The daily chest: last 7 days (collected, missed, today), the streak and the claim button. */
@Composable
fun RewardSheet(player: PlayerState, today: Long, onClaim: () -> Unit) {
    val available = DailyReward.isAvailable(player, today)
    val streak = DailyReward.currentStreak(player, today)
    val days = DailyReward.recentDays(player, today)

    Column(Modifier.fillMaxWidth().background(Panel).navigationBarsPadding().padding(16.dp)) {
        Text(
            stringResource(Res.string.reward_title),
            style = MaterialTheme.typography.titleLarge,
            color = PlantagotchiColors.Sunshine,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            pluralStringResource(Res.plurals.reward_streak, streak, streak),
            style = MaterialTheme.typography.titleMedium,
            color = PlantagotchiColors.Fertilizer,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        weekday(LocalDate.fromEpochDays(day.day.toInt()).dayOfWeek),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.day == today) PlantagotchiColors.Sunshine else PlantagotchiColors.OffWhite,
                    )
                    Spacer(Modifier.height(4.dp))
                    DayBox(day.mark, isToday = day.day == today)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (available) {
            val next = DailyReward.amount(DailyReward.nextStreak(player, today))
            Text(stringResource(Res.string.reward_today, next), style = MaterialTheme.typography.bodyMedium, color = PlantagotchiColors.OffWhite)
            Spacer(Modifier.height(8.dp))
            PixelButton(stringResource(Res.string.reward_claim), showCoin = true, color = PlantagotchiColors.Sunshine, onClick = onClaim)
        } else {
            val tomorrow = DailyReward.amount(player.streak + 1)
            Text(stringResource(Res.string.reward_claimed, tomorrow), style = MaterialTheme.typography.bodyMedium, color = PlantagotchiColors.OffWhite)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun weekday(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> Res.string.day_mon
        DayOfWeek.TUESDAY -> Res.string.day_tue
        DayOfWeek.WEDNESDAY -> Res.string.day_wed
        DayOfWeek.THURSDAY -> Res.string.day_thu
        DayOfWeek.FRIDAY -> Res.string.day_fri
        DayOfWeek.SATURDAY -> Res.string.day_sat
        DayOfWeek.SUNDAY -> Res.string.day_sun
    }
)

private val Claimed = Color(0xFF71AA34)
private val Missed = Color(0xFFE06464)

@Composable
private fun DayBox(mark: DayMark, isToday: Boolean) {
    val background = when (mark) {
        DayMark.CLAIMED -> Color(0xFF2F4A2A)
        DayMark.MISSED -> Color(0xFF4A2A2F)
        DayMark.OPEN, DayMark.NONE -> PanelLight
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(background)
            .border(2.dp, if (isToday) PlantagotchiColors.Sunshine else Outline),
    ) {
        when (mark) {
            DayMark.CLAIMED -> pixels(CHECK, Claimed)
            DayMark.MISSED -> pixels(CROSS, Missed)
            DayMark.OPEN -> pixels(QUESTION, PlantagotchiColors.Sunshine)
            DayMark.NONE -> Unit
        }
    }
}

private val CHECK = listOf(
    "......",
    ".....x",
    "....xx",
    "x..xx.",
    "xxxx..",
    ".xx...",
)
private val CROSS = listOf(
    "x....x",
    "xx..xx",
    ".xxxx.",
    ".xxxx.",
    "xx..xx",
    "x....x",
)
private val QUESTION = listOf(
    ".xxxx.",
    "xx..xx",
    "...xx.",
    "..xx..",
    "......",
    "..xx..",
)

/** Draws a small pixel pattern centered in half of the box. */
private fun DrawScope.pixels(rows: List<String>, color: Color) {
    val cell = size.minDimension * 0.5f / rows.size
    val origin = Offset((size.width - cell * rows[0].length) / 2, (size.height - cell * rows.size) / 2)
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, c ->
            if (c == 'x') drawRect(color, origin + Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
