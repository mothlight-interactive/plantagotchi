package dev.mothlight.plantagotchi

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.mothlight.plantagotchi.platform.WidgetBridge
import dev.mothlight.plantagotchi.platform.WidgetPlant
import dev.mothlight.plantagotchi.platform.WidgetSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val PREFS = "plantagotchi_widget"
private const val SNAPSHOT = "snapshot"

/** Per widget: the plant it shows. Missing = follow the active plant of the app. */
internal val PlantIdKey = stringPreferencesKey("plantId")

internal fun loadSnapshot(context: Context): WidgetSnapshot? =
    WidgetSnapshot.fromJson(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SNAPSHOT, null))

/** Stores the latest state of all plants for the widgets and refreshes them. */
class AndroidWidgetBridge(private val context: Context) : WidgetBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun update(snapshot: WidgetSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(SNAPSHOT, snapshot.toJson())
            .apply()
        scope.launch {
            runCatching { PlantWidget().updateAll(context) }
            runCatching { AllPlantsWidget().updateAll(context) }
        }
    }
}

/** Sets the plant of one widget (null = follow the app) and redraws it. */
internal suspend fun setWidgetPlant(context: Context, id: GlanceId, plantId: String?) {
    updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
        prefs.toMutablePreferences().apply {
            if (plantId == null) remove(PlantIdKey) else set(PlantIdKey, plantId)
        }
    }
    PlantWidget().update(context, id)
}

internal suspend fun setWidgetPlant(context: Context, appWidgetId: Int, plantId: String?) {
    val id = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    setWidgetPlant(context, id, plantId)
}

class PlantWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlantWidget()
}

/** One plant with its needs. Arrows browse the plants; the plant can be fixed in the widget settings. */
class PlantWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        val labels = NeedLabels(context)
        val emptyText = context.getString(R.string.widget_empty)
        val deadText = context.getString(R.string.widget_withered)
        provideContent {
            val chosen = currentState<Preferences>()[PlantIdKey]
            val plant = snapshot?.plant(chosen) ?: snapshot?.active
            WidgetFrame {
                if (snapshot == null || plant == null) {
                    Header(title = context.getString(R.string.app_name), coins = null, browse = false)
                    Text(emptyText, style = TextStyle(color = Ink, fontSize = 13.sp))
                } else {
                    Header(title = plant.name, coins = snapshot.coins, browse = snapshot.plants.size > 1)
                    if (plant.dead) {
                        Text(deadText, style = TextStyle(color = Ink, fontSize = 13.sp))
                    } else {
                        Needs(plant, labels)
                    }
                }
            }
        }
    }
}

class StepPlantAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val snapshot = loadSnapshot(context) ?: return
        val current = androidx.glance.appwidget.state.getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[PlantIdKey]
        val next = snapshot.step(current, parameters[StepKey] ?: 1) ?: return
        setWidgetPlant(context, glanceId, next.id)
    }

    companion object {
        val StepKey = ActionParameters.Key<Int>("step")
    }
}

class AllPlantsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AllPlantsWidget()
}

/** All plants as compact rows; shows as many as fit (3 medium, 5 large). */
class AllPlantsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        val emptyText = context.getString(R.string.widget_empty)
        val deadText = context.getString(R.string.widget_withered_short)
        val title = context.getString(R.string.widget_all_title)
        provideContent {
            val size: DpSize = LocalSize.current
            val rows = ((size.height.value - 44f) / 30f).toInt().coerceIn(1, 5)
            WidgetFrame {
                Header(title = title, coins = snapshot?.coins, browse = false)
                if (snapshot == null || snapshot.plants.isEmpty()) {
                    Text(emptyText, style = TextStyle(color = Ink, fontSize = 13.sp))
                } else {
                    snapshot.plants.take(rows).forEach { plant ->
                        PlantRow(plant, active = plant.id == snapshot.activeId, deadText = deadText)
                    }
                    if (snapshot.plants.size > rows) {
                        Text("+${snapshot.plants.size - rows}", style = TextStyle(color = Muted, fontSize = 11.sp))
                    }
                }
            }
        }
    }
}

private val Ink = ColorProvider(Color(0xFFEDEDED))
private val Muted = ColorProvider(Color(0x99EDEDED))
private val Title = ColorProvider(Color(0xFFFFEB57))
private val Track = ColorProvider(Color(0xFF3F282F))
private val WaterColor = Color(0xFF5FCDE4)
private val LightColor = Color(0xFFFFEB57)
private val LoveColor = Color(0xFFD77BBA)
private val FertilizerColor = Color(0xFFEEC39A)

private class NeedLabels(context: Context) {
    val water = context.getString(R.string.widget_water)
    val light = context.getString(R.string.widget_light)
    val love = context.getString(R.string.widget_love)
    val fertilizer = context.getString(R.string.widget_fertilizer)
}

@Composable
private fun WidgetFrame(content: @Composable () -> Unit) {
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF2B1D24))
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) { content() }
}

@Composable
private fun Header(title: String, coins: Int?, browse: Boolean) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (browse) Arrow("‹", -1)
        Text(
            title,
            style = TextStyle(color = Title, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (browse) Arrow("›", 1)
        if (coins != null) {
            Spacer(GlanceModifier.width(6.dp))
            Text("● $coins", style = TextStyle(color = ColorProvider(Color(0xFFF5BD1C)), fontSize = 14.sp))
        }
    }
    Spacer(GlanceModifier.height(6.dp))
}

@Composable
private fun Arrow(symbol: String, step: Int) {
    Text(
        symbol,
        style = TextStyle(color = Title, fontSize = 22.sp, fontWeight = FontWeight.Bold),
        modifier = GlanceModifier
            .padding(horizontal = 8.dp)
            .clickable(actionRunCallback<StepPlantAction>(actionParametersOf(StepPlantAction.StepKey to step))),
    )
}

@Composable
private fun Needs(plant: WidgetPlant, labels: NeedLabels) {
    listOf(
        Triple(labels.water, plant.water, WaterColor),
        Triple(labels.light, plant.lux, LightColor),
        Triple(labels.love, plant.love, LoveColor),
        Triple(labels.fertilizer, plant.fertilizer, FertilizerColor),
    ).forEach { (label, value, color) ->
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TextStyle(color = Ink, fontSize = 12.sp), modifier = GlanceModifier.width(78.dp))
            Bar(value, color, GlanceModifier.defaultWeight().height(8.dp))
        }
    }
}

@Composable
private fun PlantRow(plant: WidgetPlant, active: Boolean, deadText: String) {
    Row(GlanceModifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            (if (active) "• " else "") + plant.name,
            style = TextStyle(color = if (active) Title else Ink, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal),
            maxLines = 1,
            modifier = GlanceModifier.width(96.dp),
        )
        if (plant.dead) {
            Text(deadText, style = TextStyle(color = Muted, fontSize = 12.sp))
        } else {
            listOf(plant.water to WaterColor, plant.lux to LightColor, plant.love to LoveColor, plant.fertilizer to FertilizerColor)
                .forEach { (value, color) ->
                    Bar(value, color, GlanceModifier.defaultWeight().height(6.dp).padding(horizontal = 2.dp))
                }
        }
    }
}

@Composable
private fun Bar(value: Int, color: Color, modifier: GlanceModifier) {
    LinearProgressIndicator(
        progress = value / 100f,
        color = ColorProvider(color),
        backgroundColor = Track,
        modifier = modifier,
    )
}
