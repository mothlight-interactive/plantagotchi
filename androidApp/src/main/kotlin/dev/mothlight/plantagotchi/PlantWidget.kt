package dev.mothlight.plantagotchi

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.mothlight.plantagotchi.platform.WidgetBridge
import dev.mothlight.plantagotchi.platform.WidgetSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val PREFS = "plantagotchi_widget"

/** Stores the latest plant state for the widget and refreshes it. */
class AndroidWidgetBridge(private val context: Context) : WidgetBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun update(snapshot: WidgetSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("species", snapshot.species)
            .putInt("stage", snapshot.stage)
            .putBoolean("dead", snapshot.dead)
            .putInt("lux", snapshot.lux)
            .putInt("water", snapshot.water)
            .putInt("love", snapshot.love)
            .putInt("fertilizer", snapshot.fertilizer)
            .putInt("coins", snapshot.coins)
            .apply()
        scope.launch { runCatching { PlantWidget().updateAll(context) } }
    }
}

class PlantWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlantWidget()
}

class PlantWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hasData = prefs.contains("species")
        val species = prefs.getString("species", "SPROUT").orEmpty()
            .lowercase().replaceFirstChar { it.uppercase() }
        val needs = listOf(
            Triple(R.string.widget_water, prefs.getInt("water", 0), Color(0xFF5FCDE4)),
            Triple(R.string.widget_light, prefs.getInt("lux", 0), Color(0xFFFFEB57)),
            Triple(R.string.widget_love, prefs.getInt("love", 0), Color(0xFFD77BBA)),
            Triple(R.string.widget_fertilizer, prefs.getInt("fertilizer", 0), Color(0xFFEEC39A)),
        ).map { (label, value, color) -> Triple(context.getString(label), value, color) }
        val dead = prefs.getBoolean("dead", false)
        val coins = prefs.getInt("coins", 0)
        provideContent {
            WidgetContent(
                hasData = hasData,
                title = species,
                dead = dead,
                coins = coins,
                needs = needs,
                emptyText = context.getString(R.string.widget_empty),
                deadText = context.getString(R.string.widget_withered),
            )
        }
    }
}

@Composable
private fun WidgetContent(
    hasData: Boolean,
    title: String,
    dead: Boolean,
    coins: Int,
    needs: List<Triple<String, Int, Color>>,
    emptyText: String,
    deadText: String,
) {
    val ink = ColorProvider(Color(0xFFEDEDED))
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF2B1D24))
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = TextStyle(color = ColorProvider(Color(0xFFFFEB57)), fontSize = 16.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
            Text("● $coins", style = TextStyle(color = ColorProvider(Color(0xFFF5BD1C)), fontSize = 14.sp))
        }
        Spacer(GlanceModifier.height(6.dp))
        when {
            !hasData -> Text(emptyText, style = TextStyle(color = ink, fontSize = 13.sp))
            dead -> Text(deadText, style = TextStyle(color = ink, fontSize = 13.sp))
            else -> needs.forEach { (label, value, color) ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = TextStyle(color = ink, fontSize = 12.sp), modifier = GlanceModifier.width(78.dp))
                    LinearProgressIndicator(
                        progress = value / 100f,
                        color = ColorProvider(color),
                        backgroundColor = ColorProvider(Color(0xFF3F282F)),
                        modifier = GlanceModifier.defaultWeight().height(8.dp),
                    )
                }
            }
        }
    }
}
