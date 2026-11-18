package dev.mothlight.plantagotchi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mothlight.plantagotchi.data.AppSettings
import dev.mothlight.plantagotchi.data.repository.LocalGameStateRepository
import dev.mothlight.plantagotchi.data.weather.OpenMeteoClient
import dev.mothlight.plantagotchi.model.PlantagotchiViewModel
import dev.mothlight.plantagotchi.platform.PlatformServices
import dev.mothlight.plantagotchi.audio.AudioAssets
import dev.mothlight.plantagotchi.dev.AppEnv
import dev.mothlight.plantagotchi.dev.DevStore
import dev.mothlight.plantagotchi.resources.Res
import dev.mothlight.plantagotchi.ui.GameScreen
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

@Composable
fun App(platform: PlatformServices, env: AppEnv = AppEnv.PROD) {
    val viewModel = viewModel { createViewModel(platform, env) }

    LaunchedEffect(platform) {
        val sounds = AudioAssets.all.mapNotNull { asset ->
            runCatching { asset to Res.readBytes("files/$asset.wav") }.getOrNull()
        }.toMap()
        runCatching { platform.sound.load(sounds) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }

    GameScreen(viewModel)
}

private fun createViewModel(platform: PlatformServices, env: AppEnv): PlantagotchiViewModel {
    val http = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    return PlantagotchiViewModel(
        platform = platform,
        repository = LocalGameStateRepository(),
        weatherClient = OpenMeteoClient(http),
        appSettings = AppSettings(),
        env = env,
        devStore = if (env.testModeAvailable) DevStore() else null,
    )
}
