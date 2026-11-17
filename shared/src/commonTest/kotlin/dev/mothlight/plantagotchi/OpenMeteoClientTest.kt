package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.data.GeoPosition
import dev.mothlight.plantagotchi.data.weather.OpenMeteoClient
import dev.mothlight.plantagotchi.data.weather.WeatherState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenMeteoClientTest {

    private val body = """
        {"latitude":47.48,"longitude":8.21,"utc_offset_seconds":3600,"timezone":"Europe/Zurich",
         "current":{"time":"2026-11-16T14:15","interval":900,"temperature_2m":6.3,"apparent_temperature":3.1,
           "relative_humidity_2m":81,"precipitation":0.4,"weather_code":63,"cloud_cover":100,
           "pressure_msl":1012.4,"wind_speed_10m":11.2,"is_day":1},
         "daily":{"time":["2026-11-16"],"sunrise":["2026-11-16T07:31"],"sunset":["2026-11-16T16:52"],
           "temperature_2m_max":[7.9],"temperature_2m_min":[2.2],"uv_index_max":[0.9]}}
    """.trimIndent()

    @Test
    fun parsesForecast() = runTest {
        var requestedUrl = ""
        val http = HttpClient(MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val weather = OpenMeteoClient(http).currentWeather(GeoPosition(47.48, 8.21))

        assertTrue(requestedUrl.contains("latitude=47.48"))
        assertEquals(WeatherState.RAIN, weather.facts.state)
        assertEquals(LocalTime(14, 15), weather.localTime)
        assertEquals(LocalTime(7, 31), weather.sunrise)
        assertEquals(LocalTime(16, 52), weather.sunset)
        assertTrue(weather.isDay)
        assertEquals(7.9, weather.maxTemperature)
    }
}
