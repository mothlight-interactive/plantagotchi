package dev.mothlight.plantagotchi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.about_interactive
import dev.mothlight.plantagotchi.resources.about_particles
import dev.mothlight.plantagotchi.resources.about_presents
import dev.mothlight.plantagotchi.resources.about_tap
import dev.mothlight.plantagotchi.resources.about_version
import dev.mothlight.plantagotchi.resources.about_weather
import dev.mothlight.plantagotchi.resources.app_name
import dev.mothlight.plantagotchi.resources.p0
import dev.mothlight.plantagotchi.resources.p10
import dev.mothlight.plantagotchi.resources.p100
import dev.mothlight.plantagotchi.resources.p20
import dev.mothlight.plantagotchi.resources.p30
import dev.mothlight.plantagotchi.resources.p40
import dev.mothlight.plantagotchi.resources.p50
import dev.mothlight.plantagotchi.resources.p60
import dev.mothlight.plantagotchi.resources.p70
import dev.mothlight.plantagotchi.resources.p80
import dev.mothlight.plantagotchi.resources.p90
import dev.mothlight.plantagotchi.ui.game.MothlightLogo
import dev.mothlight.plantagotchi.ui.game.drawBitmap
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.sin
import kotlin.random.Random

const val APP_VERSION = "2.1.0"

/** Night sky with the animated pixel logo. */
@Composable
private fun LogoCanvas(modifier: Modifier = Modifier) {
    val frame = remember { mutableLongStateOf(0L) }
    val start = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos {
            if (start.longValue == 0L) start.longValue = it
            frame.longValue = it
        }
    }
    Canvas(modifier) {
        val time = (frame.longValue - start.longValue) / 1e9f
        val random = Random(3)
        repeat(60) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height
            val twinkle = 0.3f + 0.7f * (0.5f + 0.5f * sin(time * (0.8f + random.nextFloat() * 2f) + it))
            val s = if (it % 7 == 0) 3.dp.toPx() else 2.dp.toPx()
            drawRect(MothlightLogo.ink, Offset(x, y), Size(s, s), alpha = 0.5f * twinkle)
        }
        val pixel = (size.width * 0.82f / MothlightLogo.width).let { kotlin.math.floor(it) }.coerceAtLeast(1f)
        val logoW = MothlightLogo.width * pixel
        val logoH = MothlightLogo.height * pixel
        MothlightLogo.draw(this, Offset((size.width - logoW) / 2, (size.height - logoH) / 2), pixel, time)
    }
}

/** Shown while the game loads. [progress] 0..1 drives the pixel loading bar. */
@Composable
fun SplashScreen(progress: Float) {
    val frames = listOf(
        Res.drawable.p0, Res.drawable.p10, Res.drawable.p20, Res.drawable.p30, Res.drawable.p40, Res.drawable.p50,
        Res.drawable.p60, Res.drawable.p70, Res.drawable.p80, Res.drawable.p90, Res.drawable.p100,
    ).map { imageResource(it) }

    Column(
        Modifier.fillMaxSize().background(MothlightLogo.background).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LogoCanvas(Modifier.fillMaxWidth().height(160.dp))
        Text(
            stringResource(Res.string.about_interactive),
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 6.sp),
            color = MothlightLogo.lamp,
        )
        Spacer(Modifier.height(36.dp))
        Text(stringResource(Res.string.about_presents), style = MaterialTheme.typography.bodySmall, color = MothlightLogo.ink.copy(alpha = 0.6f))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MothlightLogo.ink)
        Spacer(Modifier.height(28.dp))
        Canvas(Modifier.fillMaxWidth(0.6f).height(36.dp)) {
            val index = (progress.coerceIn(0f, 1f) * 10).toInt()
            val img = frames[index]
            val h = size.width * img.height / img.width
            drawBitmap(img, Rect(0f, (size.height - h) / 2, size.width, (size.height + h) / 2))
        }
    }
}

/** About screen: the Mothlight logo and credits. */
@Composable
fun MothlightAboutScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(MothlightLogo.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LogoCanvas(Modifier.fillMaxWidth().height(180.dp))
            Text(
                stringResource(Res.string.about_interactive),
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 6.sp),
                color = MothlightLogo.lamp,
            )
            Spacer(Modifier.height(48.dp))
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MothlightLogo.ink)
            Text(
                stringResource(Res.string.about_version, APP_VERSION),
                style = MaterialTheme.typography.bodySmall,
                color = MothlightLogo.ink.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(32.dp))
            listOf(Res.string.about_weather, Res.string.about_particles).forEach {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MothlightLogo.ink.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(40.dp))
            Text(stringResource(Res.string.about_tap), style = MaterialTheme.typography.labelSmall, color = Color(0x99EDE6D6))
        }
    }
}
