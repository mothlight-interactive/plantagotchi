package dev.mothlight.plantagotchi

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mothlight.plantagotchi.data.repository.LocalGameStateRepository
import dev.mothlight.plantagotchi.data.repository.SupabaseGameStateRepository
import dev.mothlight.plantagotchi.data.repository.SyncingGameStateRepository
import dev.mothlight.plantagotchi.data.weather.OpenMeteoClient
import dev.mothlight.plantagotchi.model.PlantagotchiViewModel
import dev.mothlight.plantagotchi.platform.PlatformServices
import dev.mothlight.plantagotchi.ui.GameScreen
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

@Composable
fun App(platform: PlatformServices) {
    val viewModel = viewModel { createViewModel(platform) }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }

    GameScreen(viewModel)
}

private fun createViewModel(platform: PlatformServices): PlantagotchiViewModel {
    val http = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    val remote = if (AppConfig.SUPABASE_URL.isNotBlank() && AppConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
        SupabaseGameStateRepository(
            createSupabaseClient(AppConfig.SUPABASE_URL, AppConfig.SUPABASE_PUBLISHABLE_KEY) {
                install(Auth)
                install(Postgrest)
            }
        )
    } else {
        null
    }
    return PlantagotchiViewModel(
        platform = platform,
        repository = SyncingGameStateRepository(LocalGameStateRepository(), remote),
        weatherClient = OpenMeteoClient(http),
    )
}
