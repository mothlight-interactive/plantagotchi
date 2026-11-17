package dev.mothlight.plantagotchi.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.resources.ic_bg_about
import dev.mothlight.plantagotchi.resources.ic_ground
import dev.mothlight.plantagotchi.resources.ic_teamrocket
import dev.mothlight.plantagotchi.ui.theme.PlantagotchiColors
import org.jetbrains.compose.resources.painterResource

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        Image(
            painter = painterResource(Res.drawable.ic_bg_about),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier.fillMaxWidth().padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AboutText("Planta\nGotchi", MaterialTheme.typography.displayLarge)
            AboutText("by", MaterialTheme.typography.displaySmall, Modifier.padding(top = 25.dp))
            AboutText("Team\nRocket", MaterialTheme.typography.displayMedium, Modifier.padding(top = 25.dp))
        }
        Column(Modifier.align(Alignment.BottomCenter)) {
            Image(
                painter = painterResource(Res.drawable.ic_teamrocket),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(64.dp).fillMaxWidth().padding(start = 40.dp),
            )
            Image(
                painter = painterResource(Res.drawable.ic_ground),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxWidth().height(128.dp),
            )
        }
    }
}

@Composable
private fun AboutText(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = style,
        textAlign = TextAlign.Center,
        color = PlantagotchiColors.OffWhite,
        modifier = modifier.fillMaxWidth(),
    )
}
